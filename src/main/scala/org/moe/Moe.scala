package org.moe

import org.apache.commons.cli._;

import org.moe.ast._
import org.moe.parser._
import org.moe.interpreter._
import org.moe.runtime._

import java.io.File
import scala.io.Source

object Moe {

  def main (args: Array[String]): Unit = {
    val parser: CommandLineParser = new PosixParser()
    val options: Options          = new Options()

    options.addOption("h", false, "display this message")
    options.addOption("v", false, "display version information")
    options.addOption("u", false, "dump the AST after parsing")
    options.addOption("w", false, "enable many useful warnings")

    val e = new Option("e", "code to evaluate")
    e.setArgs(1)
    e.setArgName("program")
    options.addOption(e)

    /*
     TODO:

     Need to support some of these other options
     as well as look through perl itself to see
     what else we are missing.

     options.addOption("c", false, "check syntax only")
     options.addOption("d", false, "debug mode")
     */

    /*
     TODO:
     wrap this in try/catch block and
     watch for the various exceptions
     most importantly we need to tell
     the difference between a option
     parser error and a code execution
     error.
     - SL
     */

    val cmd: CommandLine = parser.parse(options, args);

    /*
     FIXME:
     I am sure this can be done more
     idiomatically, but just porting
     the java straight for now.
     - SL
     */
    if (cmd.hasOption("h")) {
      printHelp(options)
      return
    }

    val interpreter = new Interpreter()
    val runtime     = new MoeRuntime(
      warnings = cmd.hasOption("w")
    )
    runtime.bootstrap()

    if (cmd.hasOption("v")) {
      printVersionInformation(runtime)
      return
    }

    val dumpAST = cmd.hasOption("u")

    if (cmd.hasOption("e")) {
      val code: String = cmd.getOptionValue("e")
      REPL.evalLine(interpreter, runtime, code, Map("dumpAST" -> dumpAST))
      return
    }
    else {
      val rest: List[String] = cmd.getArgs().toList

      def evalProgram (path: String) = REPL.evalLine(
        interpreter, 
        runtime, 
        Source.fromFile(path).mkString, 
        Map("printOutput" -> false, "dumpAST" -> dumpAST)
      )

      rest match {
        case List()            => REPL.enter(interpreter, runtime, dumpAST)
        case program :: List() => evalProgram(program)
        case program :: args   => {
          runtime.getRootEnv.set(
            "@ARGV", 
            runtime.NativeObjects.getArray(
              args.map(arg => runtime.NativeObjects.getStr(arg)):_*
            )
          )
          evalProgram(program)
        }
      }

    }
  }

  def printHelp (options: Options): Unit = {
    val formatter: HelpFormatter = new HelpFormatter();
    formatter.printHelp("moe", options);
  }

  // NOTE: this likely needs work - SL
  def printVersionInformation (runtime: MoeRuntime): Unit = println(
    "\n" +
      "This is Moe v" + runtime.getVersion + "\n" +
      "\n" +
      "This software is copyright (c) 2013 by Infinity Interactive, Inc.\n" +
      "\n" +
      "This is free software; you can redistribute it and/or \n" +
      "modify it under the terms specified in the LICENSE file.\n"
  )

  object EvalResult extends Enumeration {
    val Success, Partial, Failure = Value
  }

  object REPL {
    def enter (interpreter: Interpreter, runtime: MoeRuntime, dumpAST: Boolean = false): Unit = {
      import jline.{ConsoleReader, History}

      def isReplCommand(input: String) = input(0) == ':'

      var replOptions = Map(
        "printOutput"    -> true,
        "dumpAST"        -> dumpAST,
        "prettyPrintAST" -> true
      )

      val historyFile = new File(System.getProperty("user.home") + File.separator + ".moereplhist")
      val cReader: ConsoleReader = new ConsoleReader
      cReader.setHistory(new History(historyFile))

      val prompt = "moe> "
      val continuationPrompt = "...| "

      var partialInput: String = ""
      while (true) {
        val line = cReader readLine (if (partialInput == "") prompt else continuationPrompt)
        line match {
          case null   => { println(); return }
          case "exit" => return
          case ""     => ""
          case _      => if (isReplCommand(line))
                           replOptions = processReplCommand(line, replOptions)
                         else
                           evalLine(interpreter, runtime, partialInput + line, replOptions) match {
                             case EvalResult.Partial => partialInput += line
                             case _                  => partialInput = ""
                           }
        }
      }
    }

    def evalLine(interpreter: Interpreter, runtime: MoeRuntime, line: String, options: Map[String, Boolean]) = {
      try {
        val nodes = MoeParser.parseFromEntry(line)
        val ast = CompilationUnitNode(
          ScopeNode(nodes)
        )
        if (options("dumpAST")) {
          if (options.contains("prettyPrintAST") && options("prettyPrintAST"))
            println(Serializer.toJSONPretty(ast))
          else
            println(Serializer.toJSON(ast))
        }
        val result = interpreter.eval(runtime, runtime.getRootEnv, ast)
        if( options("printOutput") ) {
          println(result.toString)
        }
        EvalResult.Success
      }
      catch {
        case i: MoeErrors.ParserInputIncomplete => EvalResult.Partial
        case e: Exception => {
          System.err.println(e)
          EvalResult.Failure
        }
      }
    }

    import scala.util.matching.Regex

    def processReplCommand(command: String, options: Map[String, Boolean]): Map[String, Boolean] = {
      def toBoolean(v: String): Boolean = v == "on" || v == "yes" || v == "1"
      val pattern = new Regex(""":set\s+(\w+)\s+(\w+)""", "option", "value")

      pattern findFirstIn command match {
        case Some(pattern(option, value)) => options updated (option, toBoolean(value))
        case None                         => {
          println("Unrecognized command: " + command)
          options
        }
      }
    }
  }
}
