package ru.smply.tester.agent

import akka.actor._
import akka.util.duration._

import ru.smply.tester.messages._
import akka.remote._
import java.io.OutputStreamWriter
import akka.actor.FSM._
import ru.smply.tester.configuration.AgentConfiguration

object AgentActor {
  trait State
  case object Idle extends State
  case object WaitingForWorker extends State
  case object WaitingForController extends State
  case object ExecutingTest extends State

  case class StateData(negotiationRetry: Cancellable = null,
                       worker: ActorRef = null,
                       workerProcess: WorkerProcess.Process = null,
                       controller: ActorRef = null)

  case object TryNegotiation
}

class AgentActor(val agentConfiguration: AgentConfiguration) extends Actor
  with FSM[AgentActor.State, AgentActor.StateData] {
  import AgentActor._
  import agentConfiguration._

  context.system.eventStream.subscribe(self, classOf[RemoteClientShutdown])

  startWith(Idle, StateData())

  when(Idle) {
    case Event(Start, s: StateData) =>
      log.info("starting agent actor")

      val selfPath = self.path.toStringWithAddress(context.system.asInstanceOf[ExtendedActorSystem]
        .provider.asInstanceOf[RemoteActorRefProvider].transport.address)

      val workerProcess = WorkerProcess.create(selfPath, workerProcessJvmOptions)
      workerProcess.setStdOutWriter(new OutputStreamWriter(System.out))
      workerProcess.setStdErrWriter(new OutputStreamWriter(System.err))
      workerProcess.execute()

      log.info("started worker process")

      goto(WaitingForWorker) using s.copy(workerProcess = workerProcess)
  }

  when(WaitingForWorker, stateTimeout = 15 seconds) {
    case Event(Negotiate(ActorType.WorkerProcess), s: StateData) =>
      log.debug("got negotiation from worker process " + sender)

      sender ! Negotiate(ActorType.Agent)

      val negotiationRetry = context.system.scheduler.schedule(0 millis, 5 seconds, self, TryNegotiation)
      goto(WaitingForController) using s.copy(worker = sender, negotiationRetry = negotiationRetry)
    case Event(StateTimeout, _) => stop(Failure("timeout when waiting for worker process"))
  }

  when(WaitingForController) {
    case Event(TryNegotiation, _) =>
      context.actorFor(controller) ! Negotiate(ActorType.Agent)
      stay
    case Event(Negotiate(ActorType.Controller), s: StateData) =>
      log.info("got negotiation from controller " + sender)
      s.negotiationRetry.cancel()
      val controller = sender

      goto(ExecutingTest) using s.copy(controller = controller)
  }

  def forwardMessageToWorker(message: Any) {
    log.debug("forwarding message " + message.toString + " to worker process")
    stateData.worker forward (message)
  }

  when(ExecutingTest) {
    case Event(m: InitializeWorkers, s: StateData) =>
      forwardMessageToWorker(m)
      stay
    case Event(m: TestMessage, s: StateData) =>
      forwardMessageToWorker(m)
      stay
  }

  whenUnhandled {
    case Event(Stop, _) =>
      log.info("controller requested to stop")
      stop(Normal)
    case Event(r: RemoteClientShutdown, s: StateData)
      if s.controller != null && s.controller.path.address == r.remoteAddress =>
      log.info("controller disconnected")
      stop(Normal)
    case Event(r: RemoteClientShutdown, s: StateData)
      if s.worker != null && s.worker.path.address == r.remoteAddress =>
      log.error("worker process disconected")
      stop(Failure(null))
    case Event(r: RemoteClientShutdown, _) => stay
  }

  onTermination {
    case StopEvent(_, _, s: StateData) =>
      if (s.workerProcess != null) {
        log.info("killing worker process")
        s.workerProcess.kill()
      }
  }

  onTransition {
    case _ -> WaitingForController => log.info("waiting for controller at " + controller)
  }

  initialize
}
