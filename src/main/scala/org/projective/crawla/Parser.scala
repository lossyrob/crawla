package org.projective.crawla

import akka.actor.{Actor, ActorRef}
import akka.dispatch.Future
import akka.pattern.pipe
import org.jsoup.nodes.Document
import scala.collection.JavaConverters._

class Parser extends Actor {
  def receive = {
    case doc: Document => Parse(doc, sender)
  }

  import context.dispatcher

  def Parse(doc: Document, replyTo: ActorRef) = {
    val chooser = doc.getElementById("dbSelect")
    Future { 
      chooser.select("option")
    		        .asScala
    		        .drop(1) // First option is default, 0
    			.map { e => e.attr("value") }
	  } pipeTo replyTo
  }
}
