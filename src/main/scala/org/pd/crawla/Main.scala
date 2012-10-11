package org.pd.crawla

import akka.actor.{Props, ActorSystem}
import cc.spray.io.IOBridge
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.httpx.SprayJsonSupport
import cc.spray.http._

import cc.spray.json.{JsonFormat, DefaultJsonProtocol}


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

  startExample2()

  // finally we drop the main thread but hook the shutdown of
  // our IOBridge into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioBridge.stop()
  }

  ///////////////////////////////////////////////////

  def startExample1() {
    log.info("Getting http://github.com ...")
    // an HttpConduit gives us access to an HTTP server,
    // it manages a pool of connections to _one_ host/port combination
    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, "github.com")),
      name = "http-conduit"
    )

    // send a simple request
    val pipeline = HttpConduit.sendReceive(conduit)
    val responseFuture = pipeline(HttpRequest(method = HttpMethods.GET, uri = "/"))
    responseFuture onComplete {
      case Right(response) =>
        log.info(
          """|Response for GET request to github.com:
             |status : {}
             |headers: {}
             |body   : {}""".stripMargin,
          response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
        )
        system.stop(conduit) // the conduit can be stopped when all operations on it have been completed
        startExample2()

      case Left(error) =>
        log.error(error, "Couldn't get http://github.com")
        system.shutdown()
    }
  }

  def startExample2() {
    log.info("Requesting the elevation of Mt. Everest from Googles Elevation API...")
    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, "maps.googleapis.com")),
      name = "http-conduit"
    )

    import HttpConduit._
    import ElevationJsonProtocol._
    import SprayJsonSupport._
    val pipeline = sendReceive(conduit) ~> unmarshal[GoogleApiResult[Elevation]]

    val responseF = pipeline(Get("/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    responseF onComplete {
      case Right(response) =>
        log.info("The elevation of Mt. Everest is: {} m", response.results.head.elevation)
        system.shutdown()

      case Left(error) =>
        log.error(error, "Couldn't get elevation")
        system.shutdown()
    }
  }
}
