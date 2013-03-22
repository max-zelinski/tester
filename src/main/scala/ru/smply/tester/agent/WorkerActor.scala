package ru.smply.tester.agent

import akka.actor._

import ru.smply.tester.messages._
import akka.util.Duration
import java.util.concurrent.TimeUnit
import ru.smply.tester.messages.TestStepType._
import org.scala_tools.time.Imports._
import ru.smply.tester.configuration.TestPlan
import java.lang.reflect.InvocationTargetException
import ru.smply.tester.scripting._
import ru.smply.tester.scripting.ConfigurationConverters._
import collection.mutable

object WorkerActor {
  trait State
  case object Idle extends State
  case object Waiting extends State
  case object Running extends State
  case object Finished extends State

  case class StateDate(testWrapper: TestWrapper = null)
}

class WorkerActor(val workerId: Int, testPlan: TestPlan,
                  testClass: Class[Test], controller: ActorRef)
  extends Actor with FSM[WorkerActor.State, WorkerActor.StateDate] {
  import WorkerActor._

  var stats: mutable.HashMap[TestStepType, TestStepStats] = mutable.HashMap()

  private def runTestStep(testStepType: TestStepType, testWrapper: TestWrapper)(fun: => Unit) {
    log.info("executting '" + testStepType + "' step")
    val timestamp = DateTime.now

    val startTime = System.nanoTime()
    val exception = try {
      fun
      None
    }
    catch {
      case e: InvocationTargetException =>
        Some(e.getTargetException)
      case e: Exception =>
        Some(e)
    }
    val elapsedTime = System.nanoTime() - startTime

    val took = Duration(elapsedTime, TimeUnit.NANOSECONDS).toMillis

    stats += (testStepType -> TestStepStats(workerId, timestamp, took, testWrapper.getMetricsAndClear()))

    val message = exception match {
      case Some(e) =>
        log.error(e, "got exception during " + testStepType)
        TestRunFailed(e)
      case None =>
        TestStepCompleted(testStepType)
    }

    controller ! message
  }

  startWith(Idle, StateDate())

  when(Idle) {
    case Event(Start, _) =>
      log.info("starting worker actor")

      log.info("sending negotiation to controller at " + controller)
      controller ! Negotiate(ActorType.Worker)
      goto(Waiting)
  }

  when(Waiting) {
    case Event(StartTestInitialization, s: StateDate) =>
      var testWrapper = new TestWrapper(log, workerId, testPlan, testClass)
      runTestStep(TestStepType.Initializing, testWrapper) {
        testWrapper.createTest()
      }

      goto(Running) using s.copy(testWrapper = testWrapper)
  }

  when(Running) {
    case Event(StartTestRun, s: StateDate) =>
      runTestStep(TestStepType.Running, s.testWrapper) {
        s.testWrapper.callTestMethod(testPlan.configuration.scriptMethod)
      }

      goto(Finished)
  }

  when(Finished) {
    case Event(ReportResults, s: StateDate) =>
      controller ! TestRunResults(stats.toMap)
      stay
    case _ => throw new UnsupportedOperationException()
  }

  initialize
}
