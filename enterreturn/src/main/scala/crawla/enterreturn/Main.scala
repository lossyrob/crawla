package crawla.enterreturn

import org.jsoup.nodes.Document

import rx.lang.scala._

object Main {
  val base = "http://philadelphia.pa.networkofcare.org"

  def main(args:Array[String]):Unit = {
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
     .map(new URL(_))

    val crawla = 
      Crawla.create {
        _ >>
        new CategoryCrawler >> // Creates a router actor, worker actors to handle UrlRecieved method
        new SubcategoryCrawler >>
        new ResourceCrawler
      }

    crawler.crawl(urls) // Sends initial UrlRequest messages to Fetcher, at end of pipeline calls OnNext
           .subscribe { resource =>
      //place resource in the database
    }
  }
}

object Crawla {
  implicit val system = ActorSystem("crawla")

  def create[T](pipeline:RootPipeline=>Pipeline[T]) = {
    val rootActor = system.actorOf(Props[CrawlerActor], "somename")
    val pipelineSteps = pipeline(PipelineBuilder(rootActor)).piplineSteps
    new RootCrawler[T](piplineSteps)
  }
}

c// lass CrawlerActor extends Actor {
//   val fetcher = context.actorOf(Props[Fetcher].withRouter(SmallestMailboxRouter(10)), "routed-fetcher")

//   var _pipeline:Pipeline

//   def recieve = {
//     case Crawl(url) =>
//       fetcher ? FetchUrl(url) map (routeFetch(_,0))
//     case 
//   }
// }

class RootCrawler[T](pipelineSteps:List[ActorRef],fetcher:ActorRef) extends ActorRef = {
  private var publishSubject:PublishSubject[T]

  def crawl(urls:List[URL]):Observable[T] = {
    publishSubject = PublishSubject.create[T]()
    for(url <- urls) {
      fetcher ? FetchUrl(url) map (routeFetch(_,0))
      pipeline(0) ! Crawl(url)
    }
    Observable(publishSubject)
  }

  def routeFetch(f:FetchResult,step:Int) = {
    pipelineSteps(0) ! ParseDoc(f.doc)
  }

  def recieve = {
    case CrawlResult(t,step) =>
      if(step == pipelineSteps.length-1) 
        ps.onNext(t)
      else
        fetcher ? FetchUrl(t.url) map { case FetchResult(doc) => pipelineSteps(step+1) ! ParseDoc1(t,doc,step+1) }
    case _ =>
//      ps.onError
      ???
  }
}

case class ParsedDoc(doc:Document) { step:Int = 0 }
case class ParseDoc1[V](v:V,doc:Document,step:Int)
case class CrawlResult[T](v:T,step:Int)

class Worker(root:ActorRef,crawler:Crawler[T]) extends Actor {
  crawler.emissions.subscribe(root ! CrawlResult(_))

  def recieve = {
    case ParsedDoc(doc) =>
      crawler.recieve(doc)
  }
}

class Worker1(root:ActorRef,crawler:Crawler[V,T]) extends Actor {
  crawler.emissions.subscribe(root ! CrawlResult(_))

  def recieve = {
    case ParsedDoc1(v,doc) =>
      crawler.recieve(v,doc)
  }
}

class RootPipelineBuilder(system:ActorSystem) {
  def >>[T](c:RootCrawler[T]):PipelineBuilder[T] = {
    val workerRouter = system.actorOf(Props(classOf[Worker],c).withRouter(SmallestMailboxRouter(10)), "routed-fetcher")
    PipelineBuilder[T](system,List(workerRouter))
  }
}

object PipelineBuilder {
  def apply(system:ActorSystem) = RootPipelineBuilder(system)
  def apply[T](system:ActorSystem,pipelineSteps:List[ActorRef]) =
    PipelineBuilder[T](system,pipelineSteps)
}

class PipelineBuilder[T](system:ActorSystem,pipelineSteps:List[ActorRef]) {
  def >>[V,T](c:Crawler[T,U]):Pipeline[U] = {
    val workerRouter = system.actorOf(Props(classOf[Worker],c).withRouter(SmallestMailboxRouter(10)), "routed-fetcher")
    Pipeline[U](system,pipelineSteps ++ List(workerRouter))
  }
}

trait Pipeline[T] = {
  def >>[T,V](c:Crawler[T,V]):Pipeline[V]
}



trait Crawlable { def url:URL }

case class Category(root:String,name:String,url:URL) extends Crawlable

trait Emitter[T] {
  private val publishSubject = PublishSubject.create[T]()
  val emissions = Observable(publishSubject)

  protected def emit(v:T):Unit = observable.onNext(v)
}

trait Crawler[T] extends Emitter[T] {
  def recieve(doc:Document):Unit
}

trait DownstreamCrawler[V <: Crawlable,T] extends Emitter[T] {
  def recieve(v:V,doc:Document):Unit
}


class CategoryCrawler extends RootCrawler[Category] {
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
        println("  " + name)
        emit(Category(title,name,new URL(base + a.attr("href"))))
       }
  }
}

case class ResourceHeader(category:Category,url:Url) extends Crawlable

class SubcategoryCrawler() extends Crawler[Category,ResourceHeader] {
  def recieve(category:Cateogry,doc:Document) = {
    doc.getElementById("listAgencies")
      .getElementsByTag("li")
      .asScala
      .map { e =>
        val url =
          e.getElementsByTag("a")
           .asScala
           .filter(_.attr("href") != "#")
           .map(a => new URL(base + "/ps/services/" + a.attr("href")))
           .apply(0)
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

    Resource(category,
             name,
             header.url.toString :: websites,
             phoneNumbers,
             addresses,
             zipCodes,
             emails,
             description)
  }
}
