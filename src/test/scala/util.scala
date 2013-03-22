import ru.smply.tester.messages._
import ru.smply.tester.metrics._
import org.joda.time.DateTime
import util.Random

object Util {
  def createTestStepStatistics = {
    TestStepStats(Random.nextInt, DateTime.now, Random.nextLong, List[Metric]())
  }

  def createTestStepStatistics(metrics: List[Metric]) = {
    TestStepStats(Random.nextInt, DateTime.now, Random.nextLong, metrics)
  }
}