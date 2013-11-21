package crawla

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import spray.http._
import akka.routing.SmallestMailboxRouter
import scala.concurrent.Await
import scala.concurrent.duration._
import org.jsoup.nodes.{ Document, Element }
import scala.collection.JavaConverters._
import scala.collection.mutable

import rx.lang.scala._
import rx.subjects._


object Main  {
  def main(args:Array[String]):Unit = {
    implicit val timeout = new Timeout(3 minutes)
    val base = "http://philadelphia.pa.networkofcare.org"
    val urls = List(
      "/ps/services/category.aspx?cid=38542&targetgrp=",
      "/ps/services/category.aspx?cid=38543&targetgrp=",
      "/ps/services/category.aspx?cid=38537&targetgrp=",
      "/ps/services/category.aspx?cid=38538&targetgrp=",
      "/ps/services/category.aspx?cid=38539&targetgrp=",
      "/ps/services/category.aspx?cid=38541&targetgrp=",
      "/ps/services/category.aspx?cid=38536&targetgrp=",
      "/ps/services/category.aspx?cid=38540&targetgrp=",
      "/ps/services/category.aspx?cid=38544&targetgrp=",
      "/ps/services/category.aspx?cid=38535&targetgrp="
    ).map(base + _)
     .map(Uri(_))

    // val crawler = 
    //   Crawla.create {
    //     _ >>
    //     new CategoryCrawler >>
    //     new SubcategoryCrawler >>
    //     new ResourceCrawler
    //   }

    implicit val system = ActorSystem("crawla")
    val a = system.actorOf(Props[HardCodeActor],"Asdf")
    Await.result((a ? CrawlRequest(urls)), 60 seconds)


    // Await.result((crawler ? CrawlRequest(urls))
    //              .mapTo[Observable[Resource]], 10 seconds)
    //      .subscribe({ resource => println(s"$resource") })



    // implicit val system = ActorSystem("crawla")
    // try {
    //   val fetcher = system.actorOf(Props[Fetcher].withRouter(SmallestMailboxRouter(10)), "routed-fetcher")
    //   implicit val timeout = new Timeout(300 seconds)
    //   val doc = Await.result(fetcher ? Uri("http://www.reddit.com"), 5 seconds)
    //   println(doc)
    // } finally {
    //   system.shutdown
    // }
  }
}

class HardCodeActor extends Actor {
  implicit val system = context.system
  val fetcher = system.actorOf(Props[Fetcher].withRouter(SmallestMailboxRouter(40)), "routed-fetcher")
  implicit val timeout = new Timeout(3 minutes)
import context.dispatcher
  
  val categoryWorker = system.actorOf(Props(classOf[Worker[Category]],self,() => new CategoryCrawler)
                             .withRouter(SmallestMailboxRouter(10)), "routed-fetcher324")
  val subcategoryWorker = system.actorOf(Props(classOf[Worker1[Category,Crawler[Category,ResourceHeader]]],self,() => new SubcategoryCrawler)
                            .withRouter(SmallestMailboxRouter(20)), "routed-fetcher2")
  val resourceWorker = system.actorOf(Props(classOf[Worker1[ResourceHeader,Resource]],self, () => new ResourceCrawler)
                            .withRouter(SmallestMailboxRouter(30)), "routed-fetcher3")

  val categories = mutable.Set[Category]()
  val cSkipped = mutable.Set[Category]()

  def receive = {
    case CrawlRequest(urls) =>
      for(url <- urls) {
        println(s"$url")
        (fetcher ? url).mapTo[Document].map { doc => categoryWorker ! ParsedDoc(doc) }
      }
//      sender ! observable
    case c:Category =>
//       if(!categories.contains(c)) {
//         categories += c
// //        println(s"CATEGORY ${categories.size}   $c")
//       } else { cSkipped += c }//; println(s"SKIPPED ${cSkipped.size}") }
      (fetcher ? c.url).mapTo[Document].map { doc => subcategoryWorker ! ParsedDoc1(c,doc) }
    case rh:ResourceHeader =>
      (fetcher ? rh.url).mapTo[Document].map { doc => resourceWorker ! ParsedDoc1(rh,doc) }
    case r:Resource =>
      println(s"$r")
  }
}

object Crawla {
  implicit val system = ActorSystem("crawla")

