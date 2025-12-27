package com.visualdiff.models

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import upickle.default.ReadWriter
import upickle.default.macroRW

final case class BoundingBox(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
)

object BoundingBox:

  given ReadWriter[BoundingBox] = macroRW

final case class VisualDiff(
    pixelDifferenceRatio: Double,
    differenceCount: Int,
)

object VisualDiff:

  given ReadWriter[VisualDiff] = macroRW

final case class ColorDiff(
    x: Int,
    y: Int,
    oldRgb: RgbColor,
    newRgb: RgbColor,
    distance: Double,
)

object ColorDiff:

  given ReadWriter[ColorDiff] = macroRW

final case class RgbColor(
    r: Int,
    g: Int,
    b: Int,
)

object RgbColor:

  given ReadWriter[RgbColor] = macroRW

final case class TextDiff(
    diffType: DiffType,
    oldText: Option[String],
    newText: Option[String],
    bbox: BoundingBox,
)

object TextDiff:

  given ReadWriter[TextDiff] = macroRW

final case class LayoutDiff(
    text: String,
    oldBbox: BoundingBox,
    newBbox: BoundingBox,
    displacement: Double,
)

object LayoutDiff:

  given ReadWriter[LayoutDiff] = macroRW

final case class FontInfo(
    fontName: String,
    isEmbedded: Boolean,
    isOutlined: Boolean,
)

object FontInfo:

  given ReadWriter[FontInfo] = macroRW

final case class FontDiff(
    diffType: DiffType,
    oldFont: Option[FontInfo],
    newFont: Option[FontInfo],
    affectedText: Option[String],
)

object FontDiff:

  given ReadWriter[FontDiff] = macroRW

final case class SuppressedDiffs(
    suppressedVisualDiff: Option[VisualDiff] = None,
    suppressedColorDiffCount: Int = 0,
    suppressedLayoutDiffCount: Int = 0,
    reason: String = "",
)

object SuppressedDiffs:

  given ReadWriter[SuppressedDiffs] = macroRW

final case class PageDiff(
    pageNumber: Int,
    visualDiff: Option[VisualDiff] = None,
    colorDiffs: Seq[ColorDiff] = Seq.empty,
    textDiffs: Seq[TextDiff] = Seq.empty,
    layoutDiffs: Seq[LayoutDiff] = Seq.empty,
    fontDiffs: Seq[FontDiff] = Seq.empty,
    oldImagePath: Option[String] = None,
    newImagePath: Option[String] = None,
    diffImagePath: Option[String] = None,
    colorImagePath: Option[String] = None,
    suppressedDiffs: Option[SuppressedDiffs] = None,
    existsInOld: Boolean = true,
    existsInNew: Boolean = true,
    hasDifferences: Boolean = true,
)

object PageDiff:

  given ReadWriter[PageDiff] = macroRW

  def removed(pageNumber: Int): PageDiff =
    PageDiff(
      pageNumber = pageNumber,
      visualDiff = Some(VisualDiff(1.0, Int.MaxValue)),
      existsInNew = false,
    )

  def added(pageNumber: Int): PageDiff =
    PageDiff(
      pageNumber = pageNumber,
      visualDiff = Some(VisualDiff(1.0, Int.MaxValue)),
      existsInOld = false,
    )

final case class DiffSummary(
    totalPages: Int,
    pagesWithDiff: Int,
    visualDiffCount: Int,
    colorDiffCount: Int,
    textDiffCount: Int,
    layoutDiffCount: Int,
    fontDiffCount: Int,
    hasDifferences: Boolean,
    isImageComparison: Boolean = false,
)

object DiffSummary:

  given ReadWriter[DiffSummary] = macroRW

final case class DiffResult(
    pageDiffs: Seq[PageDiff],
    summary: DiffSummary,
):

  /** Checks if there are any differences in the entire result.
    * Uses the hasDifferences field from each PageDiff.
    */
  def hasDifferences: Boolean = summary.hasDifferences

object DiffResult:

  given ReadWriter[DiffResult] = macroRW

final case class TextElement(
    text: String,
    bbox: BoundingBox,
    pageNumber: Int,
    fontName: String,
)

/** File pair to compare */
final case class BatchPair(
    oldFile: Path,
    newFile: Path,
    relativePath: String, // For organizing output and display
)

/** Result of a single pair comparison */
final case class PairResult(
    pair: BatchPair,
    result: Option[DiffResult], // None if failed
    error: Option[String], // Error message if failed
    duration: Duration,
    outputDir: Path, // Where this pair's report was saved
):

  def isSuccess: Boolean = result.isDefined

  def hasDifferences: Boolean = result.exists(_.hasDifferences)

/** Batch comparison result including file discovery information */
final case class BatchResult(
    pairs: Seq[PairResult],
    summary: BatchSummary,
    startTime: Instant,
    endTime: Instant,
    unmatchedOld: Seq[Path] = Seq.empty,
    unmatchedNew: Seq[Path] = Seq.empty,
):

  def hasAnyDifferences: Boolean = pairs.exists(_.hasDifferences)

/** Batch summary statistics */
final case class BatchSummary(
    totalPairs: Int,
    successful: Int,
    successfulWithDiff: Int,
    failed: Int,
    totalPages: Int,
    totalDifferences: Int,
    totalDuration: Duration,
    unmatchedOldCount: Int = 0,
    unmatchedNewCount: Int = 0,
)
