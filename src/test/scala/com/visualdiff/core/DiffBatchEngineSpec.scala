package com.visualdiff.core

import java.nio.file.Files
import java.nio.file.Path

import com.visualdiff.helper.ImageTestHelpers
import com.visualdiff.helper.PdfTestHelpers
import com.visualdiff.models._
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.scalatest.funspec.AnyFunSpec

class DiffBatchEngineSpec extends AnyFunSpec {

  private def mkConfig(baseDir: Path): Config =
    Config(
      oldFile = baseDir.resolve("old"), // dummy; overridden per pair
      newFile = baseDir.resolve("new"), // dummy; overridden per pair
      outputDir = baseDir.resolve("output"),
    )

  describe("DiffBatchEngine - pair discovery and unmatched files") {

    it("discovers matching pairs and reports no unmatched files when dirs align") {
      val dir = Files.createTempDirectory("batch_match_all")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      PdfTestHelpers.createEmptyPdf(oldDir.resolve("a.pdf"))
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("b.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("a.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("b.pdf"))

      val batchConfig = BatchConfig(
        dirOld = oldDir, dirNew = newDir, recursive = false, continueOnError = true, baseConfig = mkConfig(dir),
      )

      val engine = new DiffBatchEngine(batchConfig)
      val result = engine.compareAll()

      assert(result.summary.totalPairs == 2)
      assert(result.summary.unmatchedOldCount == 0)
      assert(result.summary.unmatchedNewCount == 0)
      assert(result.unmatchedOld.isEmpty)
      assert(result.unmatchedNew.isEmpty)
    }

    it("tracks files only in OLD directory as unmatchedOld") {
      val dir = Files.createTempDirectory("batch_unmatched_old")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      // pair
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("pair.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("pair.pdf"))
      // only in old
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("only-old-1.pdf"))
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("only-old-2.pdf"))

      val batchConfig = BatchConfig(
        dirOld = oldDir, dirNew = newDir, recursive = false, continueOnError = true, baseConfig = mkConfig(dir),
      )

      val engine = new DiffBatchEngine(batchConfig)
      val result = engine.compareAll()

      assert(result.summary.totalPairs == 1)
      assert(result.summary.unmatchedOldCount == 2)
      assert(result.summary.unmatchedNewCount == 0)

      val names = result.unmatchedOld.map(_.getFileName.toString).toSet
      assert(names == Set("only-old-1.pdf", "only-old-2.pdf"))
      assert(result.unmatchedNew.isEmpty)
    }

    it("tracks files only in NEW directory as unmatchedNew") {
      val dir = Files.createTempDirectory("batch_unmatched_new")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      // pair
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("pair.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("pair.pdf"))
      // only in new
      PdfTestHelpers.createEmptyPdf(newDir.resolve("only-new-1.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("only-new-2.pdf"))

      val batchConfig = BatchConfig(
        dirOld = oldDir, dirNew = newDir, recursive = false, continueOnError = true, baseConfig = mkConfig(dir),
      )

      val engine = new DiffBatchEngine(batchConfig)
      val result = engine.compareAll()

      assert(result.summary.totalPairs == 1)
      assert(result.summary.unmatchedOldCount == 0)
      assert(result.summary.unmatchedNewCount == 2)

      val names = result.unmatchedNew.map(_.getFileName.toString).toSet
      assert(names == Set("only-new-1.pdf", "only-new-2.pdf"))
      assert(result.unmatchedOld.isEmpty)
    }

    it("tracks unmatched files in both OLD and NEW directories") {
      val dir = Files.createTempDirectory("batch_unmatched_both")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      // one matching pair
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("common.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("common.pdf"))

      // only old
      PdfTestHelpers.createEmptyPdf(oldDir.resolve("old-only.pdf"))
      // only new
      PdfTestHelpers.createEmptyPdf(newDir.resolve("new-only.pdf"))

      val batchConfig = BatchConfig(
        dirOld = oldDir, dirNew = newDir, recursive = false, continueOnError = true, baseConfig = mkConfig(dir),
      )

      val engine = new DiffBatchEngine(batchConfig)
      val result = engine.compareAll()

      assert(result.summary.totalPairs == 1)
      assert(result.summary.unmatchedOldCount == 1)
      assert(result.summary.unmatchedNewCount == 1)

      assert(result.unmatchedOld.map(_.getFileName.toString) == Seq("old-only.pdf"))
      assert(result.unmatchedNew.map(_.getFileName.toString) == Seq("new-only.pdf"))
    }

    it("returns empty pairs but still reports unmatched when there are no matches") {
      val dir = Files.createTempDirectory("batch_no_pairs")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      PdfTestHelpers.createEmptyPdf(oldDir.resolve("old-only.pdf"))
      PdfTestHelpers.createEmptyPdf(newDir.resolve("new-only.pdf"))

      val batchConfig = BatchConfig(
        dirOld = oldDir, dirNew = newDir, recursive = false, continueOnError = true, baseConfig = mkConfig(dir),
      )

      val engine = new DiffBatchEngine(batchConfig)
      val result = engine.compareAll()

      assert(result.pairs.isEmpty)
      assert(result.summary.totalPairs == 0)
      assert(result.summary.unmatchedOldCount == 1)
      assert(result.summary.unmatchedNewCount == 1)
    }
  }

  describe("DiffBatchEngine - formats and summary") {

    it("supports PDF and image formats and populates summary correctly") {
      val dir = Files.createTempDirectory("batch_formats_summary")
      val oldDir = dir.resolve("old")
      val newDir = dir.resolve("new")
      Files.createDirectories(oldDir)
      Files.createDirectories(newDir)

      // PDFs
      PdfTestHelpers.createPdfWithText(oldDir.resolve("same.pdf"), "Same")
      PdfTestHelpers.createPdfWithText(newDir.resolve("same.pdf"), "Same")

      PdfTestHelpers.createPdfWithFont(
        oldDir.resolve("font-change.pdf"),
        "Test",
        Standard14Fonts.FontName.HELVETICA,
      )
      PdfTestHelpers.createPdfWithFont(
        newDir.resolve("font-change.pdf"),
        "Test",
        Standard14Fonts.FontName.TIMES_ROMAN,
      )

      // images
      ImageTestHelpers.createImage(oldDir.resolve("img.jpg"))
      ImageTestHelpers.createImage(newDir.resolve("img.jpg"))

      val batchConfig = BatchConfig(
        dirOld = oldDir, dirNew = newDir, recursive = false, continueOnError = true, baseConfig = mkConfig(dir),
      )

      val engine = new DiffBatchEngine(batchConfig)
      val result = engine.compareAll()

      // 3 matching pairs
      assert(result.summary.totalPairs == 3)
      // at least one with diff (font-change)
      assert(result.summary.successful >= 3)
      assert(result.summary.successfulWithDiff >= 1)
      assert(result.summary.failed == 0)
      // unmatched counts still zero
      assert(result.summary.unmatchedOldCount == 0)
      assert(result.summary.unmatchedNewCount == 0)
    }
  }

}