  def create[T](pipeline:RootPipelineBuilder=>PipelineBuilder[T]) = {
    val forwardActor = system.actorOf(Props[Actor], "somename")
    val pipelineSteps = pipeline(PipelineBuilder(forwardActor,system)).pipelineSteps
    system.actorOf(Props(classOf[CrawlerActor[T]],pipelineSteps,forwardActor))
  }
}

case class CrawlRequest(urls:List[Uri])

class CrawlerActor[T](pipelineSteps:List[ActorRef],fetcher:ActorRef,f:ActorRef) extends Actor {
  implicit val timeout = new Timeout(300 seconds)
  import context.dispatcher

  f.forward(this)

  private val publishSubject:PublishSubject[T] = PublishSubject.create[T]()
  private val observable:Observable[T] = Observable(publishSubject)

  // def crawl(urls:List[Uri]):Observable[T] = {
  //   for(url <- urls) {
  //     (fetcher ? url).mapTo[Document].map { doc => pipelineSteps(0) ! doc }
  //   }
  //   Observable(publishSubject)
  // }

  def receive = {
    case CrawlRequest(urls) =>
      for(url <- urls) {
        (fetcher ? url).mapTo[Document].map { doc => pipelineSteps(0) ! doc }
      }
      sender ! observable

    case CrawlResult(t,step) =>
      if(step == pipelineSteps.length-1) {
        t match {
          case result:T =>
            publishSubject.onNext(result)
          case _ =>
            sys.error("Last line in pipe is not of type T")
        }
      }else {
        t match {
          case c:Crawlable =>
            (fetcher ? c.url).mapTo[Document].map { doc => 
              pipelineSteps(step+1) ! ParsedDoc1(t,doc) 
            }
          case _ =>
            sys.error("Not a crawlabe step")
        }
      }

    case _ =>
//      ps.onError
      ???
  }
}

trait Emitter[T] {
  private val publishSubject = PublishSubject.create[T]()
  val emissions = Observable(publishSubject)

  protected def emit(v:T):Unit = publishSubject.onNext(v)
}

trait RootCrawler[T] extends Emitter[T] {
  def recieve(doc:Document):Unit
}

trait Crawlable { def url:Uri }

trait Crawler[V,T] extends Emitter[T] {
  def recieve(v:V,doc:Document):Unit
}


case class ParsedDoc(doc:Document)
case class ParsedDoc1[V](v:V,doc:Document)
case class CrawlResult[T](v:T,step:Int)
class Worker[T](root:ActorRef,buildCrawler:() => RootCrawler[T]) extends Actor {
  val crawler = buildCrawler()
//  crawler.emissions.subscribe(root ! CrawlResult(_,0))
  crawler.emissions.subscribe { x =>
//    println(s"EMITT $x")
    root ! x 
  }

  def receive = {
    case ParsedDoc(doc) =>
      crawler.recieve(doc)
    case x =>
      sys.error(s"Invalid message: $x")
  }
}

class Worker1[V,T](root:ActorRef,buildCrawler:() => Crawler[V,T]) extends Actor {
  val crawler = buildCrawler()
//  crawler.emissions.subscribe(root ! CrawlResult(_,step))
  crawler.emissions.subscribe(root ! _)

  def receive = {
    case ParsedDoc1(v:V,doc) =>
      crawler.recieve(v,doc)
  }
}

object PipelineBuilder {
  def apply(root:ActorRef,system:ActorSystem) = new RootPipelineBuilder(root,system)
  def apply[T](root:ActorRef,system:ActorSystem,pipelineSteps:List[ActorRef]) =
    new PipelineBuilder[T](root,system,pipelineSteps)
}

class RootPipelineBuilder(root:ActorRef,system:ActorSystem) {
  def >>[T](c:RootCrawler[T]):PipelineBuilder[T] = {
    val workerRouter = system.actorOf(Props(classOf[Worker[T]],root,c).withRouter(SmallestMailboxRouter(10)), "routed-fetcher")
    PipelineBuilder[T](root,system,List(workerRouter))
  }
}

class PipelineBuilder[T](root:ActorRef,system:ActorSystem,val pipelineSteps:List[ActorRef]) {
  def >>[T,U](c:Crawler[T,U]):PipelineBuilder[U] = {
    val workerRouter = system.actorOf(Props(classOf[Worker1[T,U]],root,c).withRouter(SmallestMailboxRouter(10)), "routed-fetcher")
    PipelineBuilder[U](root,system,pipelineSteps ++ List(workerRouter))
  }
}


