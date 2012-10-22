package org.projective.crawla

import akka.actor.{ Props, ActorSystem }
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import cc.spray.io.IOBridge
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.httpx.SprayJsonSupport
import cc.spray.http._
import cc.spray.json.{ JsonFormat, DefaultJsonProtocol }
import java.net.URL
import java.util.Date
import java.text.SimpleDateFormat

import org.jsoup.Jsoup
import org.jsoup.nodes.{ Document, Element }

import scala.collection.JavaConverters._

case class Site(id: String, url: URL, name: String,
  county: String, lat: Double, long: Double, elevation: Double, start: Date, end: Date) {
  override def toString = "[id]: %s  [url]: %s  [name]: %s  [county]: %s  [lat]: %f  [long]: %f  [elevation]: %f  [start]: %s  [end]: %s".format(id, url.toString, name, county, lat, long, elevation, start, end)
}

object Main extends App {

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

  val system = ActorSystem("crawla")
  def log = system.log

  // every spray-can HttpClient (and HttpServer) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = new IOBridge(system).start()

  // since the ioBridge is not an actor it needs to be stopped separately,
  // here we hook the shutdown of our IOBridge into the shutdown of the applications ActorSystem
  system.registerOnTermination(ioBridge.stop())

  // create and start a spray-can HttpClient
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioBridge)),
    name = "http-client")

  implicit val timeout = Timeout(5 seconds)

  val mainUrl = new URL("http://climate.met.psu.edu/www_prod/ida/")

  val siteListUrl = new URL(mainUrl + "siteList.php")
  val submitUrl = new URL(mainUrl + "submit.php")

  val fetcher = system.actorOf(Props(new Fetcher(httpClient)), name = "fetcher")
  val crawler = new Crawler(fetcher)

  try {
    run()
  } catch {
    case e =>
      println("SHIT BOMBED!" + e.toString())
      system.shutdown()
  }

  def parseFirst(doc: Document) = {
    val chooser = doc.getElementById("dbSelect")
    chooser.select("option")
      .asScala
      .drop(1) // First option is default, 0
      .map { e => e.attr("value") }
  }

  def getSiteListData(s: String): String = {
    "x=" + s + "&t=1"
  }

  def parseSecond(doc: Document) = {
    doc.getElementsByTag("tr")
      .asScala
      .filter { node => node.getElementsByTag("th").isEmpty }
      .map(createSiteFromTableRow)
  }

  def createSiteFromTableRow(tr: Element): Site = {
    val id = tr.child(0).child(0).text
    val url = new URL(mainUrl + tr.child(0).child(0).attr("href"))
    val name = tr.child(1).text
    val county = tr.child(2).text
    // State is child(3)
    val lat = tr.child(4).text.toDouble
    val long = tr.child(5).text.toDouble
    val elevation = tr.child(6).text.toDouble
    val start = dateFormat.parse(tr.child(7).text)
    val end = dateFormat.parse(tr.child(8).text)

    Site(id, url, name, county, lat, long, elevation, start, end)
  }

  def run() {
    (crawler.crawl(mainUrl)(parseFirst))
      .map { dbNames =>
        dbNames map { dbName =>
          (crawler.crawl(siteListUrl, getSiteListData(dbName))(parseSecond))
            .map { sites =>
              sites map {
                site => processSite(site, dbName)
              }
            }
        }
      }

    // TODO: Figure out a way to close out io bridge after all futures complete.
    //       If we don't then system hangs, and without time out will not ever return.
    log.info("Before await")
    system.awaitTermination(10 seconds)
    log.info("after wait")
  }

  def processSite(site: Site, dbName: String) = {
    log.info("Crawling " + site.url.toString)
    crawler.crawl(site.url) { doc =>

      doc.getElementsByTag("input")
        .asScala
        .filter { e => e.attr("name") == "choices[]" }
        .map { e => "choices[]=" + e.attr("value") }
        .reduceLeft { (a, b) => a + "&" + b }
    } map { choices =>
      log.info(choices)
      val data = ("siteId=%s&" +
        "db=%s&" +
        "datastart=%s&" +
        "dataend=%s&" +
        "filetype=web&" + choices)
        .format(site.id, dbName, dateFormat.format(site.start), dateFormat.format(site.end))
      crawler.crawl(submitUrl, data)(doc => doc.head.toString)
        .map { s => log.info(s) }
    }
  }
}
