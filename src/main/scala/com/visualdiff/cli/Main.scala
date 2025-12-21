package com.visualdiff.cli

import java.nio.file.Files
import java.nio.file.Paths

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.core.DiffEngine
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
      oldFile: String,
      @arg(doc = s"Path to the new file (PDF, ${ImageFormat.displayNames})")
      newFile: String,
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
  ): Unit =
    val config = Config(
      oldFile = Paths.get(oldFile), newFile = Paths.get(newFile), outputDir = Paths.get(out),
      thresholdPixel = thresholdPixel, thresholdLayout = thresholdLayout, thresholdColor = thresholdColor,
      ignoreAnnotation = ignoreAnnotation.value, failOnDiff = failOnDiff.value, dpi = dpi,
    )

    run(config) match
      case Success(hasDiff) =>
        if config.failOnDiff && hasDiff then
          logger.error("Differences detected. Exiting with code 1.")
          System.exit(1)
        else
          logger.info("Done.")
          System.exit(0)
      case Failure(ex) =>
        logger.error(s"Error: ${ex.getMessage}", ex)
        System.exit(1)

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args)

  def run(config: Config): Try[Boolean] = Try {
    require(Files.exists(config.oldFile), s"Old file not found: ${config.oldFile}")
    require(Files.exists(config.newFile), s"New file not found: ${config.newFile}")
    Files.createDirectories(config.outputDir)

    val engine = new DiffEngine(config)
    val result = engine.compare()

    val reporter = new Reporter(config)
    reporter.generateReports(result)

    result.hasDifferences
  }
