package ru.smply.tester.render

import ru.smply.tester.report._
import ru.smply.tester.scripting._
import io.Source
import akka.util.Duration
import java.util.concurrent.TimeUnit

object RenderApp {
  def main(args: Array[String]) {
    System.setProperty("TESTER_TYPE", "render")

    require(args.size == 1, "please provide path to the result file")

    val resultPath = args(0)
    val outputFilename = resultPath.replace(".json", ".xlsx")

    println("going to render " + resultPath + " to " + outputFilename)

    def measure[T](message: String)(fun: => T): T = {
      val startTime = System.nanoTime()
      val result = fun
      val elapsedTime = System.nanoTime() - startTime

      val took = Duration(elapsedTime, TimeUnit.NANOSECONDS).toMillis
      println(message + " took " + took)

      result
    }

    val report = measure("loading of the report") {
      ReportRepository.load(resultPath)
    }

    measure("processing of the report") {
      ExcelRender.render(report, outputFilename)
    }
  }
}