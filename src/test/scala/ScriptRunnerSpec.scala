import akka.event.LoggingAdapter
import java.io.FileNotFoundException
import org.junit.runner.RunWith
import org.scalamock.ProxyMockFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner
import ru.smply.tester.scripting._
import ru.smply.tester.scripting.ConfigurationConverters._

@RunWith(classOf[JUnitRunner])
class ScriptRunnerSpec extends FunSpec with MockFactory with ProxyMockFactory {
  describe("when running controller config") {
    it("should throw FileNotFoundException if a non existing controller config path was passed") {
      intercept[FileNotFoundException] {
        ScriptRunner.evalControllerConfigurationScript("doesnt-exist.conf")
      }
    }

    it("should throw IllegalArgumentException on empty controller config ") {
      intercept[IllegalArgumentException] {
        ScriptRunner.evalControllerConfigurationScript("src/test/resources/empty.conf")
      }
    }

    it("should return expected controller config") {
      val config = ScriptRunner.evalControllerConfigurationScript("src/test/resources/controller.conf")

      assert(config.hostname === "127.0.0.1")
      assert(config.port === 2552)
    }
  }

  describe("when running agent config") {
    it("should throw FileNotFoundException if a non existing agent config path was passed") {
      intercept[FileNotFoundException] {
        ScriptRunner.evalAgentConfigurationScript("doesnt-exist.conf")
      }
    }

    it("should throw IllegalArgumentException on empty agent config ") {
      intercept[IllegalArgumentException] {
        ScriptRunner.evalAgentConfigurationScript("src/test/resources/empty.conf")
      }
    }

    it("should return expected agent config") {
      val config = ScriptRunner.evalAgentConfigurationScript("src/test/resources/agent.conf")

      assert(config.controller === "controller_path")
      assert(config.workerProcessJvmOptions === "-Xmx64m")
    }
  }

  describe("when running test plan") {
    it("should throw FileNotFoundException if a non existing test plan path was passed") {
      intercept[FileNotFoundException] {
        ScriptRunner.evalTestPlanScript("doesnt-exist.conf")
      }
    }

    it("should throw IllegalArgumentException on empty test plan") {
      intercept[IllegalArgumentException] {
        ScriptRunner.evalTestPlanScript("src/test/resources/empty.conf")
      }
    }

    it("should throw CastException when test plan parameters contains non string value") {
      intercept[ClassCastException] {
        ScriptRunner.evalTestPlanScript("src/test/resources/test-plan-with-invalid-parameters.conf")
      }
    }

    it("should return expected test plan") {
      val testPlan = ScriptRunner.evalTestPlanScript("src/test/resources/test-plan.conf")

      assert(testPlan.configuration.timeout === "10 minutes")
      assert(testPlan.configuration.expectedAgents === 1)
      assert(testPlan.configuration.workersPerAgent === 10)
      assert(testPlan.configuration.scriptPath === "script/test.groovy")
      assert(testPlan.configuration.scriptMethod === "test")
      assert(testPlan.configuration.resultPath === "results/perf.json")

      assert(testPlan.parameters("param") === "param-value")
      assert(testPlan.parameters("param2").toString === "5")
    }
  }

  describe("when compiling test script") {
    it("should throw FileNotFoundException if a null test script path was passed") {
      intercept[IllegalArgumentException] {
        ScriptRunner.evalTestScript(null)
      }
    }

    it("should throw IllegalStateException on an empty but valid test script") {
      intercept[IllegalStateException] {
        ScriptRunner.evalTestScript("println")
      }
    }

    it("should return expected test class") {
      val clazz = ScriptRunner.evalTestScript(scala.io.Source.fromFile("src/test/resources/test.groovy").mkString)

      assert(clazz != null)
      assert(classOf[Test].isAssignableFrom(clazz))
    }
  }

  describe("test class run method") {
    it("should complete successfully") {
      val testPlan = ScriptRunner.evalTestPlanScript("src/test/resources/test-plan.conf")
      val testClass = ScriptRunner.evalTestScript(scala.io.Source.fromFile("src/test/resources/test.groovy").mkString)
      val log = mock[LoggingAdapter]
      log expects 'info anyNumberOfTimes

      val testWrapper = new TestWrapper(log, 1, testPlan, testClass)
      testWrapper.createTest()
      testWrapper.callTestMethod("test")
    }
  }
}