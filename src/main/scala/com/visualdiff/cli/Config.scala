package com.visualdiff.cli

import java.nio.file.Path
import java.nio.file.Paths

final case class Config(
    oldPdf: Path,
    newPdf: Path,
    outputDir: Path = Paths.get(Config.DefaultOutputDir),
    thresholdPixel: Double = Config.DefaultThresholdPixel,
    thresholdLayout: Double = Config.DefaultThresholdLayout,
    thresholdColor: Double = Config.DefaultThresholdColor,
    ignoreAnnotation: Boolean = false,
    failOnDiff: Boolean = false,
    dpi: Int = Config.DefaultDpi,
)

object Config:

  val DefaultOutputDir = "./report"

  val DefaultThresholdPixel = 0.0

  val DefaultThresholdLayout = 0.0

  val DefaultThresholdColor = 0.0 // RGB Euclidean distance threshold (0-441)

  val DefaultDpi = 150
