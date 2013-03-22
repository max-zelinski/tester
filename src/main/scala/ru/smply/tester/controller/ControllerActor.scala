package ru.smply.tester.controller

import akka.actor._
import akka.remote.RemoteClientShutdown
import akka.actor.FSM._

import ru.smply.tester.messages._
import ru.smply.tester.configuration._
import org.scala_tools.time.Imports._
import akka.pattern.ask
import akka.dispatch.Await
import akka.util.Timeout

object ControllerActor {
  trait State
  case object Idle extends State
  case object WaitingForAgents extends State
  case object WaitingForWorkers extends State
  case object WaitingForTestInitialized extends State
  case object WaitingForTestCompleted extends State
  case object WaitingForTestReports extends State

  case class StateData(startTime: DateTime = null,
                       invoker: ActorRef = null,
                       agentsConnected: List[ActorRef] = List.empty,
                       numOfWorkersConnected: Int = 0,
                       numOfWorkersInitialized: Int = 0,
                       numOfWorkersCompleted: Int = 0,
                       numOfWorkersReported: Int = 0)
}

class ControllerActor(val testPlan: TestPlan, val script: String, val reporter: ActorRef)
  extends Actor with LoggingFSM[ControllerActor.State, ControllerActor.StateData] {
  import ControllerActor._
  import testPlan.configuration._

  context.system.eventStream.subscribe(self, classOf[RemoteClientShutdown])

  val expectedWorkers = expectedAgents * workersPerAgent

  startWith(Idle, StateData())

  when(Idle) {
    case Event(Start, s: StateData) =>
      log.info("waiting for " + expectedAgents + " agent(s) to connect")

      goto(WaitingForAgents) using s.copy(startTime = DateTime.now, invoker = sender)
  }

  when(WaitingForAgents) {
    case Event(Negotiate(ActorType.Agent), s: StateData) =>
      sender ! Negotiate(ActorType.Controller)

      val newState = s.copy(agentsConnected = sender :: s.agentsConnected)
      if (newState.agentsConnected.size == expectedAgents) {
        log.info("all agents are connected")
        log.info("starting workers initialization")
        for ((agent, index) <- newState.agentsConnected.zipWithIndex) {
          agent ! InitializeWorkers(index, testPlan, script)
        }
        goto(WaitingForWorkers) using newState
      }
      else {
        log.info(newState.agentsConnected.size + " out of " + expectedAgents + " agents connected")
        stay using newState
      }
  }

  when(WaitingForWorkers) {
    case Event(Negotiate(ActorType.Worker), s: StateData) =>
      val newState = s.copy(numOfWorkersConnected = s.numOfWorkersConnected + 1)
      if (newState.numOfWorkersConnected == expectedWorkers) {
        log.info("all workers are ready")
        log.info("starting test initialization")
        s.agentsConnected.foreach(_ ! StartTestInitialization)
        goto(WaitingForTestInitialized) using newState
      }
      else {
        log.info(newState.numOfWorkersConnected + " out of " + expectedWorkers + " workers ready")
        stay using newState
      }
  }

  when(WaitingForTestInitialized) {
    case Event(t @ TestStepCompleted(TestStepType.Initializing), s: StateData) =>
      val newState = s.copy(numOfWorkersInitialized = s.numOfWorkersInitialized + 1)
      if (newState.numOfWorkersInitialized == expectedWorkers) {
        log.info("all workers are initialized")
        s.agentsConnected.foreach(_ ! StartTestRun)
        goto(WaitingForTestCompleted) using newState
      }
      else {
        log.info(newState.numOfWorkersInitialized + " out of " + expectedWorkers + " workers initialized")
        stay using newState
      }
  }

  when(WaitingForTestCompleted) {
    case Event(t @ TestStepCompleted(TestStepType.Running), s: StateData) =>
      val newState = s.copy(numOfWorkersCompleted = s.numOfWorkersCompleted + 1)
      if (newState.numOfWorkersCompleted == expectedWorkers) {
        log.info("test is finished")
        log.info("waiting for results")
        s.agentsConnected.foreach(_ ! ReportResults)
        goto(WaitingForTestReports) using newState
      }
      else {
        log.info(newState.numOfWorkersCompleted + " out of " + expectedWorkers + " workers finished the test")
        stay using newState
      }
  }

  when(WaitingForTestReports) {
    case Event(t: TestRunResults, s: StateData) =>
      reporter ! t
      val newState = s.copy(numOfWorkersReported = s.numOfWorkersReported + 1)
      if (newState.numOfWorkersReported == expectedWorkers) {
        log.info("received all reports")
        stop(Normal)
      }
      else {
        log.info(newState.numOfWorkersReported + " out of " + expectedWorkers + " workers reported")
        stay using newState
      }
  }

  whenUnhandled {
    case Event(r: RemoteClientShutdown, s: StateData)
      if s.agentsConnected.exists(a => a.path.address == r.remoteAddress) =>
      stop(Failure("one of agents disconnected"))
    case Event(r: RemoteClientShutdown, _) => stay
    case Event(t: TestRunFailed, s: StateData) =>
      val message = "got exception " + t.exception + " from worker " + sender
      log.error(t.exception, message)
      stop(Failure(null))
    case Event(Negotiate(_), _) => stay()
  }

  def stopController(success: Boolean) {
    val (logMessage, resultMessage) = if (success)
      ("test completed", TestCompleted)
    else
      ("test failed", TestFailed)

    log.info(logMessage)

    log.info("sending stop to agents")
    stateData.agentsConnected.foreach(_ ! Stop)

    log.info("saving report")
    implicit val timeout = Timeout(Int.MaxValue)
    val result = reporter ? SaveTestReport(stateData.startTime, DateTime.now.millis - stateData.startTime.millis)
    Await.result(result, timeout.duration)

    stateData.invoker ! resultMessage
  }

  onTermination {
    case StopEvent(f: Failure, Idle, _) =>
      log.info("test failed")
      stateData.invoker ! TestFailed
    case StopEvent(Normal, _, _) =>
      stopController(true)
    case StopEvent(Failure(_) | Shutdown, state, _) =>
      stopController(false)
  }

  initialize
}
