package com.visualdiff.report

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

import com.visualdiff.core.DiffEngine
import com.visualdiff.helper.ImageTestHelpers
import com.visualdiff.helper.PdfTestHelpers
import com.visualdiff.models._
import org.scalatest.funspec.AnyFunSpec
import upickle.default.read

class ReporterSpec extends AnyFunSpec {

  /** Helper to extract DiffResult from Either or fail the test */
  private def compareAndExtract(config: Config) =
    DiffEngine(config).compare() match
      case Right(result) => result
      case Left(error) => fail(s"Comparison failed: ${error.message}", error.cause.orNull)

  describe("Reporter - single comparison") {

    it("generates JSON report with correct structure") {
      val dir = Files.createTempDirectory("reporter_json_test")
      val config = Config(
        oldFile = Paths.get("dummy.pdf"),
        newFile = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = createSampleDiffResult()
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val jsonFile = dir.resolve("diff.json")
      val jsonContent = Files.readString(jsonFile)

      assert(Files.exists(jsonFile))
      assert(jsonContent.contains("\"totalPages\""))
      assert(jsonContent.contains("\"pagesWithDiff\""))
      assert(jsonContent.contains("\"visualDiffCount\""))
    }

    it("generates HTML report with correct structure") {
      val dir = Files.createTempDirectory("reporter_html_test")
      val config = Config(
        oldFile = Paths.get("dummy.pdf"),
        newFile = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = createSampleDiffResult()
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val htmlFile = dir.resolve("report.html")
      val htmlContent = Files.readString(htmlFile)

      assert(Files.exists(htmlFile))
      assert(htmlContent.contains("Visual Diff Report"))
      assert(htmlContent.contains("summary-bar"))

      val jsFile = dir.resolve("report.js")
      assert(Files.exists(jsFile))
      assert(Files.size(jsFile) > 0)
    }

    it("generates all report files") {
      val dir = Files.createTempDirectory("reporter_all_files_test")
      val config = Config(
        oldFile = Paths.get("dummy.pdf"),
        newFile = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = createSampleDiffResult()
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      assert(Files.exists(dir.resolve("diff.json")))
      assert(Files.exists(dir.resolve("report.html")))
      assert(Files.exists(dir.resolve("report.css")))
      assert(Files.exists(dir.resolve("report.js")))
    }

    it("includes summary data in HTML report") {
      val dir = Files.createTempDirectory("reporter_summary_test")
      val config = Config(
        oldFile = Paths.get("dummy.pdf"),
        newFile = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val summary = DiffSummary(
        totalPages = 5, pagesWithDiff = 2, visualDiffCount = 1, colorDiffCount = 1, textDiffCount = 3,
        layoutDiffCount = 2, fontDiffCount = 1, hasDifferences = true,
      )

      val result = DiffResult(Seq.empty, summary)
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val htmlContent = Files.readString(dir.resolve("report.html"))

      assert(htmlContent.contains("5")) // totalPages
      assert(htmlContent.contains("2")) // pagesWithDiff
      assert(htmlContent.contains("3")) // textDiffCount
    }

    it("JSON can be deserialized back to DiffResult") {
      val dir = Files.createTempDirectory("reporter_json_deserialize_test")
      val config = Config(
        oldFile = Paths.get("dummy.pdf"),
        newFile = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val originalResult = createSampleDiffResult()
      val reporter = new Reporter(config)

      reporter.generateReports(originalResult)

      val jsonContent = Files.readString(dir.resolve("diff.json"))
      val deserializedResult = read[DiffResult](jsonContent)

      assert(deserializedResult.summary.totalPages == originalResult.summary.totalPages)
      assert(deserializedResult.pageDiffs.size == originalResult.pageDiffs.size)
    }
  }

  describe("Reporter - image format notice in single reports") {

    it("includes image format notice in HTML for image comparisons") {
      val dir = Files.createTempDirectory("reporter_image_notice")
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.jpg"))
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.png"))

      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)
      val result = compareAndExtract(config)
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val htmlFile = dir.resolve("report.html")
      val htmlContent = Files.readString(htmlFile)

      assert(Files.exists(htmlFile))
      assert(htmlContent.contains("image-format-notice"))
      assert(htmlContent.contains("Image Format Detected"))
      assert(htmlContent.contains("converted to PDF for comparison"))
      assert(htmlContent.contains("JPG/JPEG"))
      assert(htmlContent.contains("PNG"))
      assert(htmlContent.contains("GIF"))
      assert(htmlContent.contains("BMP"))
      assert(htmlContent.contains("TIF/TIFF"))
    }

    it("does not include image notice for pure PDF comparisons") {
      val dir = Files.createTempDirectory("reporter_no_image_notice")
      val result = createSampleDiffResult()
      val pdfResult = DiffResult(result.pageDiffs, result.summary) // isImageComparison = false

      val config = Config(
        oldFile = Paths.get("dummy.pdf"),
        newFile = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val reporter = new Reporter(config)
      reporter.generateReports(pdfResult)

      val htmlContent = Files.readString(dir.resolve("report.html"))

      assert(!htmlContent.contains("image-format-notice"))
      assert(!htmlContent.contains("Image Format Detected"))
    }

    it("includes isImageComparison flag in JSON output") {
      val dir = Files.createTempDirectory("reporter_json_image_flag")
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.jpg"))
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.jpg"))

      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)
      val result = compareAndExtract(config)
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val jsonFile = dir.resolve("diff.json")
      val jsonContent = Files.readString(jsonFile)

      assert(Files.exists(jsonFile))
      assert(jsonContent.contains("\"isImageComparison\""))
      assert(jsonContent.contains("true"))
    }
  }

  describe("Reporter - dynamic formats and CSS structure") {

    it("displays all supported formats dynamically in notice") {
      val dir = Files.createTempDirectory("reporter_dynamic_formats")
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.tif"))
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.bmp"))

      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)
      val result = compareAndExtract(config)
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val htmlFile = dir.resolve("report.html")
      val htmlContent = Files.readString(htmlFile)

      import com.visualdiff.models.ImageFormat
      val expectedFormats = ImageFormat.displayNames

      assert(htmlContent.contains(expectedFormats))
    }

    it("image notice has proper CSS structure") {
      val dir = Files.createTempDirectory("reporter_css_structure")
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.png"))
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.png"))

      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)
      val result = compareAndExtract(config)
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val htmlContent = Files.readString(dir.resolve("report.html"))

      assert(htmlContent.contains("class=\"image-format-notice\""))
      assert(htmlContent.contains("class=\"notice-header\""))
      assert(htmlContent.contains("class=\"notice-icon\""))
      assert(htmlContent.contains("class=\"notice-list\""))
      assert(htmlContent.contains("class=\"notice-details\""))
    }

    it("works with mixed PDF and image comparison") {
      val dir = Files.createTempDirectory("reporter_mixed_comparison")
      val pdf = PdfTestHelpers.createPdf(dir.resolve("doc.pdf"), "Test", java.awt.Color.WHITE)
      val img = ImageTestHelpers.createImageWithText(dir.resolve("img.png"), "Test")

      val config = Config(oldFile = pdf, newFile = img, outputDir = dir)
      val result = compareAndExtract(config)
      val reporter = new Reporter(config)

      reporter.generateReports(result)

      val htmlContent = Files.readString(dir.resolve("report.html"))

      assert(htmlContent.contains("image-format-notice"))
      assert(result.summary.isImageComparison)
    }
  }

  describe("Reporter - batch report") {

    it("generates batch report including unmatched files section") {
      val dir = Files.createTempDirectory("reporter_batch_report")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      // one matched pair
      val oldPdf = PdfTestHelpers.createPdfWithText(oldDir.resolve("common.pdf"), "Same")
      val newPdf = PdfTestHelpers.createPdfWithText(newDir.resolve("common.pdf"), "Same")

      // unmatched files
      val oldOnly = PdfTestHelpers.createEmptyPdf(oldDir.resolve("only-old.pdf"))
      val newOnly = PdfTestHelpers.createEmptyPdf(newDir.resolve("only-new.pdf"))

      val baseConfig = Config(oldFile = oldPdf, newFile = newPdf, outputDir = dir)

      val summary = BatchSummary(
        totalPairs = 1, successful = 1, successfulWithDiff = 0, failed = 0, totalPages = 1, totalDifferences = 0,
        totalDuration = Duration.ofSeconds(1), unmatchedOldCount = 1, unmatchedNewCount = 1,
      )

      // minimal PairResult for the one pair
      val diffSummary = DiffSummary(1, 0, 0, 0, 0, 0, 0, false)
      val diffResult = DiffResult(Seq.empty, diffSummary)
      val pair = BatchPair(oldPdf, newPdf, relativePath = "common.pdf")
      val pairResult = PairResult(pair, Some(diffResult), None, Duration.ofMillis(10), dir.resolve("pair_001"))

      val batchResult = BatchResult(
        pairs = Seq(pairResult),
        summary = summary,
        startTime = Instant.now().minusSeconds(1),
        endTime = Instant.now(),
        unmatchedOld = Seq(oldOnly),
        unmatchedNew = Seq(newOnly),
      )

      val reporter = new Reporter(baseConfig)
      reporter.generateBatchReport(batchResult, dir, oldDir, newDir)

      val htmlFile = dir.resolve("batch_report.html")
      val htmlContent = Files.readString(htmlFile)

      assert(Files.exists(htmlFile))
      assert(htmlContent.contains("Batch Comparison Report"))
      assert(htmlContent.contains("Unmatched Files"))
      assert(htmlContent.contains("Only in OLD Directory"))
      assert(htmlContent.contains("only-old.pdf"))
      assert(htmlContent.contains("Only in NEW Directory"))
      assert(htmlContent.contains("only-new.pdf"))
    }
  }

  /** Helper to create a sample DiffResult for testing */
  private def createSampleDiffResult(): DiffResult = {
    val bbox = BoundingBox(10.0, 20.0, 100.0, 50.0)
    val visualDiff = VisualDiff(0.05, 1000)
    val colorDiff = ColorDiff(50, 50, RgbColor(255, 0, 0), RgbColor(0, 0, 255), 441.67)
    val textDiff = TextDiff(DiffType.Added, None, Some("New text"), bbox)
    val layoutDiff = LayoutDiff("Moving", bbox, bbox.copy(x = 15.0), 5.0)
    val fontInfo = FontInfo("Helvetica", isEmbedded = true, isOutlined = false)
    val fontDiff = FontDiff(DiffType.Changed, Some(fontInfo), Some(fontInfo.copy(fontName = "Times")), Some("Test"))
    val pageDiff = PageDiff(
      pageNumber = 1, visualDiff = Some(visualDiff), colorDiffs = Seq(colorDiff), textDiffs = Seq(textDiff),
      layoutDiffs = Seq(layoutDiff), fontDiffs = Seq(fontDiff), oldImagePath = None, newImagePath = None,
      diffImagePath = None, colorImagePath = None, infoNotice = None, hasDifferences = true,
    )

    val summary = DiffSummary(
      totalPages = 1, pagesWithDiff = 1, visualDiffCount = 1, colorDiffCount = 1, textDiffCount = 1,
      layoutDiffCount = 1, fontDiffCount = 1, hasDifferences = true,
    )

    DiffResult(Seq(pageDiff), summary)
  }

}
