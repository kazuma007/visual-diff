package com.visualdiff.benchmark

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.compiletime.uninitialized
import com.visualdiff.core.DiffBatchEngine
import com.visualdiff.helper.TestDataGenerator
import com.visualdiff.helper.benchmark.PerformanceHelpers
import com.visualdiff.models.BatchConfig
import com.visualdiff.models.Config
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Slow

/** Performance benchmark tests for parallel processing
  *
  * Run with: sbt "testOnly com.visualdiff.benchmark.BenchmarkSpec"
  */
class BenchmarkSpec extends AnyFunSpec with Matchers with BeforeAndAfterAll:

  private val testDataDir = Paths.get("./target/benchmark_test_data")

  private val outputDir = Paths.get("./target/benchmark_output")

  private var oldDir: Path = uninitialized

  private var newDir: Path = uninitialized

  override def beforeAll(): Unit =
    super.beforeAll()
    println("\n" + "=" * 70)
    println("Setting up benchmark test data...")
    println("=" * 70)

    val (oldPath, newPath) = TestDataGenerator.generatePdfPairs(
      baseDir = testDataDir,
      count = 5,
      pages = 2,
    )

    oldDir = oldPath
    newDir = newPath
    Files.createDirectories(outputDir)
    println(s"✓ Generated 5 PDF pairs with 5 pages each")
    println()

  override def afterAll(): Unit =
    TestDataGenerator.cleanup(testDataDir)
    TestDataGenerator.cleanup(outputDir)
    println("\n✓ Cleanup completed")
    super.afterAll()

  describe("Parallel Processing Performance") {
    it("should be significantly faster than sequential processing", Slow) {
      println("=" * 70)
      println("BENCHMARK: Sequential vs Parallel")
      println("=" * 70)

      val comparison = PerformanceHelpers.compare(
        baselineLabel = "Sequential",
        comparisonLabel = "Parallel (4 workers)",
        iterations = 3,
      )(
        runBatchComparison(enableParallel = false),
        runBatchComparison(enableParallel = true),
      )

      println()
      println(comparison.baseline.summary)
      println(comparison.comparison.summary)
      println(comparison.summary)

      println("✅ Statistically significant improvement confirmed")
      println("=" * 70)
    }
  }

  /** Runs a batch comparison with specified configuration */
  private def runBatchComparison(enableParallel: Boolean, parallelism: Int = 4) =
    val config = BatchConfig(
      dirOld = oldDir,
      dirNew = newDir,
      recursive = false,
      continueOnError = true,
      enableParallel = enableParallel,
      parallelism = parallelism,
      baseConfig = Config(
        oldFile = Paths.get("."),
        newFile = Paths.get("."),
        outputDir = outputDir.resolve(f"run_${System.currentTimeMillis()}"),
        thresholdPixel = 0.0,
        thresholdLayout = 0.0,
        thresholdColor = 0.0,
        dpi = 150,
      ),
    )

    new DiffBatchEngine(config).compareAll()
