package projective.crawla.commands

import akka.actor._
import akka.dispatch.Await
import akka.pattern.ask
import akka.util.Duration
import java.net.URL
import akka.util.Timeout
import akka.util.duration._
import java.util.concurrent.TimeUnit
import org.jsoup.nodes.{ Document, Element }
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import com.beust.jcommander._

import scala.sys.process._
import java.net.URL
import java.io.File

import scala.collection.mutable

import projective.crawla._

case class Category(root:String,name:String,url:URL)

case class Resource(category:Category,
                    name:String,
                    websites:List[String],
                    phones:List[String],
                    addresses:List[String],
                    zipCodes:List[String],
                    emails:List[String],
                    description:String)

case class ResourceUrl(category:Category,url:URL,crawler:Crawler,collector:ActorRef) {
  def fetch(collector:ActorRef) = {
    crawler.crawl(url)({ doc =>
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
           .map( _.text.replace("View Map","").trim)
           .toList
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
           .map( _.attr("href"))
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

      Resource(category,name,url.toString :: websites,phoneNumbers,addresses,zipCodes,emails,description)
    }).map(collector ! AddElement(_))
  }
}

/*
 * Scrape websites for the transit hackathon September 2013.
 */

@Parameters(commandNames = Array("transithack"), commandDescription = "Scrape prison resources.")
class TransitHackTask extends Task {
  implicit val timeout = new Timeout(300 seconds)

  val site = """http://philadelphia.pa.networkofcare.org/ps/services/index.aspx"""
  val base = "http://philadelphia.pa.networkofcare.org"


  val taskName = "transithack"

  def execute = {
    val crawler = new Crawler()

    try {
      run(crawler)
      print("Finished!")
    } catch {
      case e =>
        print("ERROR: " + e.toString())
    }

    crawler.shutdown()
  }

  def print(s: String) = println("[CRAWLA] " + s)

  def run(crawler: Crawler) = {
    val collector =  crawler.system.actorOf(Props(classOf[Collector[Resource]]))

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

    val categories = 
      (for(url <- urls) yield {
        Await.result(crawler.crawl(url)({ doc =>
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
              Category(title,name,new URL(base + a.attr("href")))
             }
            .toList
        }), Duration(5, TimeUnit.SECONDS))
      }).flatten

    val resourceUrls =
      (for(category <- categories) yield {
        Await.result(crawler.crawl(category.url)({ doc =>
          doc.getElementById("listAgencies")
            .getElementsByTag("li")
            .asScala
            .map(createResourceUrl(category,_,crawler,collector))
        }), Duration(5, TimeUnit.SECONDS))
      }).flatten

    (for(r <- resourceUrls) yield {
      r.fetch(collector)
    }).toList.map(Await.result(_,Duration(10, TimeUnit.SECONDS)))

    val GetCollectionResponse(result:List[Resource]) = 
      Await.result(collector ? GetCollectionRequest(),Duration(1,TimeUnit.SECONDS))

    val pw = new java.io.PrintWriter("/home/rob/proj/transithack/enter-return/data/networkofcare.csv")
    pw.println("category,subcategory,subcategory_url,name,websites,phones,address,zipcode,emails,description")
    for(resource <- result) {
      val len = resource.addresses.length
      for(i <- 0 until len) {
        pw.println(List(
          resource.category.root,
          resource.category.name,
          resource.category.url.toString,
          resource.name,
          resource.websites.mkString(","),
          resource.phones.mkString(","),
          resource.addresses(i),
          resource.zipCodes(i),
          resource.emails.mkString(","),
          resource.description
        ).map(""""""" + _ + """"""").mkString(","))
      }
    }
    pw.println("")
    pw.close

    val pw2 = new java.io.PrintWriter("/home/rob/proj/transithack/enter-return/data/networkofcare_categories.csv")
    pw2.println("category,subcategory,subcategory_url")
    val set = mutable.Set[Category]()
    for(resource <- result) {
      set += resource.category
    }
    for(c <- set) { 
      pw2.println(List(c.root,c.name,c.url.toString).map(""""""" + _ + """"""").mkString(","))
    }
    pw2.println("")
    pw2.close
  }

  def createResourceUrl(cat:Category,e:Element,crawler:Crawler,collector:ActorRef):ResourceUrl = {
    val url =
      e.getElementsByTag("a")
        .asScala
        .filter(_.attr("href") != "#")
        .map(a => new URL(base + "/ps/services/" + a.attr("href")))
        .apply(0)
    ResourceUrl(cat,url,crawler,collector)
  }
}
