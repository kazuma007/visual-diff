package com.visualdiff.cli

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
