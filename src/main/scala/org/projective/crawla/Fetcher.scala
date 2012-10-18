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
    case FetchRequest(url) => Fetch(url, sender)
  }

  def Fetch(url: URL, replyTo: ActorRef) {
    val host = url.getHost
    val path = url.getPath

    context.system.log.info("Hit here 1")

    val conduit = context.actorOf(
      props = Props(new HttpConduit(httpClient, host)),
      name = "http-conduit")

    val responseFuture = HttpConduit
		    .sendReceive(conduit)(
		       HttpRequest(method = HttpMethods.GET, uri = path)
		    )

    context.system.log.info("Response future created")
    
    import akka.pattern.pipe

    (responseFuture map {
	response  => 
	  context.system.log.info("Mapping response")
	  context.stop(conduit)
	  val doc = Jsoup.parse(response.entity.asString)
	  context.system.log.info("Got the doc, returning")
          doc
      }) pipeTo replyTo
  }
}
