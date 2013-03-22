package ru.smply.tester.agent

import akka.actor._
import akka.util.duration._

import ru.smply.tester.messages._
import io.Source
import ru.smply.tester.scripting._
import com.typesafe.config.ConfigFactory
import org.gfork.Fork
import org.gfork.types.Void
import akka.remote.RemoteClientShutdown
import org.slf4j.LoggerFactory

case class WorkerProcess(agentPath: String) extends Runnable {
  def run() {
    System.setProperty("TESTER_TYPE", "workers")

    val log = LoggerFactory.getLogger(classOf[WorkerProcess].getCanonicalName)
    log.info("starting worker process")

    val system = ActorSystem("tester", ConfigFactory.load().getConfig("worker"))
    val agent = system.actorFor(agentPath)
    val worker = system.actorOf(Props(new WorkerProcessActor(agent)), name = "worker-process")

    worker ! Start

    system.awaitTermination()

    log.info("worker process terminated")
  }
}

object WorkerProcess {
  type Process = Fork[WorkerProcess, Void]

  def create(agentPath: String, jvmOptions: String): Process = {
    require(jvmOptions != null && !jvmOptions.isEmpty)

    Fork.setJvmOptionsForAll(null)
    Fork.setJvmOptionsForAll(jvmOptions.split(" "): _*)

    new Fork[WorkerProcess, Void](
      WorkerProcess(agentPath), classOf[WorkerProcess].getMethod("run"))
  }
}

object WorkerProcessActor {
  trait State
  case object Idle extends State
  case object WaitingForAgent extends State
  case object WaitingToStart extends State
  case object Executing extends State

  case class StateData(workers: List[ActorRef] = List.empty)
}

class WorkerProcessActor(val agent: ActorRef)
  extends Actor with FSM[WorkerProcessActor.State, WorkerProcessActor.StateData] {
  import WorkerProcessActor._

  override def supervisorStrategy() = OneForOneStrategy() {
    case e: Exception =>
      log.error(e, "got exception from a worker")
      SupervisorStrategy.Stop
  }

  context.system.eventStream.subscribe(self, classOf[RemoteClientShutdown])

  startWith(Idle, StateData())

  when(Idle) {
    case Event(Start, _) =>
      log.info("starting worker process actor")

      agent ! Negotiate(ActorType.WorkerProcess)
      goto(WaitingForAgent)
  }

  when(WaitingForAgent, stateTimeout = 15 seconds) {
    case Event(Negotiate(ActorType.Agent), _) =>
      log.info("got negotiation from agent " + sender)
      goto(WaitingToStart)
    case Event(StateTimeout, _) =>
      throw new Exception("timeout when waiting for agent")
  }

  when(WaitingToStart) {
    case Event(m: InitializeWorkers, s: StateData) =>
      import m._

      val controller = sender

      log.info("initializing " + testPlan.configuration.workersPerAgent + " workers")
      log.info("executing method '" + testPlan.configuration.scriptMethod + "' from script: \n"
        + script)

      val testClass = ScriptRunner.evalTestScript(script)

      val workers = for (threadId <- 0 until testPlan.configuration.workersPerAgent) yield {
        val workerId = (m.testGroupId * testPlan.configuration.workersPerAgent) + threadId

        context.actorOf(Props(new WorkerActor(workerId, testPlan, testClass, controller))
          .withDispatcher("akka.thread-test-dispatcher"),
          name = "worker-" + workerId)
      }

      log.info("starting " + testPlan.configuration.workersPerAgent + " workers")
      workers.foreach(_ ! Start)

      goto(Executing) using s.copy(workers = workers.toList)
  }

  when(Executing) {
    case Event(m: TestMessage, s: StateData) =>
      log.info("forwarding message " + m.toString + " to workers")

      s.workers.foreach(_ forward (m))
      stay
  }

  whenUnhandled {
    case Event(RemoteClientShutdown(_, remoteAddress), s: StateData)
      if remoteAddress == agent.path.address =>
      throw new Exception("agent disconnected")
  }

  onTermination {
    case StopEvent(_, _, _) => System.exit(0)
  }

  initialize
}