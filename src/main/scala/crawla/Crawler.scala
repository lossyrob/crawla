// package crawla

// import akka.actor.{ActorRef, ActorSystem, Props}
// import akka.routing.SmallestMailboxRouter
// import akka.dispatch.Future
// import akka.pattern.ask
// import akka.util.Timeout
// import akka.util.duration._
// import java.net.URL
// import org.jsoup.nodes.Document

// class Crawler {
//   implicit val system = ActorSystem("crawla")

//   val fetcher = system.actorOf(Props[Fetcher].withRouter(SmallestMailboxRouter(10)), "routed-fetcher")
//   implicit val timeout = new Timeout(300 seconds)

//   def fetch(url: URL): Future[Document] = {
//     (fetcher ? url).mapTo[Document]
//   }

//   def fetch(url: URL, data: String) = {
//     (fetcher ? FetchRequest(url, data)).mapTo[Document]
//   }

//   def crawl[T](url: URL)(parseFunc: Document => T): Future[T] = {
//     val future = fetch(url)
//     future onFailure { case e => println("CRAWL ERROR FOR " + url.toString + " - " + e.toString) }
//     future map parseFunc
//   }

//   def crawl[T](url: URL, data: String)(parseFunc: Document => T): Future[T] = {
//     val future = fetch(url, data)
//     future onFailure { case e => println("CRAWL ERROR FOR " + url.toString + " - " + e.toString) }
//     future map parseFunc
//   }

//   def shutdown() = {
//     system.shutdown()
//   }
// }

