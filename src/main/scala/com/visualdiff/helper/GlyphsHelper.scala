package com.visualdiff.helper

import scala.collection.mutable.ListBuffer
import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.models.BoundingBox
import com.visualdiff.models.Glyph
import com.visualdiff.models.TextElement
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/** Helper for extracting and aggregating PDFBox glyphs (TextPosition) into word-level TextElements.
  *
  * Why:
  * - PDFBox TextPosition is usually glyph-level (often 1 character), which makes diffs noisy.
  * - Aggregating into word tokens reduces Added/Removed storms and keeps JSON smaller.
  *
  * Strategy:
  * 1) Collect glyphs with bbox + font metadata
  * 2) Cluster glyphs into lines by Y proximity
  * 3) Split each line into word tokens by whitespace / X-gap / font change
  */
object GlyphsHelper extends LazyLogging:

  final private case class WordToken(text: String, bbox: BoundingBox, fontName: String)

  /** Extract word-level TextElements from a single page.
    *
    * @param doc PDF document
    * @param pageIndex0 0-based page index
    * @return word-level TextElements in reading order
    */
  def extractWordElements(doc: PDDocument, pageIndex0: Int): Seq[TextElement] =
    Try {
      val pageNumber1 = pageIndex0 + 1
      val glyphs = collectGlyphs(doc, pageNumber1)
      glyphsToWordElements(glyphs, pageNumber1)
    }.recover { case e: Exception =>
      logger.error(s"Failed to extract glyphs/tokens from page ${pageIndex0 + 1}", e)
      Seq.empty[TextElement]
    }.getOrElse(Seq.empty)

  /** Normalize text for matching (trim + collapse whitespace). */
  def normalizeText(s: String): String =
    s.trim.replaceAll("\\s+", " ")

  /** Euclidean distance between bbox centers. */
  def bboxCenterDistance(a: BoundingBox, b: BoundingBox): Double =
    val ax = a.x + a.width / 2.0
    val ay = a.y + a.height / 2.0
    val bx = b.x + b.width / 2.0
    val by = b.y + b.height / 2.0
    val dx = bx - ax
    val dy = by - ay
    math.sqrt(dx * dx + dy * dy)

  // -----------------------------
  // Internals
  // -----------------------------

  private def collectGlyphs(doc: PDDocument, pageNumber1: Int): Seq[Glyph] =
    val buf = ListBuffer.empty[Glyph]

    val stripper = new PDFTextStripper():
      override def processTextPosition(tp: TextPosition): Unit =
        val unicode = Option(tp.getUnicode).getOrElse("")
        if unicode.nonEmpty then
          val bbox = BoundingBox(
            tp.getXDirAdj.toDouble,
            tp.getYDirAdj.toDouble,
            tp.getWidthDirAdj.toDouble,
            tp.getHeightDir.toDouble,
          )

          val fontName = Option(tp.getFont).flatMap(f => Option(f.getName)).getOrElse("Unknown")
          val fontSizePt = tp.getFontSizeInPt.toDouble

          buf += Glyph(
            text = unicode,
            bbox = bbox,
            fontName = fontName,
            fontSizePt = fontSizePt,
          )

    stripper.setStartPage(pageNumber1)
    stripper.setEndPage(pageNumber1)
    // Trigger extraction; results are collected via processTextPosition override.
    stripper.getText(doc)

    buf.toSeq

  private def glyphsToWordElements(glyphs: Seq[Glyph], pageNumber1: Int): Seq[TextElement] =
    if glyphs.isEmpty then Seq.empty
    else
      val lines = clusterIntoLines(glyphs.sorted)
      lines.flatMap { lineGlyphs =>
        splitLineIntoWords(lineGlyphs).map { w =>
          TextElement(
            text = w.text,
            bbox = w.bbox,
            pageNumber = pageNumber1,
            fontName = w.fontName,
          )
        }
      }

  private def clusterIntoLines(sortedGlyphs: Seq[Glyph]): Seq[Vector[Glyph]] =
    // Use a tolerance proportional to glyph height/font size (avoid fixed pixel thresholds).
    def lineTolerance(g: Glyph): Double =
      val base = math.max(g.height, g.fontSizePt)
      math.max(1.0, base * 0.6)

    val out = Vector.newBuilder[Vector[Glyph]]
    var current = Vector.empty[Glyph]
    var currentY: Double = Double.NaN
    var currentTol: Double = 0.0

    def flush(): Unit =
      if current.nonEmpty then out += current
      current = Vector.empty

    sortedGlyphs.foreach { g =>
      if current.isEmpty then
        current = Vector(g)
        currentY = g.y
        currentTol = lineTolerance(g)
      else
        val sameLine = math.abs(g.y - currentY) <= currentTol
        if sameLine then current = current :+ g
        else
          flush()
          current = Vector(g)
          currentY = g.y
          currentTol = lineTolerance(g)
    }

    flush()
    out.result()

  private def splitLineIntoWords(lineGlyphs: Vector[Glyph]): Vector[WordToken] =
    val glyphs = lineGlyphs.sortBy(_.x)

    def isWhitespace(g: Glyph): Boolean =
      g.text.forall(_.isWhitespace)

    def wordGapTolerance(g: Glyph): Double =
      // X-gap threshold proportional to font size. Tune if needed.
      val base = math.max(1.0, g.fontSizePt)
      base * 0.35

    val out = Vector.newBuilder[WordToken]
    val sb = new StringBuilder

    var minX = 0.0
    var minY = 0.0
    var maxX = 0.0
    var maxY = 0.0
    var prevRight = Double.NaN
    var currentFont = "Unknown"
    var active = false

    def startWith(g: Glyph): Unit =
      sb.clear()
      sb.append(g.text)
      minX = g.bbox.x
      minY = g.bbox.y
      maxX = g.bbox.x + g.bbox.width
      maxY = g.bbox.y + g.bbox.height
      prevRight = g.right
      currentFont = g.fontName
      active = true

    def extendWith(g: Glyph): Unit =
      sb.append(g.text)
      minX = math.min(minX, g.bbox.x)
      minY = math.min(minY, g.bbox.y)
      maxX = math.max(maxX, g.bbox.x + g.bbox.width)
      maxY = math.max(maxY, g.bbox.y + g.bbox.height)
      prevRight = g.right

    def emitIfActive(): Unit =
      if active then
        val text = sb.result().trim
        if text.nonEmpty then
          out += WordToken(
            text = text,
            bbox = BoundingBox(minX, minY, maxX - minX, maxY - minY),
            fontName = currentFont,
          )
      active = false

    glyphs.foreach { g =>
      if isWhitespace(g) then
        // Explicit whitespace acts as a hard token boundary.
        emitIfActive()
      else if !active then startWith(g)
      else
        val gap = g.x - prevRight
        val fontChanged = g.fontName != currentFont
        val gapBreak = gap.isNaN || gap > wordGapTolerance(g)

        if fontChanged || gapBreak then
          emitIfActive()
          startWith(g)
        else extendWith(g)
    }

    emitIfActive()
    out.result()
