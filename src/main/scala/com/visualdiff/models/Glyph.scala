package com.visualdiff.models

/** Glyph-level representation captured from PDFBox TextPosition. */
final case class Glyph(
    text: String,
    bbox: BoundingBox,
    fontName: String,
    fontSizePt: Double,
):

  def x: Double = bbox.x

  def y: Double = bbox.y

  def right: Double = bbox.x + bbox.width

  def height: Double = bbox.height

object Glyph:

  /** PDFBox Y increases bottom-to-top.
    * A typical reading order is: Y descending, then X ascending.
    */
  given Ordering[Glyph] =
    Ordering.by[Glyph, (Double, Double)](g => (-g.y, g.x))
