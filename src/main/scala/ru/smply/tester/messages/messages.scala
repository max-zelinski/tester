package ru.smply.tester.messages

import org.joda.time.DateTime
import ru.smply.tester.metrics._
import ru.smply.tester.configuration._

object ActorType extends Enumeration {
  type ActorType = Value

  val Controller, Agent, WorkerProcess, Worker = Value
}
import ActorType._

case object Start
case object Stop
case class Negotiate(actorType: ActorType)

case object TestCompleted
case object TestFailed

case class InitializeWorkers(testGroupId: Int, testPlan: TestPlan, script: String)

trait TestMessage
case object StartTestInitialization extends TestMessage
case object StartTestRun extends TestMessage
case object ReportResults extends TestMessage

object TestStepType extends Enumeration {
  type TestStepType = Value

  val Initializing, Running = Value
}
import TestStepType._

case class TestStepStats(workerId: Int, started: DateTime, took: Long, metrics: List[Metric])

case class TestStepCompleted(testStepType: TestStepType)
case class TestRunFailed(exception: Throwable)
case class TestRunResults(stats: Map[TestStepType, TestStepStats])

case class SaveTestReport(startTime: DateTime, took: Long)


