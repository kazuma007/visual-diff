package com.visualdiff.models

/** Supported image formats that can be converted to PDF for comparison. */
enum ImageFormat(val extensions: Seq[String], val displayName: String):

  case JPEG extends ImageFormat(Seq(".jpg", ".jpeg"), "JPG/JPEG")

  case PNG extends ImageFormat(Seq(".png"), "PNG")

  case GIF extends ImageFormat(Seq(".gif"), "GIF")

  case BMP extends ImageFormat(Seq(".bmp"), "BMP")

  case TIFF extends ImageFormat(Seq(".tif", ".tiff"), "TIF/TIFF")

object ImageFormat:

  /** All supported image formats */
  val all: Seq[ImageFormat] = ImageFormat.values.toSeq

  /** Get all file extensions across all formats */
  private def allExtensions: Seq[String] = all.flatMap(_.extensions)

  /** Get display names as a formatted string (e.g., "JPG/JPEG, PNG, GIF, BMP, TIF/TIFF") */
  def displayNames: String = all.map(_.displayName).mkString(", ")

  /** Check if a file extension is supported */
  def isSupported(extension: String): Boolean =
    val normalized = extension.toLowerCase
    allExtensions.exists(normalized.endsWith)

  /** Detect format from file path */
  def fromPath(path: java.nio.file.Path): Option[ImageFormat] =
    val pathStr = path.toString.toLowerCase
    all.find(format => format.extensions.exists(pathStr.endsWith))
