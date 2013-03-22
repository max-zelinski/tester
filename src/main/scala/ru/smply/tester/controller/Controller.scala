package ru.smply.tester.controller

import ru.smply.tester.configuration.TestPlan
import akka.actor._
import ru.smply.tester.report.ReporterActor
import akka.pattern.ask
import java.util.concurrent.TimeoutException
import ru.smply.tester.messages._
import akka.dispatch.Await
import akka.util._
import org.slf4j.Logger
import io.Source

class Controller(implicit val system: ActorSystem, implicit val log: Logger) {
  def start(testPlan: TestPlan): Boolean = {
    val script = Source.fromFile(testPlan.configuration.scriptPath).mkString

    val reporter = system.actorOf(Props(new ReporterActor(testPlan)), name = "reporter")
    val controller = system.actorOf(Props(new ControllerActor(testPlan, script, reporter)), name = "controller")

    implicit val timeout: Timeout = Duration.parse(testPlan.configuration.timeout)

    val testResult = controller ? Start recover {
      case e: TimeoutException =>
        log.error("timeout")
        TestFailed
    }

    Await.result(testResult, Duration.Inf) match {
      case TestCompleted => true
      case TestFailed => false
    }
  }
}
