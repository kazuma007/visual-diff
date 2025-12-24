package com.visualdiff.cli

import java.nio.file.Files
import java.nio.file.Paths

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.core.DiffBatchEngine
import com.visualdiff.core.DiffEngine
import com.visualdiff.models.BatchConfig
import com.visualdiff.models.BatchResult
import com.visualdiff.models.Config
import com.visualdiff.models.DiffResult
import com.visualdiff.models.ImageFormat
import com.visualdiff.report.Reporter
import mainargs.Flag
import mainargs.ParserForMethods
import mainargs.arg
import mainargs.main

object Main extends LazyLogging:

  @main
  def visualDiff(
      @arg(doc = s"Path to the old file (PDF, ${ImageFormat.displayNames})")
      oldFile: Option[String] = None,
      @arg(doc = s"Path to the new file (PDF, ${ImageFormat.displayNames})")
      newFile: Option[String] = None,
      @arg(doc = "Directory containing baseline/old files")
      batchDirOld: Option[String] = None,
      @arg(doc = "Directory containing new/comparison files")
      batchDirNew: Option[String] = None,
      @arg(short = 'o', doc = s"Output directory (default: ${Config.DefaultOutputDir})")
      out: String = Config.DefaultOutputDir,
      @arg(doc = s"Pixel diff threshold 0.0 - 1.0 (default: ${Config.DefaultThresholdPixel})")
      thresholdPixel: Double = Config.DefaultThresholdPixel,
      @arg(doc = s"Layout shift threshold in pixels (default: ${Config.DefaultThresholdLayout})")
      thresholdLayout: Double = Config.DefaultThresholdLayout,
      @arg(doc = s"Color difference threshold 0-441 RGB distance (default: ${Config.DefaultThresholdColor})")
      thresholdColor: Double = Config.DefaultThresholdColor,
      @arg(doc = "Ignore annotations (future use)")
      ignoreAnnotation: Flag = Flag(),
      @arg(doc = "Exit with code 1 if any diff is detected")
      failOnDiff: Flag = Flag(),
      @arg(doc = s"Rendering DPI (default: ${Config.DefaultDpi})")
      dpi: Int = Config.DefaultDpi,
      @arg(doc = "Recursively scan subdirectories (batch mode)")
      recursive: Flag = Flag(),
      @arg(doc = "Continue on error (don't stop batch on single file failure)")
      continueOnError: Flag = Flag(true),
  ): Unit =

    // Determine mode: batch or single
    val isBatchMode = batchDirOld.isDefined && batchDirNew.isDefined
    val isSingleMode = oldFile.isDefined && newFile.isDefined

    require(
      isBatchMode || isSingleMode,
      "Either provide --old-file and --new-file, or --batch-dir-old and --batch-dir-new",
    )
    require(
      !(isBatchMode && isSingleMode),
      "Cannot use both single-file and batch mode simultaneously",
    )

    if isBatchMode then runBatchMode(batchDirOld, batchDirNew, recursive, continueOnError)
    else runSingleMode(oldFile, newFile)

    def runSingleMode(oldFile: Option[String], newFile: Option[String]): Unit =
      val config = Config(
        oldFile = Paths.get(oldFile.get), newFile = Paths.get(newFile.get), outputDir = Paths.get(out),
        thresholdPixel = thresholdPixel, thresholdLayout = thresholdLayout, thresholdColor = thresholdColor,
        ignoreAnnotation = ignoreAnnotation.value, failOnDiff = failOnDiff.value, dpi = dpi,
      )

      run(config) match
        case Success(result) =>
          if config.failOnDiff && result.hasDifferences then
            logger.error("Differences detected. Exiting with code 1.")
            System.exit(1)
          else
            logger.info("Done.")
            System.exit(0)
        case Failure(ex) =>
          logger.error(s"Error: ${ex.getMessage}", ex)
          System.exit(1)

    def runBatchMode(
        batchDirOld: Option[String],
        batchDirNew: Option[String],
        recursive: Flag,
        continueOnError: Flag,
    ): Unit =
      val batchConfig = BatchConfig(
        dirOld = Paths.get(batchDirOld.get),
        dirNew = Paths.get(batchDirNew.get),
        recursive = recursive.value,
        continueOnError = continueOnError.value,
        baseConfig = Config(
          oldFile = Paths.get("."), // Dummy, not used in batch
          newFile = Paths.get("."), // Dummy, not used in batch
          outputDir = Paths.get(out), thresholdPixel = thresholdPixel, thresholdLayout = thresholdLayout,
          thresholdColor = thresholdColor, ignoreAnnotation = ignoreAnnotation.value, failOnDiff = failOnDiff.value,
          dpi = dpi,
        ),
      )

      runBatch(batchConfig) match
        case Success(result) =>
          val hasFailures = result.summary.failed > 0
          val hasDifferences = result.hasAnyDifferences

          if batchConfig.baseConfig.failOnDiff && hasDifferences then
            logger.error(
              s"Batch completed: ${result.summary.successfulWithDiff} file(s) have differences",
            )
            System.exit(1)
          else if hasFailures && !batchConfig.continueOnError then
            logger.error(s"Batch failed: ${result.summary.failed} comparison(s) failed")
            System.exit(1)
          else
            logger.info(s"Batch completed: ${result.summary.totalPairs} pairs processed")
            System.exit(0)
        case Failure(ex) =>
          logger.error(s"Batch error: ${ex.getMessage}", ex)
          System.exit(1)

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args)

  /** Runs single-file comparison and returns the result */
  def run(config: Config): Try[DiffResult] = Try {
    require(Files.exists(config.oldFile), s"Old file not found: ${config.oldFile}")
    require(Files.exists(config.newFile), s"New file not found: ${config.newFile}")
    Files.createDirectories(config.outputDir)

    val engine = new DiffEngine(config)
    val result = engine.compare()

    val reporter = new Reporter(config)
    reporter.generateReports(result)

    result
  }

  /** Runs batch comparison and returns the result */
  def runBatch(config: BatchConfig): Try[BatchResult] = Try {
    Files.createDirectories(config.baseConfig.outputDir)

    val engine = new DiffBatchEngine(config)
    val result = engine.compareAll()

    val reporter = new Reporter(config.baseConfig)
    reporter.generateBatchReport(result, config.baseConfig.outputDir, config.dirOld, config.dirNew)

    result
  }
