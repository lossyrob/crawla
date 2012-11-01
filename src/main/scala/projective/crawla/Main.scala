package projective.crawla

object Main extends App {

  val crawler = new Crawler()

  try {
    new Weather(crawler).run()
  } catch {
    case e =>
      println("ERROR: " + e.toString())
  }

  scala.Console.in.read.toChar
  crawler.shutdown()
}
