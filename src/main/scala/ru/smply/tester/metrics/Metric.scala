package ru.smply.tester.metrics

import org.joda.time.DateTime

case class Metric(timestamp: DateTime, name: String, successful: Boolean, took: Long,
                            additionalParameters: Map[String, String],
                            additionalMeasurements: Map[String, Int])