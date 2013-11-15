package crawla

// import akka.dispatch.Await
// import akka.pattern.ask
// import akka.util.Duration
// import java.net.URL
// import java.util.concurrent.TimeUnit
// import org.scala_tools.time.Imports._
// import org.jsoup.nodes.{Document, Element}
// import scala.collection.JavaConverters._
// import scala.collection.mutable.HashMap


// case class Site(id: String, url: URL, name: String,
//   county: String, lat: Double, long: Double, elevation: Double, start: DateTime, end: DateTime)

// case class SiteData(site: Site, data: HashMap[String,String])


// class Weather(crawler: Crawler) {
//   val maxRequestDays = 15
//   val dbNames = List("faa_raw", "cwop_raw", "shef", "dep")

//   val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")

//   val mainUrl = new URL("http://climate.met.psu.edu/www_prod/ida/")

//   val siteListUrl = new URL(mainUrl + "siteList.php")
//   val submitUrl = new URL(mainUrl + "submit.php")

//   def getSiteListData(s: String): String = {
//     "x=" + s + "&t=1"
//   }

//   def parseSites(doc: Document) = {
//     doc.getElementsByTag("tr")
//       .asScala
//       .filter { node => node.getElementsByTag("th").isEmpty }
//       .map(createSiteFromTableRow)
//   }

//   def createSiteFromTableRow(tr: Element): Site = {
//     val id = tr.child(0).child(0).text
//     val url = new URL(mainUrl + tr.child(0).child(0).attr("href"))
//     val name = tr.child(1).text
//     val county = tr.child(2).text
//     // State is child(3)
//     val lat = tr.child(4).text.toDouble
//     val long = tr.child(5).text.toDouble
//     val elevation = tr.child(6).text.toDouble
//     val start = dateFormat.parseDateTime(tr.child(7).text)
//     val end = dateFormat.parseDateTime(tr.child(8).text)

//     Site(id, url, name, county, lat, long, elevation, start, end)
//   }

//   def run() = {
//     dbNames map { dbName =>
//       val sites = Await.result(crawler.crawl(siteListUrl, getSiteListData(dbName))(parseSites), Duration(5, TimeUnit.SECONDS))
//       sites map {
//         site => processSite(site, dbName)
//       }
//     }
//   }

//   def processSite(site: Site, dbName: String) = {
//     val choices = Await.result(crawler.crawl(site.url) { doc =>
//       doc.getElementsByTag("input")
//         .asScala
//         .filter { e => e.attr("name") == "choices[]" }
// 	.map { e => e.attr("value") }
//     }, Duration(5, TimeUnit.SECONDS))
//     processData(site, dbName, choices)
//   }

//   def processData(site: Site, dbName: String, choices: Seq[String]) = {
//     val choicesString = choices.map { choice => "choices[]=" + choice }
//         .reduceLeft { (a, b) => a + "&" + b }
//     getDates(site.start, site.end) map {
//       case (start, end) =>
//         val data = ("siteid=%s&" +
//           "db=%s&" +
//           "datastart=%s&" +
//           "dataend=%s&" +
//           "filetype=web&" + choicesString)
//           .format(site.id, dbName, start.toString(dateFormat), end.toString(dateFormat))
//         println("[" + dbName + "] [" + site.id + "] - Getting data from " + start.toString(dateFormat) + " to " + end.toString(dateFormat))
//       val dataPoints = Await.result(crawler.crawl(submitUrl, data) { doc =>
//         doc.getElementsByTag("tr")
//             .asScala
//             .filter { node => node.getElementsByTag("th").isEmpty }
//             .map { e => choices zip e.getElementsByTag("td").asScala.map { e => e.text } }

//         // TODO: Process data from here.
								    
//         }, Duration(30, TimeUnit.SECONDS))
      
//     }
//   }

//   def getDates(start: DateTime, end: DateTime): Seq[(DateTime, DateTime)] = {
//     val days = (start to end).toDuration.getStandardDays
//     if (days <= maxRequestDays) {
//       (start, end) :: Nil
//     } else {
//       var result: List[(DateTime, DateTime)] = Nil
//       var curr = start
//       //      val numOfSplits = scala.math.ceil(days / maxRequestDays).toInt
//       val numOfSplits = 2
//       for (d <- (1 to numOfSplits)) {
//         val currEnd = curr + (maxRequestDays days)
//         if (currEnd > end) {
//           result = (curr, end) :: result
//         } else {
//           result = (curr, currEnd) :: result
//           curr = currEnd
//         }
//       }
//       result
//     }
//   }
// }
