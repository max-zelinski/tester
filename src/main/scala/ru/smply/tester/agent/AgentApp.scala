package ru.smply.tester.agent

import com.typesafe.config.ConfigFactory
import akka.actor._
import io.Source

import ru.smply.tester.messages._
import org.slf4j.LoggerFactory
import ru.smply.tester.configuration.AgentConfiguration
import ru.smply.tester.scripting.ScriptRunner

object AgentApp {
  def main(args: Array[String]) {
    System.setProperty("TESTER_TYPE", "agent")

    val log = LoggerFactory.getLogger(AgentApp.getClass.getCanonicalName)
    log.info("starting agent app")

    require(args.size == 1, "please provide path to the agent configuration")

    val agentConfiguration = ScriptRunner.evalAgentConfigurationScript(args(0))
    log.info("going to use agent configuration " + agentConfiguration)

    val system = ActorSystem("tester", ConfigFactory.load().getConfig("agent"))
    val agent = system.actorOf(Props(new AgentSupervisorActor(agentConfiguration)),
      name = "agent-supervisor")
    agent ! Start

    system.awaitTermination()
  }
}
