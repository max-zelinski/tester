package ru.smply.tester.scripting

import akka.event._
import akka.util.Duration
import java.util.concurrent.TimeUnit
import org.scala_tools.time.Imports._

import ru.smply.tester.metrics._
import util.DynamicVariable
import collection.mutable.{ListBuffer}
import groovy.lang.Closure
import reflect.BeanProperty
import java.util.HashMap
import scala.collection.JavaConversions._

class Measurement[T](@BeanProperty val timestamp: DateTime, @BeanProperty val name: String,
                     @BeanProperty val took: Long, @BeanProperty val result: T,
                     @BeanProperty val exception: Exception = null) {
  @BeanProperty var successful = exception == null
  @BeanProperty var parameters = new HashMap[String, String]()
  @BeanProperty var measurements = new HashMap[String, Int]()
}

class TestWrapper(loggerAdapter: LoggingAdapter, workerId: Int, testPlan: TestPlan, testClass: Class[Test]) {
  import Test._
  private val measurements: ListBuffer[Measurement[_]] = ListBuffer()
  private var test: Test = _

  def createTest() {
    test = context.withValue(Context(loggerAdapter, workerId, testPlan, measurements)) {
      testClass.newInstance()
    }
  }

  def callTestMethod(methodName: String) {
    require(test != null)

    val method = test.getClass.getMethod(methodName)
    method.invoke(test)
  }

  def getMetricsAndClear(): List[Metric] = {
    measurements.map(measure => Metric(measure.timestamp, measure.name, measure.successful, measure.took,
      measure.parameters.toMap, measure.measurements.toMap)).toList
  }
}

object Test {
  case class Context(loggerAdapter: LoggingAdapter, workerId: Int, testPlan: TestPlan,
                     measurements: ListBuffer[Measurement[_]])

  val context: DynamicVariable[Context] = new DynamicVariable(null)
}

class Test {
  import Test._

  println("ctor Test")

  require(context.value != null)

  private val loggerAdapter = context.value.loggerAdapter
  @BeanProperty protected final val workerId = context.value.workerId
  @BeanProperty protected final val testPlan = context.value.testPlan
  @BeanProperty protected final val measurements = context.value.measurements
  @BeanProperty protected final val log = new Log()

  def measure[T](name: String, closure: Closure[T]): Measurement[T] = {
    measure(name, false, closure)
  }

  def measure[T](name: String, suppressException: Boolean, closure: Closure[T]): Measurement[T] = {
    case class default[T]() {
      var value:T = _
    }

    val startTime = System.nanoTime()
    val result: Either[Exception, T] = try {
      Right(closure.call())
    }
    catch {
      case e: Exception =>
        if (suppressException) {
          Left(e)
        }
        else {
          throw e
        }
    }

    val took = Duration(System.nanoTime() - startTime, TimeUnit.NANOSECONDS).toMillis

    val measurement = result match {
      case Right(r) => new Measurement[T](DateTime.now, name, took, r)
      case Left(e) => new Measurement[T](DateTime.now, name, took, default().value, exception = e)
    }

    measurements += measurement
    measurement
  }

  class Log {
    def info(message: String) {
      loggerAdapter.info(wrapMessage("info", message))
    }

    def debug(message: String) {
      loggerAdapter.info(wrapMessage("debug", message))
    }

    def error(message: String) {
      loggerAdapter.info(wrapMessage("error", message))
    }

    private def wrapMessage(severity: String, message: String) = {
      severity + " (worker id: " + workerId + "): " + message
    }
  }
}
