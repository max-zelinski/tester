package ru.smply.tester.scripting

import groovy.lang._
import java.io.File

import ru.smply.tester.scripting.{
  TestPlan => JavaTestPlan,
  ControllerConfiguration => JavaControllerConfiguration,
  AgentConfiguration => JavaAgentConfiguration
}

import ru.smply.tester.configuration.{
  TestPlan => ScalaTestPlan,
  ControllerConfiguration => ScalaControllerConfiguration,
  AgentConfiguration => ScalaAgentConfiguration
}

import ru.smply.tester.scripting.ConfigurationConverters._
import org.apache.commons.lang.StringUtils

object ScriptRunner {
  def evalTestScript(script: String): Class[Test] = {
    require(!StringUtils.isBlank(script))

    val gcl = new GroovyClassLoader
    gcl.parseClass(script)

    gcl.getLoadedClasses.find(clazz => classOf[Test].isAssignableFrom(clazz)) match {
      case Some(c) => c.asInstanceOf[Class[Test]]
      case None => throw new IllegalStateException("No test class is found")
    }
  }

  def evalTestPlanScript(scriptPath: String): ScalaTestPlan = {
    require(!StringUtils.isBlank(scriptPath))

    val testPlan = new JavaTestPlan
    val bindings = new Binding()
    bindings.setVariable("settings", testPlan.getSettings)
    bindings.setVariable("parameters", testPlan.getParameters)

    val shell = new GroovyShell(bindings)
    shell.evaluate(new File(scriptPath))

    testPlan
  }

  def evalControllerConfigurationScript(scriptPath: String): ScalaControllerConfiguration = {
    require(!StringUtils.isBlank(scriptPath))

    val config = new JavaControllerConfiguration
    val bindings = new Binding()
    bindings.setVariable("config", config)

    val shell = new GroovyShell(bindings)
    shell.evaluate(new File(scriptPath))

    config
  }

  def evalAgentConfigurationScript(scriptPath: String): ScalaAgentConfiguration = {
    require(!StringUtils.isBlank(scriptPath))
    
    val config = new JavaAgentConfiguration
    val bindings = new Binding()
    bindings.setVariable("config", config)

    val shell = new GroovyShell(bindings)
    shell.evaluate(new File(scriptPath))

    config
  }
}