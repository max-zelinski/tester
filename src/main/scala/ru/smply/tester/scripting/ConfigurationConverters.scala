package ru.smply.tester.scripting

import ru.smply.tester.scripting.{
  TestPlan => JavaTestPlan,
  Settings => JavaSettings,
  ControllerConfiguration => JavaControllerConfiguration,
  AgentConfiguration => JavaAgentConfiguration
}

import ru.smply.tester.configuration.{
  TestPlan => ScalaTestPlan,
  Configuration => ScalaConfiguration,
  ControllerConfiguration => ScalaControllerConfiguration,
  AgentConfiguration => ScalaAgentConfiguration
}

import java.util.HashMap
import scala.collection.JavaConversions._

object ConfigurationConverters {
  implicit def ScalaTestPlanAsJava(testPlan: ScalaTestPlan): JavaTestPlan = {
    new JavaTestPlan(new JavaSettings(testPlan.configuration.timeout, testPlan.configuration.expectedAgents,
      testPlan.configuration.workersPerAgent, testPlan.configuration.scriptPath, testPlan.configuration.scriptMethod,
      testPlan.configuration.resultPath), new HashMap(testPlan.parameters))
  }

  implicit def JavaTestPlanAsScala(testPlan: JavaTestPlan): ScalaTestPlan = {
    ScalaTestPlan(ScalaConfiguration(testPlan.getSettings.getTimeout, testPlan.getSettings.getExpectedAgents,
      testPlan.getSettings.getWorkersPerAgent, testPlan.getSettings.getScriptPath, testPlan.getSettings.getScriptMethod,
      testPlan.getSettings.getResultPath), testPlan.getParameters.toMap)
  }

  implicit def ScalaControllerConfigurationAsJava(config: ScalaControllerConfiguration): JavaControllerConfiguration = {
    new JavaControllerConfiguration(config.hostname, config.port)
  }

  implicit def JavaControllerConfigurationAsScala(config: JavaControllerConfiguration): ScalaControllerConfiguration = {
    ScalaControllerConfiguration(config.getHostname, config.getPort)
  }

  implicit def ScalaAgentConfigurationAsJava(config: ScalaAgentConfiguration): JavaAgentConfiguration = {
    new JavaAgentConfiguration(config.controller, config.workerProcessJvmOptions)
  }

  implicit def JavaAgentConfigurationAsScala(config: JavaAgentConfiguration): ScalaAgentConfiguration = {
    ScalaAgentConfiguration(config.getController, config.getWorkerProcessJvmOptions)
  }
}