package crawla

// import akka.dispatch.Await
// import akka.pattern.ask
// import akka.util.Duration
// import java.net.URL
// import java.util.concurrent.TimeUnit
// import org.jsoup.nodes.{ Document, Element }
// import scala.collection.JavaConverters._
// import scala.collection.mutable.HashMap

// import scala.sys.process._
// import java.net.URL
// import java.io.File

// class Elevation(crawler: Crawler) {
//   val host = "http://www.pasda.psu.edu"
//   val mainUrl = new URL(host + "/data/dem24k_10m/")

//   val dlDir = "/home/rob/dem/"

//   def parseZips(doc: Document): Seq[String] = {
//     println("Parsing...")
//     doc.getElementsByTag("a")
//       .asScala
//       .filter { node => node.hasAttr("href") }
//       .map { node => node.attr("href") }
//       .filter { link => link.endsWith("zip") } 
//       .map { relativeAddr => host + relativeAddr }
//   }

//   def run() = {
//     println("Running...")
//     val zips = Await.result(crawler.crawl(mainUrl)(parseZips), Duration(5, TimeUnit.SECONDS))
//     zips map {
//       zip => downloadZip(zip)
//     }
//   }

//   def downloadZip(zip: String) = {
//     val fileName = new File(new URL(zip).getFile).getName
//     Seq("wget", "-O", dlDir + fileName, zip) !
//   }
// }
