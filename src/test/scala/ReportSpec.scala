import ru.smply.tester.configuration._
import ru.smply.tester.messages.TestStepType
import ru.smply.tester.metrics.Metric
import ru.smply.tester.report._
import org.joda.time.DateTime
import org.scalatest.FunSpec
import util.Random
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class ReportSpec extends FunSpec {
  import Util._

  def createReportStepInfo = {
    TestStepReport(TestStepType.Initializing.toString, createTestStepStatistics(List(
      Metric(DateTime.now, "run1", Random.nextBoolean(), Random.nextLong(),
        additionalParameters = Map("parameter-h1" -> "parameter-v1"),
        additionalMeasurements = Map(
          "measurement-h1" -> Random.nextInt(),
          "measurement-h2" -> Random.nextInt())),
      Metric(DateTime.now, "run1", Random.nextBoolean(), Random.nextLong(),
        additionalParameters = Map(
          "parameter-h1" -> "parameter-v1",
          "parameter-h2" -> "parameter-v2",
          "parameter-h3" -> ""),
        additionalMeasurements = Map(
          "measurement-h1" -> Random.nextInt())),
      Metric(DateTime.now, "run2", Random.nextBoolean(), Random.nextLong(),
        additionalParameters = Map(
          "parameter-h1" -> "parameter-v1",
          "parameter-h2" -> "parameter-v2",
          "parameter-h3" -> ""),
        additionalMeasurements = Map(
          "measurement-h1" -> Random.nextInt()))
    )), Random.nextBoolean(), Random.nextString(10))
  }

  val report = Report(DateTime.now, Random.nextLong, List(createReportStepInfo, createReportStepInfo),
    TestPlan(Configuration("5 seconds", 10, 5, "scriptPath", "scriptMethod", "resultPath"),
      Map("parameter1" -> "value1", "parameter2" -> "value2")))

  describe("A Report") {
    it("should serialize and desirialize expectacly") {
      val json = report.toJson
      println(json)
      val loadedReport = Report.fromJson(json)
      assert(report === loadedReport)
    }

    it("should return expected additionalParameters") {
      assert(report.additionalParameters.mkString(",") === "parameter-h1,parameter-h2,parameter-h3")
    }

    it("should return expected additionalMeasurements") {
      assert(report.additionalMeasurements.mkString(",") === "measurement-h1,measurement-h2")
    }
  }
}