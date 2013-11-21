// package crawla.commands

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

// import crawla._

// // @Parameters(commandNames = Array("pull-files"), commandDescription = "Downloads all links to a specific file type do a specified directory from a given page.")
// class PullFilesTask {//extends Task {
//   // @Parameter(
//   //   names = Array("--filetype", "-f"),
//   //   description = "The extension of the file type that you want to download",
//   //   required = true)
//   var fileType: String = _

//   // @Parameter(
//   //   names = Array("--dir", "-d"),
//   //   description = "Path of directory to place downloaded files",
//   //   required = true)
//   var outDir: String = _

//   // @Parameter(
//   //   names = Array("--site", "-s"),
//   //   description = "URL of the page you want to crawl.",
//   //   required = true)
//   var site: String = _

//   val taskName = "pull-files"

//   def execute = {
//     val crawler = new Crawler()

//     try {
//       run(crawler)
//       print("Finished!")
//       scala.Console.in.read.toChar      
//     } catch {
//       case e =>
//         print("ERROR: " + e.toString())
//     }

//     crawler.shutdown()
//   }

//   def print(s: String) = println("[CRAWLA] " + s)

//   def run(crawler: Crawler) = {
//     print(site)
//     val url = new URL(site)
//     print("Crawling site %s, path %s".format(url.getHost, url.getPath))
//     val zips = Await.result(crawler.crawl(url)(parseFiles), Duration(5, TimeUnit.SECONDS))
//     zips map {
//       zip => downloadZip(zip)
//     }
//   }

//   def parseFiles(doc: Document): Seq[String] = {
//     val url = new URL(site)
//     print("Parsing...")
//     doc.getElementsByTag("a")
//       .asScala
//       .filter { node => node.hasAttr("href") }
//       .map { node => node.attr("href") }
//       .filter { link => link.endsWith(fileType) }
//       .map { relativeAddr => new URL(url.getProtocol, url.getHost, relativeAddr).toString }
//   }

//   def downloadZip(zip: String) = {
//     val fileName = new File(new URL(zip).getFile).getName
//     Seq("wget", "-O", outDir + fileName, zip) !
//   }
// }
