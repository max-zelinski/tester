package ru.smply.tester.controller

import akka.actor._
import io.Source
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import ru.smply.tester.scripting.ScriptRunner
import ru.smply.tester.configuration._
import akka.remote.RemoteActorRefProvider

object ControllerApp {
  def main(args: Array[String]) {
    System.setProperty("TESTER_TYPE", "controller")

    implicit val log = LoggerFactory.getLogger(ControllerApp.getClass.getCanonicalName)

    require(args.size == 2, "please provide path to the controller configuration and test plan")

    val controllerConfiguration = ScriptRunner.evalControllerConfigurationScript(args(0))
    log.info("going to use controller configuration " + controllerConfiguration)

    val testPlan = ScriptRunner.evalTestPlanScript(args(1))
    log.info("going to run test plan " + testPlan)

    val script = Source.fromFile(testPlan.configuration.scriptPath).mkString

    log.info("going to execute method '" + testPlan.configuration.scriptMethod + "' from script: \n"
      + script)

    log.info("trying to compile run script")
    try {
      ScriptRunner.evalTestScript(script).getMethod(testPlan.configuration.scriptMethod)
    }
    catch {
      case e: Exception =>
        log.error("run script is invalid", e)
        exit(1)
    }
    log.info("run script is okay")

    val nettyConfiguration = ConfigFactory.parseString("""
      akka.remote.netty.hostname = "%s"
      akka.remote.netty.port = %d
      """.format(controllerConfiguration.hostname, controllerConfiguration.port))
    val akkaConfiguration = ConfigFactory.load().getConfig("controller").withFallback(nettyConfiguration)
    implicit val system = ActorSystem("tester", akkaConfiguration)

    log.info("started akka system at " + system.asInstanceOf[ExtendedActorSystem]
      .provider.asInstanceOf[RemoteActorRefProvider].transport.address)

    val controller = new Controller()
    val returnCode = controller.start(testPlan) match {
      case true => log.info("test completed sucesefully"); 0
      case false => log.error("test failed"); 1
    }

    exit(returnCode)
  }

  private def exit(returnCode: Int) {
    System.exit(returnCode)
  }
}
