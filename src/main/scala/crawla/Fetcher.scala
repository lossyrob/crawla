package crawla

import akka.io.IO
import akka.actor._
import akka.pattern.{ask,pipe}

import spray.can.Http
import spray.http._
import HttpMethods._

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FetchRequest {
  def apply(url:Uri):FetchRequest = FetchRequest(url,"")
}
case class FetchRequest(url:Uri, data:String)

class Fetcher extends Actor {
  implicit val system = context.system
  implicit val timeout = CrawlaConfig.timeout
  import context.dispatcher

  def receive = {
    case uri: Uri => 
      fetch(sender,HttpRequest(GET,uri))
    case FetchRequest(uri,data) =>
      fetch(sender,HttpRequest(GET,uri,entity = HttpEntity(data)))
    case x =>
      sys.error(s"Invalid message: $x")
  }

  def fetch(s:ActorRef,req:HttpRequest) =
      (IO(Http) ? req)
        .mapTo[HttpResponse]
        .map { r => Jsoup.parse(r.entity.asString) }
        .pipeTo(s)


  // def requestFor(url: URL, data: String):HttpRequest = {
  //   val path = url.getPath
  //   val entity = HttpBody(ContentType(MediaTypes.`application/x-www-form-urlencoded`), data)
  //   HttpRequest(method = HttpMethods.POST, uri = path, entity = entity)
  // }

  // def fetch(conduit: ActorRef, request: HttpRequest) = {
  //   val recipient = sender
  //   val responseFuture = sendReceive(conduit)(request)
  //   val result = (responseFuture map { response => Jsoup.parse(response.entity.asString) })
  //   result pipeTo recipient
  // }
}
