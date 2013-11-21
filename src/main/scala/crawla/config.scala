package crawla

import akka.util.Timeout
import scala.concurrent.duration._

object CrawlaConfig {
  val timeout = new Timeout(300 seconds)
}