///////////////////////

case class Category(root:String,name:String,url:Uri) extends Crawlable
class CategoryCrawler extends RootCrawler[Category] {
  val base = "http://philadelphia.pa.networkofcare.org"
  def recieve(doc:Document) = {
    val title =
      doc.getElementsByTag("h1")
        .asScala
        .map( _.text).toSeq(0)

    println("Category: " + title)

    doc.getElementsByTag("ul")
      .asScala
      .filter( _.id == "subCategories")
      .map(_.getAllElements.asScala)
      .flatten
      .map(_.getElementsByTag("a").asScala)
      .flatten
      .map { a => 
        val name = a.text
        Category(title,name,Uri(base + a.attr("href")))  
       }
      .distinct
      .map { c =>
        println(s"EMMITTTING ONE ${c.name}")
        emit(c)
       }
  }
}

case class ResourceHeader(category:Category,url:Uri) extends Crawlable

class SubcategoryCrawler extends Crawler[Category,ResourceHeader] {
  val base = "http://philadelphia.pa.networkofcare.org"

  def recieve(category:Category,doc:Document) = {
    println(s"RECEIVED $category")
    doc.getElementById("listAgencies")
      .getElementsByTag("li")
      .asScala
      .map { e =>
        val url =
          e.getElementsByTag("a")
           .asScala
           .filter(_.attr("href") != "#")
           .map(a => Uri(base + "/ps/services/" + a.attr("href")))
           .apply(0)
        println(s"EMITTING $url")
        emit(ResourceHeader(category,url))
      }
  }
}

case class Resource(category:Category,
                    name:String,
                    websites:List[String],
                    phones:List[String],
                    addresses:List[String],
                    zipCodes:List[String],
                    emails:List[String],
                    description:String)

class ResourceCrawler() extends Crawler[ResourceHeader,Resource] {
  def recieve(header:ResourceHeader,doc:Document) = {
    println(s"RESOURCE RECEIVING  $header")
    val div =
      doc.getElementsByTag("div")
        .asScala
        .filter(_.attr("style") == "margin-bottom: 20px;")
        .apply(0)

    val name =
      div.getElementsByTag("h1").asScala.apply(0).text

    val description =
      div.text

    val div2 =
      doc.getElementsByClass("right")
        .asScala
        .apply(0)

    val addresses = {
      val d =
        div2.getElementsByClass("expandCollapse")
          .asScala
          .filter( _.getElementsByTag("span").asScala.apply(0).text == "Addresses")
      
      if(d.length > 0) {
        d.apply(0)
          .getElementsByTag("li")
          .asScala
          .map { li =>
          li.getElementsByTag("a")
            .asScala
            .map { a =>
            val title = a.attr("title")
            title.substring(0,title.indexOf("|")).trim
          }
            .toList
        }.flatten.toList
      } else { List[String]() }
    }

    val zipCodes =
      addresses.map(new scala.util.matching.Regex("""\d\d\d\d\d""").findFirstIn(_) match {
        case Some(s) => s
        case None => ""
      }).toList

    val phoneNumbers = {
      val d =
        div2.getElementsByClass("expandCollapse")
          .asScala
          .filter( _.getElementsByTag("span").asScala.apply(0).text == "Phone Numbers")
      
      if(d.length > 0) {
        d.apply(0)
          .getElementsByTag("li")
          .asScala
          .map( _.text.trim)
          .toList
      } else { List[String]() }
    }

    val websites = {
      val d =
        div2.getElementsByClass("expandCollapse")
          .asScala
          .filter( _.getElementsByTag("span").asScala.apply(0).text == "Web Site")
      
      if(d.length > 0) {
        d.apply(0)
          .getElementsByTag("a")
          .asScala
          .map( _.attr("href").replace("",""))
          .toList
      } else { List[String]() }
    }

    val emails = {
      val d =
        div2.getElementsByClass("expandCollapse")
          .asScala
          .filter( _.getElementsByTag("span").asScala.apply(0).text == "Email")
      
      if(d.length > 0) {
        d.apply(0)
          .getElementsByTag("li")
          .asScala
          .map( _.text.trim)
          .toList
      } else { List[String]() }
    }

    Resource(header.category,
             name,
             header.url.toString :: websites,
             phoneNumbers,
             addresses,
             zipCodes,
             emails,
             description)
  }
}
