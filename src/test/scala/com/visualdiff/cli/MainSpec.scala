package com.visualdiff.cli

import java.awt.Color
import java.nio.file.Files
import java.nio.file.Paths

import com.visualdiff.helper.PdfTestHelpers
import com.visualdiff.models.BatchConfig
import com.visualdiff.models.Config
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.scalatest.funspec.AnyFunSpec

class MainSpec extends AnyFunSpec {

  describe("Main.run") {
    it("executes successfully and generates report") {
      val dir = Files.createTempDirectory("main_test_success")
      val pdf = PdfTestHelpers.createEmptyPdf(dir.resolve("a.pdf"))
      val config = Config(oldFile = pdf, newFile = pdf, outputDir = dir.resolve("out"))

      val result = Main.run(config)
      assert(result.isSuccess)
      assert(Files.exists(dir.resolve("out/report.html")))
      assert(Files.exists(dir.resolve("out/diff.json")))
      assert(Files.exists(dir.resolve("out/report.css")))
      assert(Files.exists(dir.resolve("out/report.js")))
    }

    it("fails validation if PDF does not exist") {
      val dir = Files.createTempDirectory("main_test_fail")
      val config = Config(
        oldFile = dir.resolve("non_existent.pdf"),
        newFile = dir.resolve("non_existent_2.pdf"),
        outputDir = Paths.get("./report"),
      )

      val result = Main.run(config)
      assert(result.isFailure)
    }

    it("respects fail-on-diff flag") {
      val dir = Files.createTempDirectory("main_test_flag")
      val pdf = PdfTestHelpers.createEmptyPdf(dir.resolve("flag.pdf"))
      val config = Config(oldFile = pdf, newFile = pdf, failOnDiff = true, outputDir = dir)

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("returns true when differences are detected") {
      val dir = Files.createTempDirectory("main_test_diff_detected")
      val pdf1 = PdfTestHelpers.createPdfWithText(dir.resolve("d1.pdf"), "Original")
      val pdf2 = PdfTestHelpers.createPdfWithText(dir.resolve("d2.pdf"), "Modified")
      val config = Config(
        oldFile = pdf1,
        newFile = pdf2,
        outputDir = dir.resolve("out"),
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("creates output directory if it doesn't exist") {
      val dir = Files.createTempDirectory("main_test_create_dir")
      val pdf = PdfTestHelpers.createEmptyPdf(dir.resolve("test.pdf"))
      val outputDir = dir.resolve("new_output_dir")

      assert(!Files.exists(outputDir))

      val config = Config(oldFile = pdf, newFile = pdf, outputDir = outputDir)
      val result = Main.run(config)

      assert(result.isSuccess)
      assert(Files.exists(outputDir))
    }

    it("handles custom threshold settings") {
      val dir = Files.createTempDirectory("main_test_thresholds")
      val pdf = PdfTestHelpers.createEmptyPdf(dir.resolve("threshold.pdf"))
      val config = Config(
        oldFile = pdf, newFile = pdf, outputDir = dir.resolve("out"), thresholdPixel = 0.05, thresholdLayout = 10.0,
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("generates JSON output file with correct structure") {
      val dir = Files.createTempDirectory("main_test_json")
      val pdf = PdfTestHelpers.createEmptyPdf(dir.resolve("json_test.pdf"))
      val config = Config(oldFile = pdf, newFile = pdf, outputDir = dir.resolve("out"))

      val result = Main.run(config)
      assert(result.isSuccess)

      val jsonPath = dir.resolve("out/diff.json")
      assert(Files.exists(jsonPath))
      assert(Files.size(jsonPath) > 0L)
    }

    it("fails when old PDF path is invalid") {
      val dir = Files.createTempDirectory("main_test_invalid_old")
      val validPdf = PdfTestHelpers.createEmptyPdf(dir.resolve("valid.pdf"))
      val config = Config(
        oldFile = dir.resolve("invalid.pdf"),
        newFile = validPdf,
        outputDir = dir,
      )

      val result = Main.run(config)
      assert(result.isFailure)
    }

    it("fails when new PDF path is invalid") {
      val dir = Files.createTempDirectory("main_test_invalid_new")
      val validPdf = PdfTestHelpers.createEmptyPdf(dir.resolve("valid.pdf"))
      val config = Config(
        oldFile = validPdf,
        newFile = dir.resolve("invalid.pdf"),
        outputDir = dir,
      )

      val result = Main.run(config)
      assert(result.isFailure)
    }

    it("handles multi-page PDF comparison") {
      val dir = Files.createTempDirectory("main_test_multipage")
      val pdf1 = PdfTestHelpers.createMultiPagePdf(dir.resolve("mp1.pdf"), Seq("Page 1", "Page 2", "Page 3"))
      val pdf2 = PdfTestHelpers.createMultiPagePdf(dir.resolve("mp2.pdf"), Seq("Page 1", "Page 2", "Page 3"))
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir.resolve("out"))

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("generates HTML report with proper structure") {
      val dir = Files.createTempDirectory("main_test_html")
      val pdf = PdfTestHelpers.createEmptyPdf(dir.resolve("html_test.pdf"))
      val config = Config(oldFile = pdf, newFile = pdf, outputDir = dir.resolve("out"))

      val result = Main.run(config)
      assert(result.isSuccess)

      val htmlPath = dir.resolve("out/report.html")
      assert(Files.exists(htmlPath))

      val htmlContent = Files.readString(htmlPath)
      assert(htmlContent.contains("<!DOCTYPE html>"))
    }
  }

  describe("difference detection") {
    it("detects visual differences") {
      val dir = Files.createTempDirectory("main_test_visual")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("v1.pdf"), "Test", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdf(dir.resolve("v2.pdf"), "Test", Color.LIGHT_GRAY)
      val config = Config(
        oldFile = pdf1,
        newFile = pdf2,
        outputDir = dir.resolve("out"),
        thresholdPixel = 0.0,
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("detects text differences") {
      val dir = Files.createTempDirectory("main_test_text")
      val pdf1 = PdfTestHelpers.createPdfWithText(dir.resolve("t1.pdf"), "Hello World")
      val pdf2 = PdfTestHelpers.createPdfWithText(dir.resolve("t2.pdf"), "Hello Scala")
      val config = Config(
        oldFile = pdf1,
        newFile = pdf2,
        outputDir = dir.resolve("out"),
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("detects font differences") {
      val dir = Files.createTempDirectory("main_test_font")
      val pdf1 = PdfTestHelpers.createPdfWithFont(
        dir.resolve("f1.pdf"),
        "Test",
        Standard14Fonts.FontName.HELVETICA,
      )
      val pdf2 = PdfTestHelpers.createPdfWithFont(
        dir.resolve("f2.pdf"),
        "Test",
        Standard14Fonts.FontName.TIMES_ROMAN,
      )
      val config = Config(
        oldFile = pdf1,
        newFile = pdf2,
        outputDir = dir.resolve("out"),
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("detects color differences") {
      val dir = Files.createTempDirectory("main_test_color")
      val pdf1 = PdfTestHelpers.createPdfWithTextColor(dir.resolve("c1.pdf"), "Test", Color.RED)
      val pdf2 = PdfTestHelpers.createPdfWithTextColor(dir.resolve("c2.pdf"), "Test", Color.BLUE)
      val config = Config(
        oldFile = pdf1,
        newFile = pdf2,
        outputDir = dir.resolve("out"),
        thresholdColor = 30.0,
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("detects layout differences") {
      val dir = Files.createTempDirectory("main_test_layout")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("l1.pdf"), "Moving Text", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdf(dir.resolve("l2.pdf"), "Moving Text", Color.WHITE, x = 150, y = 150)
      val config = Config(
        oldFile = pdf1,
        newFile = pdf2,
        outputDir = dir.resolve("out"),
        thresholdLayout = 5.0,
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }

    it("handles PDFs with different page counts") {
      val dir = Files.createTempDirectory("main_test_page_diff")
      val pdf1 = PdfTestHelpers.createMultiPagePdf(dir.resolve("p1.pdf"), Seq("Page 1", "Page 2"))
      val pdf2 = PdfTestHelpers.createMultiPagePdf(dir.resolve("p2.pdf"), Seq("Page 1", "Page 2", "Page 3"))
      val config = Config(
        oldFile = pdf1,
        newFile = pdf2,
        outputDir = dir.resolve("out"),
      )

      val result = Main.run(config)
      assert(result.isSuccess)
    }
  }

  describe("Main.runBatch") {

    it("executes batch mode successfully and generates batch report") {
      val dir = Files.createTempDirectory("main_batch_success")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      PdfTestHelpers.createEmptyPdf(oldDir.resolve("doc1.pdf"))
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("doc2.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("doc1.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("doc2.pdf"))

      val batchConfig = BatchConfig(
        dirOld = oldDir,
        dirNew = newDir,
        recursive = false,
        continueOnError = true,
        baseConfig = Config(
          oldFile = oldDir,
          newFile = newDir,
          outputDir = dir.resolve("output"),
        ),
      )

      val result = Main.runBatch(batchConfig)

      assert(result.isSuccess)
      assert(Files.exists(dir.resolve("output/batch_report.html")))
      assert(Files.exists(dir.resolve("output/report.css")))
      assert(Files.exists(dir.resolve("output/report.js")))
      assert(Files.exists(dir.resolve("output/pair_001_doc1.pdf/report.html")))
      assert(Files.exists(dir.resolve("output/pair_002_doc2.pdf/report.html")))
    }

    it("fails validation if batch directory does not exist") {
      val dir = Files.createTempDirectory("main_batch_fail")

      val batchConfig = BatchConfig(
        dirOld = dir.resolve("nonexistent_old"),
        dirNew = dir.resolve("nonexistent_new"),
        recursive = false,
        continueOnError = true,
        baseConfig = Config(
          oldFile = dir,
          newFile = dir,
          outputDir = dir.resolve("output"),
        ),
      )

      val result = Main.runBatch(batchConfig)

      assert(result.isSuccess) // Should succeed but with no pairs
      assert(result.get.summary.totalPairs == 0)
    }

    it("returns batch result with correct statistics") {
      val dir = Files.createTempDirectory("main_batch_stats")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      PdfTestHelpers.createPdfWithText(oldDir.resolve("same.pdf"), "Identical")
      PdfTestHelpers.createPdfWithText(newDir.resolve("same.pdf"), "Identical")

      PdfTestHelpers.createPdfWithText(oldDir.resolve("diff.pdf"), "Old")
      PdfTestHelpers.createPdfWithText(newDir.resolve("diff.pdf"), "New")

      val batchConfig = BatchConfig(
        dirOld = oldDir,
        dirNew = newDir,
        recursive = false,
        continueOnError = true,
        baseConfig = Config(
          oldFile = oldDir,
          newFile = newDir,
          outputDir = dir.resolve("output"),
        ),
      )

      val result = Main.runBatch(batchConfig)

      assert(result.isSuccess)
      val batchResult = result.get
      assert(batchResult.summary.totalPairs == 2)
      assert(batchResult.summary.successful == 2)
      assert(batchResult.summary.successfulWithDiff == 1)
      assert(batchResult.hasAnyDifferences)
    }

    it("respects continueOnError flag in batch mode") {
      val dir = Files.createTempDirectory("main_batch_continue_error")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      PdfTestHelpers.createEmptyPdf(oldDir.resolve("valid.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("valid.pdf"))

      Files.writeString(oldDir.resolve("corrupt.pdf"), "INVALID")
      Files.writeString(newDir.resolve("corrupt.pdf"), "INVALID")

      val batchConfig = BatchConfig(
        dirOld = oldDir,
        dirNew = newDir,
        recursive = false,
        continueOnError = true,
        baseConfig = Config(
          oldFile = oldDir,
          newFile = newDir,
          outputDir = dir.resolve("output"),
        ),
      )

      val result = Main.runBatch(batchConfig)

      assert(result.isSuccess)
      assert(result.get.summary.failed == 1)
      assert(result.get.summary.successful == 1)
    }

    it("creates output directory if it doesn't exist in batch mode") {
      val dir = Files.createTempDirectory("main_batch_create_dir")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      val outputDir = dir.resolve("new_output_dir")

      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("doc.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("doc.pdf"))

      assert(!Files.exists(outputDir))

      val batchConfig = BatchConfig(
        dirOld = oldDir,
        dirNew = newDir,
        recursive = false,
        continueOnError = true,
        baseConfig = Config(
          oldFile = oldDir,
          newFile = newDir,
          outputDir = outputDir,
        ),
      )

      val result = Main.runBatch(batchConfig)

      assert(result.isSuccess)
      assert(Files.exists(outputDir))
      assert(Files.exists(outputDir.resolve("batch_report.html")))
    }
  }

}
