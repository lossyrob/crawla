package org.projective.crawla

import akka.dispatch.{ Promise, Future }
import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.http._
import java.net.URL
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.collection.mutable.HashMap

import HttpConduit.sendReceive

case class FetchRequest(url: URL, data: String)

class Fetcher(httpClient: ActorRef) extends Actor {
  val conduits = new HashMap[String, ActorRef]

  def receive = {
    case url: URL => fetch(conduitFor(url), requestFor(url))
    case FetchRequest(url, data) => fetch(conduitFor(url), requestFor(url, data))
  }

  def conduitFor(url: URL) = {
    val host = url.getHost
    if (!conduits.contains(host)) {
      conduits(host) = context.actorOf(Props(new HttpConduit(httpClient, host)), name = "http-conduit" + host)
    }
    conduits(host)
  }

  def requestFor(url: URL) = {
    val path = url.getQuery match {
      case null => url.getPath
      case _ =>  url.getPath + "?" + url.getQuery
    }
    HttpRequest(method = HttpMethods.GET, uri = path)
  }

  def requestFor(url: URL, data: String) = {
    val path = url.getPath
    val entity = HttpBody(ContentType(MediaTypes.`application/x-www-form-urlencoded`), data)
    context.system.log.info("POST for " + url.toString + "  and entity " + entity.toString())
    HttpRequest(method = HttpMethods.POST, uri = path, entity = entity)
  }

  def fetch(conduit: ActorRef, request: HttpRequest) = {
    import akka.util.Timeout
    import akka.util.duration._
    implicit val timeOut = Timeout(3 seconds)
    val recipient = sender
    val responseFuture = sendReceive(conduit)(request)
    val result = (responseFuture map { response => Jsoup.parse(response.entity.asString) })
    result pipeTo recipient
  }
}
