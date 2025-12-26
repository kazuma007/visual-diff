package com.visualdiff.models

import java.nio.file.Path

/** Error types for DiffEngine operations using Scala 3 union types */
enum DiffEngineError(val message: String, val cause: Option[Throwable] = None):

  case FileNotFound(path: Path) extends DiffEngineError(s"File not found: $path")

  case DocumentLoadError(path: Path, throwable: Throwable)
      extends DiffEngineError(s"Failed to load document: $path", Some(throwable))

  case ImageConversionError(path: String, throwable: Throwable)
      extends DiffEngineError(s"Failed to convert image to PDF: $path", Some(throwable))

  case ImageReadError(path: String, throwable: Throwable)
      extends DiffEngineError(s"Failed to read image: $path", Some(throwable))

  case ComparisonError(throwable: Throwable)
      extends DiffEngineError(s"Comparison failed: ${throwable.getMessage}", Some(throwable))

  case FontExtractionError(fontName: String, pageNum: Int, throwable: Throwable)
      extends DiffEngineError(s"Failed to extract font '$fontName' on page $pageNum", Some(throwable))
