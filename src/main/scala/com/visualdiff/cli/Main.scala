package com.visualdiff.cli

import java.nio.file.Files
import java.nio.file.Paths

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.core.DiffEngine
import com.visualdiff.report.Reporter
import mainargs.Flag
import mainargs.ParserForMethods
import mainargs.arg
import mainargs.main

object Main extends LazyLogging:

  @main
  def visualDiff(
      @arg(doc = "Path to the old PDF")
      oldPdf: String,
      @arg(doc = "Path to the new PDF")
      newPdf: String,
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
      oldPdf = Paths.get(oldPdf), newPdf = Paths.get(newPdf), outputDir = Paths.get(out),
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
    require(Files.exists(config.oldPdf), s"Old PDF not found: ${config.oldPdf}")
    require(Files.exists(config.newPdf), s"New PDF not found: ${config.newPdf}")

    Files.createDirectories(config.outputDir)

    val engine = new DiffEngine(config)
    val result = engine.compare()

    val reporter = new Reporter(config)
    reporter.generateReports(result)

    result.hasDifferences
  }
