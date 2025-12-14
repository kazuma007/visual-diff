package com.visualdiff.report

import java.nio.file.Files
import java.nio.file.Paths

import com.visualdiff.cli.Config
import com.visualdiff.models._
import org.scalatest.funspec.AnyFunSpec
import upickle.default.read

class ReporterSpec extends AnyFunSpec {

  describe("Reporter") {
    it("generates JSON report with correct structure") {
      val dir = Files.createTempDirectory("reporter_json_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = createSampleDiffResult()
      val reporter = new Reporter(config)
      reporter.generateReports(result)

      val jsonFile = dir.resolve("diff.json")
      assert(Files.exists(jsonFile))

      val jsonContent = Files.readString(jsonFile)
      assert(jsonContent.contains("\"totalPages\""))
      assert(jsonContent.contains("\"pagesWithDiff\""))
      assert(jsonContent.contains("\"visualDiffCount\""))
    }

    it("generates HTML report with correct structure") {
      val dir = Files.createTempDirectory("reporter_html_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = createSampleDiffResult()
      val reporter = new Reporter(config)
      reporter.generateReports(result)

      val htmlFile = dir.resolve("report.html")
      assert(Files.exists(htmlFile))

      val htmlContent = Files.readString(htmlFile)
      assert(htmlContent.contains("<!DOCTYPE html>"))
      assert(htmlContent.contains("Visual Diff Report"))
      assert(htmlContent.contains("</html>"))
    }

    it("copies CSS and JS files to output directory") {
      val dir = Files.createTempDirectory("reporter_assets_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = createSampleDiffResult()
      val reporter = new Reporter(config)
      reporter.generateReports(result)

      assert(Files.exists(dir.resolve("report.css")))
      assert(Files.exists(dir.resolve("report.js")))
    }

    it("references external CSS and JS in HTML") {
      val dir = Files.createTempDirectory("reporter_links_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = createSampleDiffResult()
      val reporter = new Reporter(config)
      reporter.generateReports(result)

      val htmlContent = Files.readString(dir.resolve("report.html"))
      assert(htmlContent.contains("href=\"report.css\""))
      assert(htmlContent.contains("src=\"report.js\""))
    }

    it("generates report with no differences message") {
      val dir = Files.createTempDirectory("reporter_no_diff_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val result = DiffResult(
        pageDiffs = Seq.empty,
        summary = DiffSummary(1, 0, 0, 0, 0, 0, 0),
      )

      val reporter = new Reporter(config)
      reporter.generateReports(result)

      val htmlContent = Files.readString(dir.resolve("report.html"))
      assert(htmlContent.contains("No differences found"))
    }

    it("includes all diff types in HTML report") {
      val dir = Files.createTempDirectory("reporter_all_types_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val pageDiff = PageDiff(
        pageNumber = 1,
        visualDiff = Some(VisualDiff(0.1, 1000)),
        colorDiffs = Seq(ColorDiff(10, 10, RgbColor(0, 0, 0), RgbColor(255, 255, 255), 441.0)),
        textDiffs = Seq(TextDiff(DiffType.Added, None, Some("New text"), BoundingBox(0, 0, 10, 10))),
        layoutDiffs = Seq(LayoutDiff("Text", BoundingBox(0, 0, 10, 10), BoundingBox(5, 5, 10, 10), 7.07)),
        fontDiffs = Seq(
          FontDiff(
            DiffType.Changed,
            Some(FontInfo("Arial", true, false)),
            Some(FontInfo("Times", true, false)),
            Some("Test"),
          ),
        ),
        oldImagePath = Some("old_p1.png"),
        newImagePath = Some("new_p1.png"),
        diffImagePath = Some("diff_p1.png"),
        colorImagePath = None,
      )

      val result = DiffResult(
        pageDiffs = Seq(pageDiff),
        summary = DiffSummary(1, 1, 1, 1, 1, 1, 1),
      )

      val reporter = new Reporter(config)
      reporter.generateReports(result)

      val htmlContent = Files.readString(dir.resolve("report.html"))
      assert(htmlContent.contains("Visual"))
      assert(htmlContent.contains("Color"))
      assert(htmlContent.contains("Text"))
      assert(htmlContent.contains("Layout"))
      assert(htmlContent.contains("Font"))
    }

    it("serializes and deserializes DiffResult correctly") {
      val dir = Files.createTempDirectory("reporter_serialization_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
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

    it("handles suppressed diffs in HTML report") {
      val dir = Files.createTempDirectory("reporter_suppressed_test")
      val config = Config(
        oldPdf = Paths.get("dummy.pdf"),
        newPdf = Paths.get("dummy.pdf"),
        outputDir = dir,
      )

      val pageDiff = PageDiff(
        pageNumber = 1,
        visualDiff = None,
        colorDiffs = Seq.empty,
        textDiffs = Seq.empty,
        layoutDiffs = Seq.empty,
        fontDiffs = Seq(
          FontDiff(
            DiffType.Changed,
            Some(FontInfo("Arial", true, false)),
            Some(FontInfo("Times", true, false)),
            Some("Test"),
          ),
        ),
        oldImagePath = None,
        newImagePath = None,
        diffImagePath = None,
        colorImagePath = None,
        suppressedDiffs = Some(
          SuppressedDiffs(
            suppressedVisualDiff = Some(VisualDiff(0.1, 1000)),
            suppressedColorDiffCount = 5,
            suppressedLayoutDiffCount = 3,
            reason = "Font differences detected",
          ),
        ),
      )

      val result = DiffResult(
        pageDiffs = Seq(pageDiff),
        summary = DiffSummary(1, 1, 0, 0, 0, 0, 1),
      )

      val reporter = new Reporter(config)
      reporter.generateReports(result)

      val htmlContent = Files.readString(dir.resolve("report.html"))
      assert(htmlContent.contains("Font differences detected"))
    }
  }

  private def createSampleDiffResult(): DiffResult =
    DiffResult(
      pageDiffs = Seq(
        PageDiff(
          pageNumber = 1,
          visualDiff = Some(VisualDiff(0.05, 500)),
          colorDiffs = Seq.empty,
          textDiffs = Seq.empty,
          layoutDiffs = Seq.empty,
          fontDiffs = Seq.empty,
          oldImagePath = None,
          newImagePath = None,
          diffImagePath = None,
          colorImagePath = None,
        ),
      ),
      summary = DiffSummary(1, 1, 1, 0, 0, 0, 0),
    )

}
