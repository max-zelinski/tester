package ru.smply.tester.configuration

import org.apache.commons.lang.StringUtils

case class TestPlan(configuration: Configuration, parameters: Map[String, String]) {
  require(configuration != null)
}

case class Configuration(timeout: String, expectedAgents: Int, workersPerAgent: Int,
                         scriptPath: String, scriptMethod: String, resultPath: String) {
  require(!StringUtils.isBlank(timeout))
  require(expectedAgents != 0)
  require(workersPerAgent != 0)
  require(!StringUtils.isBlank(scriptPath))
  require(!StringUtils.isBlank(scriptMethod))
  require(!StringUtils.isBlank(resultPath))
}

case class AgentConfiguration(controller: String, workerProcessJvmOptions: String) {
  require(!StringUtils.isBlank(controller))
  require(!StringUtils.isBlank(workerProcessJvmOptions))
}

case class ControllerConfiguration(hostname: String, port: Int) {
  require(!StringUtils.isBlank(hostname))
  require(port != 0)
}