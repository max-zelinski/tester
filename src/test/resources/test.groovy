import ru.smply.tester.scripting.*

class SampleTest extends Test {
  SampleTest() {
    measure("initialization") {
      def sleep = new Random().nextInt(100)
      log.info("init: sleeping for ${sleep}")
      Thread.sleep(sleep)
    }
  }

  void test() {
    log.info("using param: ${testPlan.parameters.param}")
    log.info("running some code in a loop")

    0.upto(20) {
      def result = measure("run") {
        def sleep = new Random().nextInt(100)
        log.info("run: sleeping for ${sleep}")
        Thread.sleep(sleep)
        "hello"
      }
      result.parameters["parameter1"] = "p-value1"
      result.measurements["measurement1"] = new Random().nextInt(100)
      log.info("result: ${result.result}")
    }
  }
}
