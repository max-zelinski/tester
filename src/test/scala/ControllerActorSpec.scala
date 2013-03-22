import akka.actor._
import akka.remote.RemoteClientShutdown
import akka.testkit._
import akka.testkit.TestActor.AutoPilot
import akka.util.duration._
import ru.smply.tester.messages._
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import ru.smply.tester.configuration._
import ru.smply.tester.controller.ControllerActor
import ru.smply.tester.controller.ControllerActor._

@RunWith(classOf[JUnitRunner])
class ControllerActorSpec extends TestKit(ActorSystem("TestSystem")) with FunSpec with ImplicitSender {
  import Util._

  val testPlan = TestPlan(Configuration("5 seconds", expectedAgents = 2, workersPerAgent = 3,
    scriptPath = "scriptPath", scriptMethod = "scriptMethod", resultPath = "resultPath"), Map())
  val script = "script"
  val expectedWorkers = testPlan.configuration.expectedAgents * testPlan.configuration.workersPerAgent

  type controllerTestType = TestFSMRef[State, StateData, ControllerActor]

  val reporter = TestProbe()
  reporter.setAutoPilot(new AutoPilot {
    def run(sender: ActorRef, msg: Any) = {
      msg match {
        case m: SaveTestReport =>
          sender ! true
          Some(this)
        case _ => Some(this)
      }
    }
  })

  val controller: controllerTestType = TestFSMRef(new ControllerActor(testPlan, script, reporter.ref))
  val starter = TestProbe()
  val agent1 = TestProbe()
  val agent2 = TestProbe()

