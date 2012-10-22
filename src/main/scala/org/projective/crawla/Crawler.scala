package org.projective.crawla

import akka.actor.ActorRef
import akka.dispatch.Future
import akka.pattern.ask
import akka.util.Timeout
import java.net.URL
import org.jsoup.nodes.Document

class Crawler(fetcher: ActorRef)(implicit timeout: Timeout)  {
  def fetch(url: URL): Future[Document] = {
    (fetcher ? url).mapTo[Document]
  }

  def fetch(url: URL, data: String) = {
    (fetcher ? FetchRequest(url,data)).mapTo[Document]    
  }

  def crawl[T](url: URL)(parseFunc: Document => T): Future[T] = {
    val future = fetch(url)
//    future onFailure { case e => println(e.toString) }
    future map parseFunc
  }

  def crawl[T](url: URL, data: String)(parseFunc: Document => T): Future[T] = {
    val future = fetch(url, data) 
//    future onFailure { case e => println(e.toString) }
    future map parseFunc
  }
}
