package crawla.enterreturn

import org.jsoup.nodes.Document

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

    val pipeline = 
      new CategoryCrawler >>
      new SubcategoryCrawler >>
      new ResourceCrawler

    Crawla.register(pipeline)

    for(url <- urls) { Crawla.crawl(url) }

    for(event <- observable) {
      // Place resource in databse.
    }
  }
}

trait Crawlable { def url:URL }

case class Category(root:String,name:String,url:URL) extends Crawlable

trait Crawler[T] {
  def recieve(doc:Document):Unit
  protected def emit[T](v:T):Unit
}

case class CategoryCrawler extends Crawler[Category] {
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

case class SubcategoryCrawler extends Crawler[Category,ResourceHeader] {
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

case class ResourceCrawler extends Crawler[ResourceHeader,Resource] {
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
