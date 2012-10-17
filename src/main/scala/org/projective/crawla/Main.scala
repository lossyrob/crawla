package org.projective.crawla

import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import cc.spray.io.IOBridge
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.httpx.SprayJsonSupport
import cc.spray.http._
import cc.spray.json.{JsonFormat, DefaultJsonProtocol}
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
  implicit def googleApiResultFormat[T :JsonFormat] = jsonFormat2(GoogleApiResult.apply[T])
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
    name = "http-client"
  )

  implicit val timeout = Timeout(5 seconds)

  example()

  def example() {
    val fetcher = system.actorOf(Props(new Fetcher(httpClient)), name = "fetcher")
    val response = fetcher ? FetchRequest(new URL("http://climate.met.psu.edu/www_prod/ida/"))
    response onComplete {
      case Right(result) =>
	val doc = result.asInstanceOf[Document]
	val chooser = doc.getElementById("dbSelect")
        println(chooser.select("option").asScala map (e => e.attr("value")))
        system.shutdown()
      case Left(error) =>
	log.error(error, "Could not fetch")
        system.shutdown()
    }
  }

  def example1() {
    log.info("Getting http://github.com ...")
    // an HttpConduit gives us access to an HTTP server,
    // it manages a pool of connections to _one_ host/port combination
    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, "climate.met.psu.edu")),
      name = "http-conduit"
    )

    // send a simple request
    val pipeline = HttpConduit.sendReceive(conduit)
    val responseFuture = pipeline(HttpRequest(method = HttpMethods.GET, uri = "/www_prod/ida/"))
    responseFuture onComplete {
      case Right(response) =>
	val body = response.entity.asString
        val doc = Jsoup.parse(body)
        
	val chooser = doc.getElementById("dbSelect")
        println(chooser.select("option").asScala map (e => e.attr("value")))
        
	system.stop(conduit)
	system.shutdown()
      case Left(error) =>
        log.error(error, "Couldn't get http://github.com")
        system.shutdown()
    }
  }
}
