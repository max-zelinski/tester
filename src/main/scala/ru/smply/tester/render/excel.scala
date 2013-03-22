package ru.smply.tester.render

import ru.smply.tester.report.Report
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import collection.mutable.HashMap

object Workbook {
  def apply(fun: => Iterable[SheetContainer]): WorkbookContainer = {
    WorkbookContainer(fun)
  }
}
case class WorkbookContainer(sheets: Iterable[SheetContainer]) {
  def save(outputFilename: String) {
    val xworkbook = new XSSFWorkbook()
    for (sheet <- sheets) {
      val xsheet = xworkbook.createSheet(sheet.name)
      var rowNumber = 0
      for (table <- sheet.tables) {
        if (table.name != null) {
          val xheader = xsheet.createRow(rowNumber)
          xheader.createCell(0).setCellValue(table.name)
          rowNumber += 1
        }
        for (row <- table.rows) {
          val xrow = xsheet.createRow(rowNumber)
          var cellNumber = 0
          for (cell <- row.cells) {
            if (cell.value != null) {
              xrow.createCell(cellNumber).setCellValue(cell.value.toString)
            }
            cellNumber += 1
          }
          rowNumber += 1
        }
        rowNumber += 1
      }
    }

    val outputStream = new FileOutputStream(outputFilename)
    xworkbook.write(outputStream)
    outputStream.close()
  }
}

object Sheet {
  def apply(name: String)(fun: => Iterable[TableContainer]): SheetContainer = {
    SheetContainer(name, fun)
  }
}
case class SheetContainer(name: String, tables: Iterable[TableContainer])

object Table {
  def apply(name: String = null)(fun: => Iterable[RowContainer]): TableContainer = {
    TableContainer(name, fun)
  }
}
case class TableContainer(name: String, rows: Iterable[RowContainer])

object Row {
  def apply(fun: => Iterable[Cell]): RowContainer = {
    RowContainer(fun)
  }
}
case class RowContainer(cells: Iterable[Cell])

case class Cell(value: Any)

class MeasurementStatistics {
  private val statistics: SummaryStatistics = new SummaryStatistics()

  def accumulate(value: Double) {
    statistics.addValue(value)
  }

  def geometricMean = statistics.getGeometricMean
  def mean = statistics.getMean
  def max = statistics.getMax
  def min = statistics.getMin
  def standardDeviation = statistics.getStandardDeviation
  def count = statistics.getN
}

object ExcelRender {
  def render(report: Report, outputFilename: String) {
    require(report != null)
    require(outputFilename != null)

    val statistics: HashMap[String, MeasurementStatistics] = HashMap()

    for (
      step <- report.steps;
      metric <- step.stats.metrics
    ) {
      statistics.getOrElseUpdate(metric.name, new MeasurementStatistics()).accumulate(metric.took)

      for ((name, value) <- metric.additionalMeasurements) {
        statistics.getOrElseUpdate(name, new MeasurementStatistics()).accumulate(value)
      }
    }

    Workbook {
      Sheet("Test info") {
        Table("Test time") {
          Row {
            Cell("start time") :: Cell(report.startTime.toString()) :: Nil
          } ::
          Row {
            Cell("total time, ms") :: Cell(report.took) :: Nil
          } :: Nil
        } ::
        Table("Test plan") {
          Row {
            Cell("timeout") :: Cell(report.testPlan.configuration.timeout) :: Nil
          } ::
          Row {
            Cell("expected agents") :: Cell(report.testPlan.configuration.expectedAgents) :: Nil
          } ::
          Row {
            Cell("workers per agent") :: Cell(report.testPlan.configuration.workersPerAgent) :: Nil
          } ::
          Row {
            Cell("script path") :: Cell(report.testPlan.configuration.scriptPath) :: Nil
          } ::
          Row {
            Cell("script method") :: Cell(report.testPlan.configuration.scriptMethod) :: Nil
          } ::
          Row {
            Cell("result path") :: Cell(report.testPlan.configuration.resultPath) :: Nil
          } :: Nil
        } ::
        Table("Parameters") {
          for ((key, value) <- report.testPlan.parameters) yield {
            Row {
              Cell(key) :: Cell(value) :: Nil
            }
          }
        } :: Nil
      } ::
      Sheet("Metrics") {
        Table() {
          val header = Row {
            var header = Cell("worker id") :: Cell("step") :: Cell("timestamp") :: Cell("name") ::
              Cell("successful") :: Cell("took, ms") :: Nil
            header = header ::: report.additionalParameters.map(c => Cell(c)) :::
              report.additionalMeasurements.map(c => Cell(c))
            header
          }
          val rows = for (
            step <- report.steps;
            metric <- step.stats.metrics
          ) yield {
            Row {
              var cells = Cell(step.stats.workerId) :: Cell(step.testStepType.toString) ::
                Cell(metric.timestamp) :: Cell(metric.name) :: Cell(metric.successful) ::
                Cell(metric.took) :: Nil

              cells = cells :::
                report.additionalParameters.map(p => Cell(metric.additionalParameters.getOrElse(p, ""))) :::
                report.additionalMeasurements.map(p => Cell(metric.additionalMeasurements.getOrElse(p, "")))
              cells
            }
          }
          header :: rows.toList
        } :: Nil
      } ::
      Sheet("Statistics") {
        Table() {
          val header = Row {
            Cell("name") :: Cell("geometric mean") :: Cell("mean") :: Cell("max") :: Cell("min") ::
              Cell("std deviation") :: Cell("count") :: Nil
          }
          val rows = for((name, statistic) <- statistics) yield {
            Row {
              Cell(name) :: Cell(statistic.geometricMean) :: Cell(statistic.mean) :: Cell(statistic.max) ::
                Cell(statistic.min) :: Cell(statistic.standardDeviation) :: Cell(statistic.count) :: Nil
            }
          }
          header :: rows.toList
        } :: Nil
      } ::
      Sheet("Steps") {
        Table() {
          val header = Row {
            Cell("worker id") :: Cell("step") :: Cell("started") :: Cell("took") :: Cell("successful") ::
              Cell("exception") :: Nil
          }
          val rows = for(step <- report.steps) yield {
            Row {
              Cell(step.stats.workerId) :: Cell(step.testStepType) :: Cell(step.stats.started) ::
                Cell(step.stats.took) :: Nil
            }
          }
          header :: rows.toList
        } :: Nil
      } :: Nil
    }.save(outputFilename)
  }
}