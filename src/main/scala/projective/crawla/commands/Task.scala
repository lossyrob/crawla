package projective.crawla.commands

import com.beust.jcommander._

import  scala.collection._

// ** Taken from azavea/geotrellis ** //
 
// Command line tool to run crawla tasks.

/**
 * Task for command line execution.
 *
 * Each task is defined as a JCommander 'command' (http://jcommander.org/) with annotations
 * that define what command line arguments the command takes.  The idea of a command is similar 
 * to a github command, e.g. "git add" versus "git commit" ('add' and 'commit' are commands). 
 * See defined tasks under trellis.run for examples of how to define a task.  A basic example:
 *
 *  @Parameters(commandNames = Array("demo_command"), commandDescription="peachy keen command description")
 *  class DemoTask extends Task {
 *    @Parameter(
 *     names = Array("--file", "-f"),
 *     description("File for transmogrification"),
 *     required = true)
 *     var inPath:String = _
 *
 *     val taskName = "demo_command"
 *
 *     def execute = { DemoTask.transmogrify(this.inPath) }
 * }
 */
abstract class Task {
  def taskName:String
  def execute(): Unit
}


object Tasks {
  class BaseArgs {
    @Parameter(names = Array("-h", "--help"), description = "Show this help screen")
    var help: Boolean = _
    @Parameter(names = Array("--version", "-version"), description = "Show the program version")
    var version: Boolean = _
  }

  var tasks:mutable.ListBuffer[Task] = mutable.ListBuffer[Task]()

  def addTask(t:Task) = {
    tasks.append(t)
  }

  def usage(jcOpt:Option[JCommander]) = { 
     jcOpt.foreach(_.usage)
     println("----------------------------------------------------")
     println("\n * before an option indicates that it is required.")
     println("\n\nExample:")
     println("./crawla pull-files -f zip -d /home/user/zips -s http://somesite.com/sitewithlistofziplinks.html\n")

     System.exit(0)
  }

  def run(args:Array[String]):Int = {
    val baseArgs = new BaseArgs()

    val jc = new JCommander()
    jc.setProgramName("crawla")
    jc.addObject(baseArgs)
    var taskMap = mutable.Map[String,Task]()

    // We use reflection to load all available defined tasks (which extend Task) under
    // the projective.crawla.run package.
    //TODO: command line argument to specify package to search for tasks 
    //REVIEW: This reflection takes ~250ms.  Should we just define the tasks, or use configuration?
    val r = new org.reflections.Reflections("projective.crawla.commands")
    val s = r.getSubTypesOf(classOf[Task]).iterator
    while (s.hasNext) {
      val t = s.next
      val task = (t.newInstance).asInstanceOf[Task]
      taskMap(task.taskName) = task
      jc.addCommand(task)
    } 
      
    try {
      jc.parse(args: _*)
    } catch {
      case _ => { usage(Option(jc)) } 
    }
    
    if (baseArgs.help) {
      usage(Option(jc))
    }
  
    val c = jc.getParsedCommand()

    if (c == null) {
      usage(Option(jc))
    }

    c match {
      case taskName:String => { taskMap(taskName).execute } 
      case _ => throw new Exception("getParsedCommand didn't return a string")
    }

    0
  }
}
