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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.JavaConverters._

case class Elevation(location: Location, elevation: Double)
case class Location(lat: Double, lng: Double)
case class GoogleApiResult[T](status: String, results: List[T])

object ElevationJsonProtocol extends DefaultJsonProtocol {
  implicit val locationFormat = jsonFormat2(Location)
  implicit val elevationFormat = jsonFormat2(Elevation)
  implicit def googleApiResultFormat[T: JsonFormat] = jsonFormat2(GoogleApiResult.apply[T])
}

object Main extends App {
  // we need an ActorSystem to host our application in
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

  try {
    run()
  } 
  catch { 
    case e => 
      println("SHIT BOMBED!" + e.toString())
      system.shutdown() 
  }

  def run() {
    val fetcher = system.actorOf(Props(new Fetcher(httpClient)), name = "fetcher")
    val parser = system.actorOf(Props[Parser], name = "parser")
    (fetcher ? new URL("http://climate.met.psu.edu/www_prod/ida/")).mapTo[Document]
      .flatMap { doc => (parser ? doc).mapTo[Seq[String]] }
      .onSuccess { case result => 
	      log.info(result.reduceLeft { (a,b) => a + ", " + b} )
	      system.shutdown()
	 }

    // response onComplete {
    //   case Right(doc) =>
    //     val chooser = doc.getElementById("dbSelect")
    //     log.info(chooser.select("option")
    // 		        .asScala
    // 		        .drop(1) // First option is default, 0
    // 			.map { e => e.attr("value") }
    // 			.reduceLeft { (a,b) => a + ", " + b} )
    //     system.shutdown()
    //   case Left(error) =>
    //     log.error(error, "Could not fetch")
    //     system.shutdown()
    // }
  }
}
