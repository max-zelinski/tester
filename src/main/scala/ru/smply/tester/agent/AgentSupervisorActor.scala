package ru.smply.tester.agent

import akka.actor._
import akka.util.duration._
import ru.smply.tester.messages._
import ru.smply.tester.configuration.AgentConfiguration

class AgentSupervisorActor(val agentConfiguration: AgentConfiguration) extends Actor with ActorLogging {
  override def supervisorStrategy() = OneForOneStrategy() {
    case e: Exception =>
      log.error(e, "got exception from an actor, will wait 5 seconds then restart")
      Thread.sleep((5 seconds).toMillis)
      SupervisorStrategy.Stop
  }

  def startAgentActor() {
    val actor = context.actorOf(Props(new AgentActor(agentConfiguration)), name = "agent")
    context.watch(actor)
    actor ! Start
  }

  def receive = {
    case Start =>
      log.info("starting agent actor supervisor")
      startAgentActor()
    case t: Terminated =>
      log.info("got termination message from agent " + t.actor)
      log.info("restarting")
      startAgentActor()
  }
}
