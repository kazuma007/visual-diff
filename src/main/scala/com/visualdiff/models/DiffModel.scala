package com.visualdiff.models

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
    visualDiff: Option[VisualDiff],
    colorDiffs: Seq[ColorDiff],
    textDiffs: Seq[TextDiff],
    layoutDiffs: Seq[LayoutDiff],
    fontDiffs: Seq[FontDiff],
    oldImagePath: Option[String], // NEW: separate old image
    newImagePath: Option[String], // NEW: separate new image
    diffImagePath: Option[String], // The diff highlight image
    colorImagePath: Option[String],
    suppressedDiffs: Option[SuppressedDiffs] = None,
    existsInOld: Boolean = true,
    existsInNew: Boolean = true,
)

object PageDiff:

  given ReadWriter[PageDiff] = macroRW

final case class DiffSummary(
    totalPages: Int,
    pagesWithDiff: Int,
    visualDiffCount: Int,
    colorDiffCount: Int,
    textDiffCount: Int,
    layoutDiffCount: Int,
    fontDiffCount: Int,
)

object DiffSummary:

  given ReadWriter[DiffSummary] = macroRW

final case class DiffResult(
    pageDiffs: Seq[PageDiff],
    summary: DiffSummary,
):

  def hasDifferences: Boolean =
    pageDiffs.exists { p =>
      p.visualDiff.exists(_.differenceCount > 0) ||
      p.colorDiffs.nonEmpty ||
      p.textDiffs.nonEmpty ||
      p.layoutDiffs.nonEmpty ||
      p.fontDiffs.nonEmpty
    }

object DiffResult:

  given ReadWriter[DiffResult] = macroRW

final case class TextElement(
    text: String,
    bbox: BoundingBox,
    pageNumber: Int,
    fontName: String,
)
