package ru.smply.tester.report

import akka.actor._
import ru.smply.tester.messages._
import java.io.File
import scalax.io.Resource
import org.scala_tools.time.Imports._
import net.liftweb.json._
import ext.JodaTimeSerializers
import net.liftweb.json.Serialization._
import ru.smply.tester.configuration.TestPlan

object Report {
  def fromJson(json: String): Report = {
    implicit val formats = Serialization.formats(NoTypeHints) ++ JodaTimeSerializers.all
    read[Report](json)
  }
}

case class Report(startTime: DateTime, took: Long, steps: List[TestStepReport], testPlan: TestPlan) {
  val (additionalParameters, additionalMeasurements) = {
    val paramsList = for {
      testStepReport <- steps
      metrics <- testStepReport.stats.metrics
    }
    yield {
      val parameters = for {
        parameterName <- metrics.additionalParameters.keySet
      } yield parameterName

      val measurements = for {
        measurementName <- metrics.additionalMeasurements.keySet
      } yield measurementName

      (parameters, measurements)
    }

    val parameters = (for ((parameters, _) <- paramsList; parameter <- parameters) yield parameter).toSet
    val measurements = (for ((_, measurements) <- paramsList; measurement <- measurements) yield measurement).toSet

    (parameters.toList, measurements.toList)
  }

  def toJson: String = {
    implicit val formats = Serialization.formats(NoTypeHints) ++ JodaTimeSerializers.all
    write(this)
  }
}

case class TestStepReport(testStepType: String, stats: TestStepStats)

object ReportRepository {
  def save(report: Report, reportPath: String) {
    val file = new File(reportPath)
    if (file.exists()) file.delete()

    Resource.fromFile(file).write(report.toJson)
  }

  def load(reportPath: String) : Report = {
    val file = new File(reportPath)
    if (!file.exists()) throw new Exception("file " + reportPath + " doesn't exist")

    Report.fromJson(Resource.fromFile(file).reader.lines().mkString)
  }
}

class ReporterActor(testPlan: TestPlan) extends Actor with ActorLogging {
  var steps: List[TestStepReport] = List()

  def receive = {
    case t: TestRunResults =>
      steps = (for ((testStepType, stats) <- t.stats) yield TestStepReport(testStepType.toString, stats)).toList ::: steps
    case s: SaveTestReport =>
      val reportPath = testPlan.configuration.resultPath.replace("%timestamp%", s.startTime.toString("yyyyMMdd'T'HHmmss"))

      ReportRepository.save(Report(s.startTime, s.took, steps.reverse, testPlan), reportPath)

      log.info("saved report to " + reportPath)
      sender ! true
  }
}