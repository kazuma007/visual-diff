package com.visualdiff.helper.benchmark

import java.time.Duration
import java.time.Instant

/** Helper utilities for performance benchmarking */
object PerformanceHelpers:

  /** Single benchmark measurement */
  case class Measurement(label: String, duration: Duration):

    def ms: Long = duration.toMillis

    def seconds: Double = ms / 1000.0

  /** Statistics from multiple benchmark runs */
  case class Stats(label: String, measurements: Seq[Measurement]):

    lazy val mean: Double = measurements.map(_.ms).sum.toDouble / measurements.size

    lazy val median: Double =
      val sorted = measurements.map(_.ms).sorted
      val mid = sorted.size / 2
      if sorted.size % 2 == 0 then (sorted(mid - 1) + sorted(mid)) / 2.0
      else sorted(mid).toDouble

    lazy val min: Long = measurements.map(_.ms).min

    lazy val max: Long = measurements.map(_.ms).max

    lazy val stdDev: Double =
      val variance = measurements.map(m => math.pow(m.ms - mean, 2)).sum / measurements.size
      math.sqrt(variance)

    def summary: String =
      f"""Statistics for $label:
        |  Mean:   $mean%.2fms
        |  Median: $median%.2fms
        |  Min:    ${min}ms
        |  Max:    ${max}ms
        |  StdDev: $stdDev%.2fms
        |  Runs:   ${measurements.size}
        |""".stripMargin

  /** Comparison between two benchmark results */
  case class Comparison(baseline: Stats, comparison: Stats):

    lazy val speedup: Double = baseline.mean / comparison.mean

    lazy val improvement: Double = ((baseline.mean - comparison.mean) / baseline.mean) * 100

    lazy val timeSaved: Double = baseline.mean - comparison.mean

    def summary: String =
      f"""Comparison: ${baseline.label} vs ${comparison.label}
        |  Baseline:    ${baseline.mean}%.2fms
        |  Comparison:  ${comparison.mean}%.2fms
        |  Speedup:     $speedup%.2fx
        |  Improvement: $improvement%.1f%%
        |  Time saved:  $timeSaved%.2fms
        |""".stripMargin

    def isSignificant(threshold: Double = 1.1): Boolean = speedup >= threshold

  /** Times a code block execution */
  private def time[T](label: String)(block: => T): (T, Measurement) =
    val start = Instant.now()
    val result = block
    val duration = Duration.between(start, Instant.now())
    (result, Measurement(label, duration))

  /** Runs a benchmark multiple times and collects statistics
    *
    * @param label Benchmark identifier
    * @param iterations Number of measurement runs
    * @param warmup Number of warmup runs (not measured)
    * @param block Code to benchmark
    */
  def benchmark[T](label: String, iterations: Int = 5, warmup: Int = 1)(block: => T): Stats =
    // Warmup runs
    (1 to warmup).foreach(_ => block)

    // Measured runs
    val measurements = (1 to iterations).map { i =>
      val (_, measurement) = time(s"$label-$i")(block)
      measurement
    }

    Stats(label, measurements)

  /** Compares two implementations with statistical analysis
    *
    * @param baselineLabel Name for the baseline implementation
    * @param comparisonLabel Name for the comparison implementation
    * @param iterations Number of runs for each
    * @param warmup Number of warmup runs
    * @param baselineBlock Baseline code to benchmark
    * @param comparisonBlock Comparison code to benchmark
    */
  def compare[T, U](
      baselineLabel: String,
      comparisonLabel: String,
      iterations: Int = 5,
      warmup: Int = 1,
  )(
      baselineBlock: => T,
      comparisonBlock: => U,
  ): Comparison =
    println(s"\nBenchmarking $baselineLabel...")
    val baselineStats = benchmark(baselineLabel, iterations, warmup)(baselineBlock)

    println(s"Benchmarking $comparisonLabel...")
    val comparisonStats = benchmark(comparisonLabel, iterations, warmup)(comparisonBlock)

    Comparison(baselineStats, comparisonStats)

  /** Formats a duration in human-readable form */
  def formatDuration(duration: Duration): String =
    val ms = duration.toMillis
    if ms < 1000 then f"${ms}ms"
    else if ms < 60000 then f"${ms / 1000.0}%.2fs"
    else
      val mins = ms / 60000
      val secs = (ms % 60000) / 1000
      f"${mins}m ${secs}s"
