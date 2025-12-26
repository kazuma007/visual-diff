package com.visualdiff.models

import java.nio.file.Path
import java.nio.file.Paths

import com.visualdiff.models.ImageFormat

final case class Config(
    oldFile: Path,
    newFile: Path,
    outputDir: Path = Paths.get(Config.DefaultOutputDir),
    thresholdPixel: Double = Config.DefaultThresholdPixel,
    thresholdLayout: Double = Config.DefaultThresholdLayout,
    thresholdColor: Double = Config.DefaultThresholdColor,
    ignoreAnnotation: Boolean = false,
    failOnDiff: Boolean = false,
    dpi: Int = Config.DefaultDpi,
):

  /** Checks if either input file is an image (non-PDF) format */
  def hasImageInput: Boolean =
    ImageFormat.isSupported(oldFile.toString) || ImageFormat.isSupported(newFile.toString)

object Config:

  val DefaultOutputDir = "./report"

  val DefaultThresholdPixel = 0.0

  val DefaultThresholdLayout = 0.0

  val DefaultThresholdColor = 0.0 // RGB Euclidean distance threshold (0-441)

  val DefaultDpi = 150

/** Configuration for batch comparison mode */
final case class BatchConfig(
    dirOld: Path,
    dirNew: Path,
    recursive: Boolean,
    continueOnError: Boolean,
    baseConfig: Config,
    parallelism: Int = BatchConfig.DefaultParallelism,
    enableParallel: Boolean = true,
)

object BatchConfig:

  /** Default parallelism uses available CPU cores minus 1 (to leave resources for system) */
  val DefaultParallelism: Int = Math.max(1, Runtime.getRuntime.availableProcessors() - 1)
