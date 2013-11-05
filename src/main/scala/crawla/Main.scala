package projective.crawla

import projective.crawla.commands._

object Main extends App {
  try {
    Tasks.run(args)
  } catch {
    case e =>
      println("ERROR: " + e.toString())
  }
}
