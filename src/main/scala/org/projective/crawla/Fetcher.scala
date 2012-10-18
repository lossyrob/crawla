package org.projective.crawla

import akka.dispatch.{Promise, Future}
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.http._
import java.net.URL
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class Fetcher(httpClient: ActorRef) extends Actor {
  def receive = {
    case url: URL => Fetch(url, sender)
  }

  def Fetch(url: URL, replyTo: ActorRef) {
    val host = url.getHost
    val path = url.getPath

    val conduit = context.actorOf(
      props = Props(new HttpConduit(httpClient, host)),
      name = "http-conduit")

    val request = HttpRequest(method = HttpMethods.GET, uri = path)
    val responseFuture = HttpConduit.sendReceive(conduit)(request)

    (responseFuture 
       map { response  => 
	       context.stop(conduit)
	       Jsoup.parse(response.entity.asString)
      }) pipeTo replyTo
  }
}
