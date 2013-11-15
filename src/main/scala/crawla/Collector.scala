package crawla.commands

import akka.actor._
//import scala.concurrent.duration._

import scala.collection.mutable

import akka.dispatch.Await
import akka.pattern.ask
import akka.util.Duration
import java.net.URL
import java.util.concurrent.TimeUnit
import org.jsoup.nodes.{ Document, Element }
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import com.beust.jcommander._

import scala.sys.process._
import java.net.URL
import java.io.File

case class AddElement[T](e:T)
case class GetCollectionRequest()
case class GetCollectionResponse[T](collection:List[T])

class Collector[T] extends Actor {
  private val collection = mutable.ListBuffer[T]()

  def receive = {
    case AddElement(v) => collection += v.asInstanceOf[T]
    case GetCollectionRequest() => sender ! GetCollectionResponse(collection.toList)
  }

}
