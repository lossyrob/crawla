package org.projective.crawla

import akka.dispatch.{Promise, Future}
import akka.actor.{Actor, ActorRef, Props}
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.http._
import java.net.URL
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

case class FetchRequest(url: URL)

class Fetcher(httpClient: ActorRef) extends Actor {
  def receive = {
    case FetchRequest(url) => Fetch(url)
  }

  def Fetch(url: URL) {
    val host = url.getHost
    val path = url.getPath

    val conduit = context.actorOf(
      props = Props(new HttpConduit(httpClient, host)),
      name = "http-conduit")

    implicit val dispatcher = context.system.dispatcher

    val promise = Promise[Document]

    val responseFuture = HttpConduit
		    .sendReceive(conduit)(HttpRequest(method = HttpMethods.GET, uri = path))

      responseFuture onComplete {
	case Right(response) => 
	  context.stop(conduit)
	  val doc = Jsoup.parse(response.entity.asString)
	  promise.success(doc)
	case Left(error) =>
	  promise.failure(error)
      }

    promise
  }
}