  describe("A Controller") {
    def shouldIgnoreWhenAgentsConnect(controller: controllerTestType) {
      it("should ignore new agents connected") {
        val currentState = controller.stateName
        val currentStateData = controller.stateData
        controller ! Negotiate(ActorType.Agent)

        assert(controller.stateName === currentState)
        assert(controller.stateData === currentStateData)
      }
    }

    def shouldShutdownOnAgentDisconnected(controller: controllerTestType) {
      it("should stop on agent disconnected") {
        val newInvoker = TestProbe()
        val newController = TestFSMRef(new ControllerActor(testPlan, script, reporter.ref))
        newController.setState(controller.stateName, controller.stateData.copy(invoker = newInvoker.ref))

        newController ! RemoteClientShutdown(null, agent1.ref.path.address)

        agent1.expectMsg(Stop)
        agent2.expectMsg(Stop)
        reporter.expectMsgClass(classOf[SaveTestReport])

        newInvoker.expectMsg(TestFailed)
        assert(newController.isTerminated === true)
      }
    }

    def shouldStopOnExceptionFromWorker(testStepType: TestStepType.TestStepType) {
      it("should stop agents if one of workers failed to " + testStepType) {
        val message = TestStepFailed(testStepType, createTestStepStatistics, new Exception())

        val newController = TestFSMRef(new ControllerActor(testPlan, script, reporter.ref))
        val newInvoker = TestProbe()
        newController.setState(controller.stateName, controller.stateData.copy(invoker = newInvoker.ref))

        newController ! message

        agent1.expectMsg(Stop)
        agent2.expectMsg(Stop)

        reporter.expectMsg(message)
        reporter.expectMsgClass(classOf[SaveTestReport])

        newInvoker.expectMsg(TestFailed)
        assert(newController.isTerminated === true)
      }
    }

    it("should start with idle state") {
      assert(controller.stateName === ControllerActor.Idle)
    }

    it("should go to WaitingForAgents on Start message") {
      starter.send(controller, Start)

      assert(controller.stateName === ControllerActor.WaitingForAgents)
    }

    it("should not shutdown on RemoteClientShutdown") {
      controller ! RemoteClientShutdown(null, agent1.ref.path.address)
      controller ! RemoteClientShutdown(null, agent2.ref.path.address)

      assert(controller.isTerminated === false)
    }

    describe("when WaitingForAgents") {
      it("should send Negotiate(Controller) to connected agent 1") {
        controller.tell(Negotiate(ActorType.Agent), agent1.ref)
        agent1.expectMsg(Negotiate(ActorType.Controller))
      }

      it("should stop on agent disconnected") {
        val newInvoker = TestProbe()
        val newController = TestFSMRef(new ControllerActor(testPlan, script, reporter.ref))
        newController.setState(controller.stateName, controller.stateData.copy(invoker = newInvoker.ref))

        newController ! RemoteClientShutdown(null, agent1.ref.path.address)

        agent1.expectMsg(Stop)
        reporter.expectMsgClass(classOf[SaveTestReport])

        newInvoker.expectMsg(TestFailed)
        assert(newController.isTerminated === true)
      }

      it("should send Negotiate(Controller) to connected agent 2") {
        controller.tell(Negotiate(ActorType.Agent), agent2.ref)
        agent2.expectMsg(Negotiate(ActorType.Controller))
      }

      it("should send InitializeWorkers to all agents when they all are connected") {
        agent1.expectMsg(InitializeWorkers(1, testPlan, script))
        agent2.expectMsg(InitializeWorkers(0, testPlan, script))
      }

      it("should go to WaitingForWorkers when all agents are connected") {
        assert(controller.stateName === ControllerActor.WaitingForWorkers)
        assert(controller.stateData.agentsConnected === List(agent2.ref, agent1.ref))
      }
    }

    describe("when WaitingForWorkers") {
      shouldIgnoreWhenAgentsConnect(controller)
      shouldShutdownOnAgentDisconnected(controller)

      it("should send StartTestInitialization when all workers are connected") {
        controller.tell(Negotiate(ActorType.Worker), TestProbe().ref)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        controller.tell(Negotiate(ActorType.Worker), TestProbe().ref)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        controller.tell(Negotiate(ActorType.Worker), TestProbe().ref)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        controller.tell(Negotiate(ActorType.Worker), TestProbe().ref)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        controller.tell(Negotiate(ActorType.Worker), TestProbe().ref)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        controller.tell(Negotiate(ActorType.Worker), TestProbe().ref)

        agent1.expectMsg(StartTestInitialization)
        agent2.expectMsg(StartTestInitialization)
      }

      it("should go to WaitingForTestInitialized") {
        assert(controller.stateName === ControllerActor.WaitingForTestInitialized)
        assert(controller.stateData.numOfWorkersConnected === expectedWorkers)
      }
    }

    describe("when WaitingForTestInitialized") {
      shouldIgnoreWhenAgentsConnect(controller)
      shouldShutdownOnAgentDisconnected(controller)
      shouldStopOnExceptionFromWorker(TestStepType.Initializing)

      it("should send StartTestRun to all connected agents when all workers are initialized and report step info") {
        val message1 = TestStepCompleted(TestStepType.Initializing, createTestStepStatistics)
        controller.tell(message1, TestProbe().ref)
        reporter.expectMsg(message1)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message2 = TestStepCompleted(TestStepType.Initializing, createTestStepStatistics)
        controller.tell(message2, TestProbe().ref)
        reporter.expectMsg(message2)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message3 = TestStepCompleted(TestStepType.Initializing, createTestStepStatistics)
        controller.tell(message3, TestProbe().ref)
        reporter.expectMsg(message3)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message4 = TestStepCompleted(TestStepType.Initializing, createTestStepStatistics)
        controller.tell(message4, TestProbe().ref)
        reporter.expectMsg(message4)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message5 = TestStepCompleted(TestStepType.Initializing, createTestStepStatistics)
        controller.tell(message5, TestProbe().ref)
        reporter.expectMsg(message5)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message6 = TestStepCompleted(TestStepType.Initializing, createTestStepStatistics)
        controller.tell(message6, TestProbe().ref)
        reporter.expectMsg(message6)

        agent1.expectMsg(StartTestRun)
        agent2.expectMsg(StartTestRun)
      }

      it("should go to WaitingForTestCompleted") {
        assert(controller.stateName === ControllerActor.WaitingForTestCompleted)
        assert(controller.stateData.numOfWorkersInitialized === expectedWorkers)
      }
    }

    describe("when WaitingForTestCompleted") {
      shouldIgnoreWhenAgentsConnect(controller)
      shouldShutdownOnAgentDisconnected(controller)
      shouldStopOnExceptionFromWorker(TestStepType.Running)

      it("should send SaveTestReport to reporter and Stop to agents when all workers completed test") {
        val message1 = TestStepCompleted(TestStepType.Running, createTestStepStatistics)
        controller.tell(message1, TestProbe().ref)
        reporter.expectMsg(message1)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message2 = TestStepCompleted(TestStepType.Running, createTestStepStatistics)
        controller.tell(message2, TestProbe().ref)
        reporter.expectMsg(message2)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message3 = TestStepCompleted(TestStepType.Running, createTestStepStatistics)
        controller.tell(message3, TestProbe().ref)
        reporter.expectMsg(message3)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message4 = TestStepCompleted(TestStepType.Running, createTestStepStatistics)
        controller.tell(message4, TestProbe().ref)
        reporter.expectMsg(message4)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message5 = TestStepCompleted(TestStepType.Running, createTestStepStatistics)
        controller.tell(message5, TestProbe().ref)
        reporter.expectMsg(message5)
        agent1.expectNoMsg(100 millis)
        agent2.expectNoMsg(100 millis)

        val message6 = TestStepCompleted(TestStepType.Running, createTestStepStatistics)
        controller.tell(message6, TestProbe().ref)

        agent1.expectMsg(Stop)
        agent2.expectMsg(Stop)

        reporter.expectMsg(message6)
        reporter.expectMsgClass(classOf[SaveTestReport])
      }

      it("should send TestCompleted to invoker") {
        starter.expectMsg(TestCompleted)
      }

      it("should stop when all workers completed test") {
        assert(controller.isTerminated === true)
      }
    }
  }
}