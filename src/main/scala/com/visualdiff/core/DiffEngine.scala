package com.visualdiff.core

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.Using

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.models._
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType3Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/** Core engine for comparing two PDF documents and detecting differences.
  *
  * Performs multi-dimensional analysis including:
  * - Visual (pixel-level) differences
  * - Color changes using RGB distance
  * - Text additions/removals
  * - Layout shifts (positional changes)
  * - Font substitutions and embedding changes
  */
final class DiffEngine(config: Config) extends LazyLogging:

  private final case class RenderImages(oldImage: BufferedImage, newImage: BufferedImage, minWidth: Int, minHeight: Int)

  /** Compares two PDF documents page-by-page and returns comprehensive diff results.
    * Returns Either with error details or successful DiffResult.
    */
  def compare(): Either[DiffEngineError, DiffResult] =
    Using.Manager { use =>
      for
        oldDoc <- loadDocument(config.oldFile).map(use.apply)
        newDoc <- loadDocument(config.newFile).map(use.apply)
      yield
        val oldRenderer = PDFRenderer(oldDoc)
        val newRenderer = PDFRenderer(newDoc)

        val maxPages = math.max(oldDoc.getNumberOfPages, newDoc.getNumberOfPages)
        val pageDiffs = (0 until maxPages).map(page => comparePage(oldDoc, newDoc, oldRenderer, newRenderer, page))
        val summary = createSummary(pageDiffs, config.hasImageInput)
        DiffResult(pageDiffs, summary)
    }.toEither.fold(
      error => Left(DiffEngineError.ComparisonError(error)),
      result => result,
    )

  /** Loads a document, converting images to PDF if necessary.
    * Returns Either with error details or loaded PDDocument.
    */
  private def loadDocument(path: Path): Either[DiffEngineError, PDDocument] =
    if !Files.exists(path) then Left(DiffEngineError.FileNotFound(path))
    else
      ImageFormat.fromPath(path) match
        case Some(format) =>
          logger.info(s"Detected ${format.displayName} image file, converting to PDF: $path")
          convertImageToPdf(path.toFile, path.toString)
        case None =>
          Try(Loader.loadPDF(path.toFile)).toEither.left.map(DiffEngineError.DocumentLoadError(path, _))

  /** Converts an image file to a single-page PDF document.
    * Returns Either with error details or converted PDDocument.
    */
  private def convertImageToPdf(
      imageFile: java.io.File,
      imagePath: String,
  ): Either[DiffEngineError, PDDocument] =
    val result = for
      bufferedImage <- Try(ImageIO.read(imageFile)).toEither.left.map(DiffEngineError.ImageReadError(imagePath, _))
      doc <- createPdfFromImage(bufferedImage, imageFile, imagePath)
    yield doc

    result.left.map { error =>
      logger.error(s"Failed to convert image to PDF: $imagePath", error.cause.orNull)
      error
    }

  /** Creates a PDF document from a BufferedImage.
    * Helper method to break down convertImageToPdf complexity.
    */
  private def createPdfFromImage(
      bufferedImage: BufferedImage,
      imageFile: java.io.File,
      imagePath: String,
  ): Either[DiffEngineError, PDDocument] = {
    Try {
      val doc = new PDDocument()
      val width = bufferedImage.getWidth
      val height = bufferedImage.getHeight
      val page = new PDPage(new PDRectangle(width, height))
      doc.addPage(page)
      val pdImage = PDImageXObject.createFromFile(imageFile.getAbsolutePath, doc)

      Using.resource(new PDPageContentStream(doc, page)) { contentStream =>
        contentStream.drawImage(pdImage, 0, 0, width, height)
      }

      doc
    }.toEither.left.map { e =>
      DiffEngineError.ImageConversionError(imagePath, e)
    }
  }

  /** Performs comprehensive comparison of a single page across all difference dimensions.
    * Detects all differences without suppression and adds informational notice when font changes exist.
    */
  private def comparePage(
      oldDoc: PDDocument,
      newDoc: PDDocument,
      oldRenderer: PDFRenderer,
      newRenderer: PDFRenderer,
      pageNum: Int,
  ): PageDiff =
    val hasOld = pageNum < oldDoc.getNumberOfPages
    val hasNew = pageNum < newDoc.getNumberOfPages

    (hasOld, hasNew) match
      case (true, true) =>
        val renderImages = generateRenderImages(oldRenderer, newRenderer, pageNum)

        val visualDiff = Some(compareVisual(renderImages))
        val colorDiffs = compareColors(renderImages)
        val (textDiffs, layoutDiffs) = compareTextAndLayout(oldDoc, newDoc, pageNum)
        val fontDiffs = compareFonts(oldDoc, newDoc, pageNum)

        val infoNotice = createInfoNotice(fontDiffs, visualDiff, colorDiffs, layoutDiffs)

        val (oldImagePath, newImagePath, diffImagePath) =
          if visualDiff.exists(_.pixelDifferenceRatio > config.thresholdPixel) then
            generateVisualDiffImages(renderImages, pageNum)
          else (None, None, None)

        val colorImagePath =
          if colorDiffs.nonEmpty then
            Some(generateColorDiffImageFromImages(renderImages, pageNum, config.thresholdColor))
          else None

        val hasDifferences =
          visualDiff.exists(_.differenceCount > 0) ||
            colorDiffs.nonEmpty ||
            textDiffs.nonEmpty ||
            layoutDiffs.nonEmpty ||
            fontDiffs.nonEmpty

        PageDiff(
          pageNum + 1,
          visualDiff,
          colorDiffs,
          textDiffs,
          layoutDiffs,
          fontDiffs,
          oldImagePath,
          newImagePath,
          diffImagePath,
          colorImagePath,
          infoNotice,
          hasDifferences = hasDifferences,
        )

      case (true, false) =>
        // New Page removed
        PageDiff(
          pageNum + 1,
          visualDiff = Some(VisualDiff(1.0, Int.MaxValue)),
          colorDiffs = Seq.empty,
          textDiffs = Seq.empty,
          layoutDiffs = Seq.empty,
          fontDiffs = Seq.empty,
          oldImagePath = None,
          newImagePath = None,
          diffImagePath = None,
          colorImagePath = None,
          suppressedDiffs = None,
          existsInNew = false,
          hasDifferences = true,
        )

      case (false, true) =>
        // New Page added
        PageDiff(
          pageNum + 1,
          visualDiff = Some(VisualDiff(1.0, Int.MaxValue)),
          colorDiffs = Seq.empty,
          textDiffs = Seq.empty,
          layoutDiffs = Seq.empty,
          fontDiffs = Seq.empty,
          oldImagePath = None,
          newImagePath = None,
          diffImagePath = None,
          colorImagePath = None,
          suppressedDiffs = None,
          existsInOld = false,
          hasDifferences = true,
        )

      case (false, false) =>
        // Should not happen with maxPages = max(oldPages, newPages), but safe fallback
        PageDiff(
          pageNum + 1,
          visualDiff = None,
          colorDiffs = Seq.empty,
          textDiffs = Seq.empty,
          layoutDiffs = Seq.empty,
          fontDiffs = Seq.empty,
          oldImagePath = None,
          newImagePath = None,
          diffImagePath = None,
          colorImagePath = None,
          suppressedDiffs = None,
          existsInOld = false,
          existsInNew = false,
          hasDifferences = false,
        )

  private def generateRenderImages(oldRenderer: PDFRenderer, newRenderer: PDFRenderer, pageNum: Int): RenderImages =
    val oldImage = oldRenderer.renderImageWithDPI(pageNum, config.dpi.toFloat, ImageType.RGB)
    val newImage = newRenderer.renderImageWithDPI(pageNum, config.dpi.toFloat, ImageType.RGB)

    // Use minimum dimensions to handle pages with different sizes
    val width = math.min(oldImage.getWidth, newImage.getWidth)
    val height = math.min(oldImage.getHeight, newImage.getHeight)

    RenderImages(oldImage = oldImage, newImage = newImage, minWidth = width, minHeight = height)

  /** Creates an informational notice when font differences are detected.
    *
    * This helps users understand that font changes often cause cascading visual/layout/color changes.
    * Unlike suppression, this just informs - all diffs are still shown.
    */
  private def createInfoNotice(
      fontDiffs: Seq[FontDiff],
      visualDiff: Option[VisualDiff],
      colorDiffs: Seq[ColorDiff],
      layoutDiffs: Seq[LayoutDiff],
  ): Option[SuppressedDiffs] =
    if fontDiffs.nonEmpty && (visualDiff.isDefined || colorDiffs.nonEmpty || layoutDiffs.nonEmpty) then
      Some(
        SuppressedDiffs(
          suppressedVisualDiff = None, // Not suppressing, just informing
          reason = "Font differences detected",
        ),
      )
    else None

  /** Performs pixel-by-pixel comparison of rendered PDF pages.
    *
    * Process:
    * 1. Render both pages to images at configured DPI
    * 2. Use minimum dimensions if sizes differ
    * 3. Compare RGB values at each pixel coordinate
    */
  private def compareVisual(renderImages: RenderImages): VisualDiff =
    val width = renderImages.minWidth
    val height = renderImages.minHeight

    var diffCount = 0
    val total = width * height

    // Nested loops for pixel-by-pixel comparison (optimized for performance)
    var y = 0
    while y < height do
      var x = 0
      while x < width do
        if renderImages.oldImage.getRGB(x, y) != renderImages.newImage.getRGB(x, y) then diffCount += 1
        x                                                                                          += 1
      y += 1

    val ratio = if total == 0 then 0.0 else diffCount.toDouble / total.toDouble
    VisualDiff(ratio, diffCount)

  /** Detects color changes using RGB Euclidean distance analysis.
    *
    * Uses sampling strategy to reduce memory usage:
    * - Samples every 10th pixel for performance
    * - Calculates RGB distance for changed pixels
    * - Returns top 200 most significant differences
    *
    * RGB distance range: 0 (identical) to 441.67 (max RGB difference)
    */
  private def compareColors(renderImages: RenderImages): Seq[ColorDiff] =
    val width = renderImages.minWidth
    val height = renderImages.minHeight

    val colorDiffs = ListBuffer.empty[ColorDiff]

    // Sample every 10th pixel to balance detection accuracy with performance/memory
    val samplingRate = 10

    var y = 0
    while y < height do
      var x = 0
      while x < width do
        // Only sample pixels for JSON storage
        if x % samplingRate == 0 && y % samplingRate == 0 then
          val oldRGB = renderImages.oldImage.getRGB(x, y)
          val newRGB = renderImages.newImage.getRGB(x, y)

          if oldRGB != newRGB then
            val oldColor = extractRgb(oldRGB)
            val newColor = extractRgb(newRGB)
            val distance = calculateColorDistance(oldColor, newColor)

            if distance > config.thresholdColor then colorDiffs += ColorDiff(x, y, oldColor, newColor, distance)
        x += 1
      y += 1

    // Return only top 200 most significant color differences to limit JSON size
    colorDiffs.sortBy(-_.distance).take(200).toSeq

  /** Extracts RGB components from packed integer color value.
    * Java AWT Color format: 0xAARRGGBB (alpha, red, green, blue)
    */
  private def extractRgb(rgb: Int): RgbColor =
    val r = (rgb >> 16) & 0xff
    val g = (rgb >> 8) & 0xff
    val b = rgb & 0xff
    RgbColor(r, g, b)

  /** Calculates Euclidean distance in RGB color space.
    *
    * Formula: sqrt((r2-r1)² + (g2-g1)² + (b2-b1)²)
    * Range: [0, 441.67] where 441.67 = sqrt(255² + 255² + 255²)
    */
  private def calculateColorDistance(c1: RgbColor, c2: RgbColor): Double =
    val deltaRed = c2.r - c1.r
    val deltaGreen = c2.g - c1.g
    val deltaBlue = c2.b - c1.b
    math.sqrt(deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue * deltaBlue)

  /** Simultaneously detects text content changes and layout shifts.
    *
    * Algorithm:
    * 1. Extract all text elements with positions from both PDFs
    * 2. Compare elements sequentially by index (maintains reading order)
    * 3. Use greedy matching to find corresponding elements
    * 4. Identify added text (exists in new but not old)
    * 5. Identify removed text (exists in old but not new)
    * 6. For matching text, calculate positional displacement
    */
  private def compareTextAndLayout(
      oldDoc: PDDocument,
      newDoc: PDDocument,
      pageNum: Int,
  ): (Seq[TextDiff], Seq[LayoutDiff]) =
    val oldTexts = extractTextElements(oldDoc, pageNum)
    val newTexts = extractTextElements(newDoc, pageNum)

    // Use greedy matching algorithm to find corresponding elements
    val matches = computeTextMatches(oldTexts, newTexts)

    val matchedOldIndices = matches.map { case (oldIdx, _) => oldIdx }.toSet
    val matchedNewIndices = matches.map { case (_, newIdx) => newIdx }.toSet

    // Added text: present in new but not matched
    val addedDiffs = newTexts.zipWithIndex.collect {
      case (elem, idx) if !matchedNewIndices.contains(idx) =>
        TextDiff(DiffType.Added, None, Some(elem.text), elem.bbox)
    }

    // Removed text: present in old but not matched
    val removedDiffs = oldTexts.zipWithIndex.collect {
      case (elem, idx) if !matchedOldIndices.contains(idx) =>
        TextDiff(DiffType.Removed, Some(elem.text), None, elem.bbox)
    }

    val textDiffs = addedDiffs ++ removedDiffs

    // Layout shifts: matched elements with position changes
    val layoutDiffs = matches.flatMap { case (oldIdx, newIdx) =>
      val oldElem = oldTexts(oldIdx)
      val newElem = newTexts(newIdx)
      val displacement = calculateDisplacement(oldElem.bbox, newElem.bbox)
      Option.when(
        displacement > config.thresholdLayout &&
          oldElem.text.trim.nonEmpty &&
          newElem.text.trim.nonEmpty,
      ) {
        LayoutDiff(oldElem.text, oldElem.bbox, newElem.bbox, displacement)
      }
    }

    (textDiffs, layoutDiffs)

  /** Computes matches between old and new text elements using greedy matching.
    * Returns pairs of (oldIndex, newIndex) for matching elements.
    */
  private def computeTextMatches(
      oldTexts: Seq[TextElement],
      newTexts: Seq[TextElement],
  ): Seq[(Int, Int)] =
    val (matches, _) = oldTexts.zipWithIndex
      .foldLeft((Vector.empty[(Int, Int)], Set.empty[Int])) { case ((matches, usedIndices), (oldElem, oldIdx)) =>
        newTexts.zipWithIndex.find { case (newElem, newIdx) =>
          newElem.text == oldElem.text && !usedIndices.contains(newIdx)
        }.map { case (_, newIdx) => (matches :+ (oldIdx, newIdx), usedIndices + newIdx) }
          .getOrElse((matches, usedIndices))
      }
    matches

  /** Detects font differences including substitutions, additions, and removals.
    *
    * Checks three types of changes:
    * 1. Font substitutions: Same text rendered with different fonts
    * 2. Added fonts: Fonts present in new but not old
    * 3. Removed fonts: Fonts present in old but not new
    */
  private def compareFonts(oldDoc: PDDocument, newDoc: PDDocument, pageNum: Int): Seq[FontDiff] =
    val oldFonts = extractFontsFromPage(oldDoc, pageNum)
    val newFonts = extractFontsFromPage(newDoc, pageNum)
    val oldTexts = extractTextElements(oldDoc, pageNum)
    val newTexts = extractTextElements(newDoc, pageNum)

    // Check for font substitutions: same text, different font
    val substitutionDiffs = oldTexts.flatMap { oldElem =>
      newTexts.find(_.text == oldElem.text).collect {
        case newElem if oldElem.fontName != newElem.fontName =>
          FontDiff(DiffType.Changed, oldFonts.get(oldElem.fontName), newFonts.get(newElem.fontName), Some(oldElem.text))
      }
    }

    // Check for newly added fonts
    val oldFontNames = oldFonts.keySet
    val newFontNames = newFonts.keySet

    val addedDiffs = newFontNames.diff(oldFontNames).flatMap { fontName =>
      newFonts.get(fontName).map { fontInfo =>
        FontDiff(DiffType.Added, None, Some(fontInfo), None)
      }
    }

    // Check for missing fonts (removed from old to new)
    val removedDiffs = oldFontNames.diff(newFontNames).flatMap { fontName =>
      oldFonts.get(fontName).map { fontInfo =>
        FontDiff(DiffType.Removed, Some(fontInfo), None, None)
      }
    }

    substitutionDiffs ++ addedDiffs ++ removedDiffs

  /** Extracts font metadata from a PDF page's resources.
    *
    * Returns a map of font name to FontInfo including:
    * - Font name
    * - Embedding status (embedded fonts travel with the PDF)
    * - Outline status (Type3 or outline fonts)
    */
  private def extractFontsFromPage(doc: PDDocument, pageNum: Int): Map[String, FontInfo] =
    if pageNum >= doc.getNumberOfPages then Map.empty
    else
      val page = doc.getPage(pageNum)
      Option(page.getResources).map { resources =>
        resources.getFontNames.asScala.flatMap { fontName =>
          extractSingleFont(resources.getFont(fontName), fontName, pageNum) match
            case Right(fontInfo) => Some(fontInfo)
            case Left(error) =>
              logger.debug(error.message)
              None
        }.toMap
      }.getOrElse(Map.empty)

  /** Extracts information from a single font, returning Either for error handling. */
  private def extractSingleFont(
      font: PDFont,
      fontName: org.apache.pdfbox.cos.COSName,
      pageNum: Int,
  ): Either[DiffEngineError, (String, FontInfo)] =
    Try {
      Option(font).map { _ =>
        val name = Option(font.getName).getOrElse(fontName.getName)
        val isEmbedded = font.isEmbedded
        val isOutlined = isType3OrOutlined(font)
        name -> FontInfo(name, isEmbedded, isOutlined)
      }.getOrElse(throw new NoSuchElementException("Font is null"))
    }.toEither.left.map { e =>
      DiffEngineError.FontExtractionError(fontName.getName, pageNum + 1, e)
    }

  /** Checks if a font is Type3 (bitmap) or explicitly marked as outline.
    * Type3 fonts use custom rendering procedures and may render differently.
    */
  private def isType3OrOutlined(font: PDFont): Boolean =
    font.isInstanceOf[PDType3Font] || font.getName.toLowerCase.contains("outline")

  /** Extracts all text elements from a page with their positions and fonts.
    *
    * Uses PDFBox's PDFTextStripper with custom TextPosition processing to capture:
    * - Text content
    * - Bounding box coordinates
    * - Font information
    */
  private def extractTextElements(doc: PDDocument, pageNum: Int): Seq[TextElement] =
    Try {
      val buf = ListBuffer.empty[TextElement]

      // Custom PDFTextStripper that captures position data for each character
      val stripper = new PDFTextStripper() {
        override def processTextPosition(text: TextPosition): Unit =
          val bbox = BoundingBox(
            text.getXDirAdj.toDouble,
            text.getYDirAdj.toDouble,
            text.getWidthDirAdj.toDouble,
            text.getHeightDir.toDouble,
          )

          val fontName = Option(text.getFont).flatMap(f => Option(f.getName)).getOrElse("Unknown")
          buf += TextElement(text.getUnicode, bbox, pageNum + 1, fontName)
      }

      stripper.setStartPage(pageNum + 1)
      stripper.setEndPage(pageNum + 1)
      stripper.getText(doc)

      buf.toSeq
    }.recover { case e: Exception =>
      logger.error(s"Failed to extract text elements from page ${pageNum + 1}", e)
      Seq.empty[TextElement]
    }.getOrElse(Seq.empty)

  /** Calculates Euclidean distance between two bounding box positions.
    * Uses top-left corner coordinates (x, y) to determine displacement.
    */
  private def calculateDisplacement(a: BoundingBox, b: BoundingBox): Double =
    val dx = b.x - a.x
    val dy = b.y - a.y
    math.sqrt(dx * dx + dy * dy)

  /** Generates separate old, new, and diff images for visual comparison.
    *
    * Returns a tuple of (oldImagePath, newImagePath, diffImagePath)
    */
  private def generateVisualDiffImages(
      renderImages: RenderImages,
      pageNum: Int,
  ): (Option[String], Option[String], Option[String]) =
    val width = renderImages.minWidth
    val height = renderImages.minHeight

    // Save old image
    val oldFileName = s"old_p${pageNum + 1}.png"
    ImageIO.write(renderImages.oldImage, "PNG", config.outputDir.resolve(oldFileName).toFile)

    // Save new image
    val newFileName = s"new_p${pageNum + 1}.png"
    ImageIO.write(renderImages.newImage, "PNG", config.outputDir.resolve(newFileName).toFile)

    // Generate diff image with red highlights
    val diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    var y = 0
    while y < height do
      var x = 0
      while x < width do
        val oldRGB = renderImages.oldImage.getRGB(x, y)
        val newRGB = renderImages.newImage.getRGB(x, y)
        if oldRGB != newRGB then diff.setRGB(x, y, Color.RED.getRGB)
        else diff.setRGB(x, y, newRGB)
        x += 1
      y += 1

    val diffFileName = s"diff_p${pageNum + 1}.png"
    ImageIO.write(diff, "PNG", config.outputDir.resolve(diffFileName).toFile)

    (Some(oldFileName), Some(newFileName), Some(diffFileName))

  /** Generates a color diff image highlighting color changes in magenta.
    * This method specifically highlights pixels where RGB distance exceeds the threshold,
    * marking them in magenta for easy identification.
    */
  private def generateColorDiffImageFromImages(renderImages: RenderImages, pageNum: Int, threshold: Double): String =
    val width = renderImages.minWidth
    val height = renderImages.minHeight

    val diffImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    // Copy new image as base and mark color differences in magenta
    var y = 0
    while y < height do
      var x = 0
      while x < width do
        val oldRGB = renderImages.oldImage.getRGB(x, y)
        val newRGB = renderImages.newImage.getRGB(x, y)

        // Check if this pixel has a color difference exceeding threshold
        if oldRGB != newRGB then
          val oldColor = extractRgb(oldRGB)
          val newColor = extractRgb(newRGB)
          val distance = calculateColorDistance(oldColor, newColor)

          if distance > threshold then
            // Mark with magenta to distinguish from visual diff (red)
            diffImage.setRGB(x, y, Color.MAGENTA.getRGB)
          else diffImage.setRGB(x, y, newRGB)
        else diffImage.setRGB(x, y, newRGB)

        x += 1
      y += 1

    val fileName = s"color_diff_p${pageNum + 1}.png"
    ImageIO.write(diffImage, "PNG", config.outputDir.resolve(fileName).toFile)
    fileName

  /** Creates summary statistics from all page-level differences.
    * Uses the hasDifferences field from PageDiff.
    */
  private def createSummary(pages: Seq[PageDiff], isImageComparison: Boolean): DiffSummary =
    // Use the hasDifferences field
    val pagesWithDiff = pages.count(_.hasDifferences)
    val visualCount = pages.count(_.visualDiff.exists(_.pixelDifferenceRatio > config.thresholdPixel))
    val colorCount = pages.count(_.colorDiffs.nonEmpty)
    val textCount = pages.map(_.textDiffs.size).sum
    val layoutCount = pages.map(_.layoutDiffs.size).sum
    val fontCount = pages.map(_.fontDiffs.size).sum
    val hasDifferences = pages.exists(_.hasDifferences)
    DiffSummary(
      totalPages = pages.size, pagesWithDiff = pagesWithDiff, visualDiffCount = visualCount,
      colorDiffCount = colorCount, textDiffCount = textCount, layoutDiffCount = layoutCount, fontDiffCount = fontCount,
      hasDifferences = hasDifferences, isImageComparison = isImageComparison,
    )
