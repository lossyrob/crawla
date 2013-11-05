package projective.crawla

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.dispatch.Future
import akka.pattern.pipe
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.client.HttpConduit.sendReceive
import cc.spray.http._
import cc.spray.io.IOBridge
import java.net.URL
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.collection.mutable.HashMap

case class FetchRequest(url: URL, data: String)

class Fetcher extends Actor {
  val conduits = new HashMap[String, ActorRef]

  var ioBridge = new IOBridge(context.system).start()
  var httpClient = context.actorOf(Props(new HttpClient(ioBridge)), "http-client")

  override def postStop() {
    ioBridge.stop()
  }

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
      case _ => url.getPath + "?" + url.getQuery
    }
    HttpRequest(method = HttpMethods.GET, uri = path)
  }

  def requestFor(url: URL, data: String) = {
    val path = url.getPath
    val entity = HttpBody(ContentType(MediaTypes.`application/x-www-form-urlencoded`), data)
    HttpRequest(method = HttpMethods.POST, uri = path, entity = entity)
  }

  def fetch(conduit: ActorRef, request: HttpRequest) = {
    val recipient = sender
    val responseFuture = sendReceive(conduit)(request)
    val result = (responseFuture map { response => Jsoup.parse(response.entity.asString) })
    result pipeTo recipient
  }
}
