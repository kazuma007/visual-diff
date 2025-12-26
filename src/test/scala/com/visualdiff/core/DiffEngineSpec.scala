package com.visualdiff.core

import java.awt.Color
import java.nio.file.Files

import com.visualdiff.helper.ImageTestHelpers
import com.visualdiff.helper.PdfTestHelpers
import com.visualdiff.models.Config
import com.visualdiff.models.DiffType._
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._

class DiffEngineSpec extends AnyFunSpec {

  /** Helper to extract DiffResult from Either or fail the test */
  private def compareAndExtract(config: Config) =
    DiffEngine(config).compare() match
      case Right(result) => result
      case Left(error) => fail(s"Comparison failed: ${error.message}", error.cause.orNull)

  describe("visual differences") {
    it("detects pixel changes") {
      val dir = Files.createTempDirectory("diff_visual_test")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("v1.pdf"), "Hello World", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdf(dir.resolve("v2.pdf"), "Hello World", Color.LIGHT_GRAY)
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir, thresholdPixel = 0.0)

      val result = compareAndExtract(config)

      assert(result.hasDifferences)
      assert(result.summary.visualDiffCount > 0)
      assert(result.pageDiffs.head.visualDiff.isDefined)
      result.pageDiffs.head.visualDiff match {
        case Some(vd) => assert(vd.pixelDifferenceRatio > 0.0)
        case None => fail("visualDiff should exist")
      }
    }

    it("respects pixel thresholds") {
      val dir = Files.createTempDirectory("diff_threshold_test")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("th1.pdf"), "Text", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdfWithDot(dir.resolve("th2.pdf"), "Text", Color.WHITE, dotColor = Color.BLACK)

      val thresholdCases = Table(
        ("description", "threshold", "expectedCount"),
        ("strict", 0.0, 1),
        ("lenient", 0.5, 0),
      )

      forAll(thresholdCases) { (_, threshold, expected) =>
        val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir, thresholdPixel = threshold)
        assert(compareAndExtract(config).summary.visualDiffCount == expected)
      }
    }

    it("generates diff images") {
      val dir = Files.createTempDirectory("diff_image_gen_test")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("ig1.pdf"), "Test", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdf(dir.resolve("ig2.pdf"), "TestT", Color.LIGHT_GRAY)
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir, thresholdPixel = 0.0)

      val result = compareAndExtract(config)
      val pageDiff = result.pageDiffs.head

      assert(pageDiff.oldImagePath.isDefined)
      assert(pageDiff.newImagePath.isDefined)
      assert(pageDiff.diffImagePath.isDefined)
      assert(Files.exists(dir.resolve(pageDiff.oldImagePath.get)))
      assert(Files.exists(dir.resolve(pageDiff.newImagePath.get)))
      assert(Files.exists(dir.resolve(pageDiff.diffImagePath.get)))
    }
  }

  describe("text differences") {
    it("detects content changes") {
      val dir = Files.createTempDirectory("diff_text_test")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("t1.pdf"), "TestB C", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdf(dir.resolve("t2.pdf"), "TestAB", Color.WHITE)
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir)

      val result = compareAndExtract(config)

      assert(result.hasDifferences)
      assert(result.summary.textDiffCount == 3)
      val diffs = result.pageDiffs.flatMap(_.textDiffs)
      assert(diffs.filter(_.diffType == Added).flatMap(_.newText) == Seq("A"))
      assert(diffs.filter(_.diffType == Removed).flatMap(_.oldText) == Seq(" ", "C"))
    }

    it("handles special characters and long text") {
      val dir = Files.createTempDirectory("diff_text_edge")
      val pdf1 = PdfTestHelpers.createPdfWithText(dir.resolve("sc1.pdf"), "Hello © 2024 — Test™")
      val pdf2 = PdfTestHelpers.createPdfWithText(dir.resolve("lt1.pdf"), "A" * 1000)

      val testCases = Table(
        ("pdf", "config"),
        (pdf1, Config(oldFile = pdf1, newFile = pdf1, outputDir = dir)),
        (pdf2, Config(oldFile = pdf2, newFile = pdf2, outputDir = dir)),
      )

      forAll(testCases) { (_, config) =>
        assert(!compareAndExtract(config).hasDifferences)
      }
    }
  }

  describe("font differences") {
    it("detects substitution, addition, removal") {
      val dir = Files.createTempDirectory("diff_font_test")
      val pdf1Helvetica =
        PdfTestHelpers.createPdfWithFont(dir.resolve("f1.pdf"), "Test", Standard14Fonts.FontName.HELVETICA)
      val pdf2Times =
        PdfTestHelpers.createPdfWithFont(dir.resolve("f2.pdf"), "Test", Standard14Fonts.FontName.TIMES_ROMAN)
      val pdfMulti = PdfTestHelpers.createPdfWithMultipleFonts(
        dir.resolve("multi.pdf"),
        Seq(
          ("Text", Standard14Fonts.FontName.HELVETICA),
          ("More", Standard14Fonts.FontName.COURIER),
        ),
      )
      val pdfSimple = PdfTestHelpers.createPdf(dir.resolve("simple.pdf"), "Text", Color.WHITE)

      val fontCases = Table(
        ("description", "config"),
        (
          "substitution",
          Config(oldFile = pdf1Helvetica, newFile = pdf2Times, outputDir = dir),
        ),
        (
          "addition",
          Config(oldFile = pdfSimple, newFile = pdfMulti, outputDir = dir),
        ),
        (
          "removal",
          Config(oldFile = pdfMulti, newFile = pdfSimple, outputDir = dir),
        ),
      )

      forAll(fontCases) { (_, config) =>
        val result = compareAndExtract(config)
        assert(result.summary.fontDiffCount > 0)
      }
    }

    it("ignores identical fonts") {
      val dir = Files.createTempDirectory("diff_font_identical")
      val pdf1 = PdfTestHelpers.createPdfWithFont(dir.resolve("fi1.pdf"), "Same", Standard14Fonts.FontName.HELVETICA)
      val pdf2 = PdfTestHelpers.createPdfWithFont(dir.resolve("fi2.pdf"), "Same", Standard14Fonts.FontName.HELVETICA)
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir)

      assert(compareAndExtract(config).summary.fontDiffCount == 0)
    }
  }

  describe("color differences") {
    val colorCases = Table(
      ("description", "oldColor", "newColor", "threshold", "expectedCount"),
      ("below threshold", new Color(0, 0, 0), new Color(10, 10, 10), 30.0, 0),
      ("above threshold", new Color(0, 0, 0), new Color(100, 100, 100), 30.0, 1),
      ("max distance", new Color(255, 255, 255), new Color(0, 0, 0), 30.0, 1),
      ("identical", Color.BLACK, Color.BLACK, 30.0, 0),
    )

    forAll(colorCases) { (desc, oldColor, newColor, threshold, expected) =>
      it(s"handles $desc") {
        val dir = Files.createTempDirectory(s"diff_color_$desc")
        val pdf1 = PdfTestHelpers.createPdfWithTextColor(dir.resolve("c1.pdf"), "Test", oldColor)
        val pdf2 = PdfTestHelpers.createPdfWithTextColor(dir.resolve("c2.pdf"), "Test", newColor)
        val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir, thresholdColor = threshold)

        val result = compareAndExtract(config)
        assert(result.summary.colorDiffCount == expected)
      }
    }

    it("generates color diff images") {
      val dir = Files.createTempDirectory("diff_color_image")
      val pdf1 = PdfTestHelpers.createPdfWithTextColor(dir.resolve("ci1.pdf"), "Test", Color.RED)
      val pdf2 = PdfTestHelpers.createPdfWithTextColor(dir.resolve("ci2.pdf"), "Test", Color.BLUE)
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir, thresholdColor = 30.0)

      val result = compareAndExtract(config)
      val pageDiff = result.pageDiffs.head

      assert(pageDiff.colorImagePath.isDefined)
      assert(Files.exists(dir.resolve(pageDiff.colorImagePath.get)))
    }
  }

  describe("layout differences") {
    it("detects shifts") {
      val dir = Files.createTempDirectory("diff_layout_test")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("l1.pdf"), "Moving Text", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdf(dir.resolve("l2.pdf"), "Moving Text", Color.WHITE, x = 120, y = 120)
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir, thresholdLayout = 5.0)

      val result = compareAndExtract(config)

      assert(result.hasDifferences)
      assert(result.summary.layoutDiffCount > 0)
      val layoutDiffs = result.pageDiffs.flatMap(_.layoutDiffs)
      layoutDiffs.headOption match {
        case Some(diff) =>
          assert(diff.displacement > 20.0)
        case None =>
          fail("Expected layout diffs but found none")
      }
      assert(layoutDiffs.map(_.text) == Seq("M", "o", "v", "i", "n", "g", "T", "e", "x", "t"))
    }

    it("ignores small shifts") {
      val dir = Files.createTempDirectory("diff_layout_threshold")
      val pdf1 = PdfTestHelpers.createPdf(dir.resolve("lt1.pdf"), "Text", Color.WHITE)
      val pdf2 = PdfTestHelpers.createPdf(dir.resolve("lt2.pdf"), "Text", Color.WHITE, x = 101, y = 101)
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir, thresholdLayout = 5.0)

      assert(compareAndExtract(config).summary.layoutDiffCount == 0)
    }
  }

  describe("multi-page and edge cases") {
    it("handles page count differences") {
      val dir = Files.createTempDirectory("diff_pagecount")
      val pdf1 = PdfTestHelpers.createMultiPagePdf(dir.resolve("pc1.pdf"), Seq("Page 1", "Page 2"))
      val pdf2 = PdfTestHelpers.createMultiPagePdf(dir.resolve("pc2.pdf"), Seq("Page 1", "Page 2", "Page 3"))
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir)

      val result = compareAndExtract(config)

      assert(result.summary.totalPages == 3)
      assert(result.hasDifferences)
      result.pageDiffs.find(_.pageNumber == 3) match {
        case Some(page) =>
          page.visualDiff match {
            case Some(vd) => assert(vd.pixelDifferenceRatio == 1.0)
            case None => fail("visualDiff should exist for missing page")
          }
        case None => fail("Page 3 should exist")
      }
    }

    it("reports no differences for identical PDFs") {
      val dir = Files.createTempDirectory("diff_identical")
      val pdf = PdfTestHelpers.createPdf(dir.resolve("same.pdf"), "Exact Match", Color.WHITE)
      val config = Config(oldFile = pdf, newFile = pdf, outputDir = dir)

      val result = compareAndExtract(config)

      assert(!result.hasDifferences)
      assert(result.summary.visualDiffCount == 0)
      assert(result.summary.textDiffCount == 0)
      assert(result.summary.layoutDiffCount == 0)
      assert(result.summary.fontDiffCount == 0)
    }

    it("handles empty PDFs") {
      val dir = Files.createTempDirectory("diff_empty")
      val pdf1 = PdfTestHelpers.createEmptyPdf(dir.resolve("e1.pdf"))
      val pdf2 = PdfTestHelpers.createEmptyPdf(dir.resolve("e2.pdf"))
      val config = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir)

      val result = compareAndExtract(config)

      assert(!result.hasDifferences)
      assert(result.summary.totalPages == 1)
    }
  }

  describe("image format support") {
    it("converts and compares identical JPG images") {
      val dir = Files.createTempDirectory("diff_jpg_identical")
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.jpg"), color = Color.WHITE)
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.jpg"), color = Color.WHITE)
      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)

      val result = compareAndExtract(config)

      assert(!result.hasDifferences)
      assert(result.summary.isImageComparison)
    }

    it("detects visual differences in images") {
      val dir = Files.createTempDirectory("diff_image_diff")
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.png"), color = Color.WHITE)
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.png"), color = Color.LIGHT_GRAY)
      val config = Config(oldFile = img1, newFile = img2, outputDir = dir, thresholdPixel = 0.0)

      val result = compareAndExtract(config)

      assert(result.hasDifferences)
      assert(result.summary.visualDiffCount > 0)
      assert(result.summary.isImageComparison)
    }

    it("supports all image formats") {
      val formats = Table(
        "extension", "jpg", "png", "gif", "bmp", "tif",
      )

      forAll(formats) { ext =>
        val dir = Files.createTempDirectory(s"diff_format_$ext")
        val img1 = ImageTestHelpers.createImage(dir.resolve(s"img1.$ext"), color = Color.WHITE)
        val img2 = ImageTestHelpers.createImage(dir.resolve(s"img2.$ext"), color = Color.WHITE)
        val config = Config(oldFile = img1, newFile = img2, outputDir = dir)

        val result = compareAndExtract(config)

        assert(!result.hasDifferences, s".$ext format failed")
        assert(result.summary.isImageComparison, s".$ext not marked as image comparison")
      }
    }

    it("compares mixed image formats") {
      val dir = Files.createTempDirectory("diff_mixed_formats")
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.jpg"), color = Color.WHITE)
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.png"), color = Color.WHITE)
      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)

      val result = compareAndExtract(config)

      assert(!result.hasDifferences)
      assert(result.summary.isImageComparison)
    }

    it("compares PDF with image file") {
      val dir = Files.createTempDirectory("diff_pdf_vs_image")
      val pdf = PdfTestHelpers.createPdf(dir.resolve("doc.pdf"), "Test", Color.WHITE)
      val img = ImageTestHelpers.createImageWithText(dir.resolve("img.png"), "Test")
      val config = Config(oldFile = pdf, newFile = img, outputDir = dir)

      val result = compareAndExtract(config)

      assert(result.summary.isImageComparison)
    }

    it("does not detect text/layout/font diffs in images") {
      val dir = Files.createTempDirectory("diff_image_no_text")
      val img1 = ImageTestHelpers.createImageWithText(dir.resolve("img1.png"), "Hello World")
      val img2 = ImageTestHelpers.createImageWithText(dir.resolve("img2.png"), "Goodbye World")
      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)

      val result = compareAndExtract(config)

      // Images don't have text layers
      assert(result.summary.textDiffCount == 0)
      assert(result.summary.layoutDiffCount == 0)
      assert(result.summary.fontDiffCount == 0)
      // But visual differences should be detected
      assert(result.summary.visualDiffCount > 0)
    }

    it("detects color differences in images") {
      val dir = Files.createTempDirectory("diff_image_color")
      val img1 = ImageTestHelpers.createImageWithShape(dir.resolve("img1.png"), shapeColor = Color.RED)
      val img2 = ImageTestHelpers.createImageWithShape(dir.resolve("img2.png"), shapeColor = Color.BLUE)
      val config = Config(oldFile = img1, newFile = img2, outputDir = dir, thresholdColor = 30.0)

      val result = compareAndExtract(config)

      assert(result.hasDifferences)
      assert(result.summary.colorDiffCount > 0)
    }

    it("handles uppercase image extensions") {
      val dir = Files.createTempDirectory("diff_uppercase_ext")
      val img1 = ImageTestHelpers.createImage(dir.resolve("IMG1.JPG"), color = Color.WHITE)
      val img2 = ImageTestHelpers.createImage(dir.resolve("IMG2.PNG"), color = Color.WHITE)
      val config = Config(oldFile = img1, newFile = img2, outputDir = dir)

      val result = compareAndExtract(config)

      assert(!result.hasDifferences)
      assert(result.summary.isImageComparison)
    }

    it("sets isImageComparison flag correctly") {
      val dir = Files.createTempDirectory("diff_image_flag")

      // Test with images
      val img1 = ImageTestHelpers.createImage(dir.resolve("img1.jpg"))
      val img2 = ImageTestHelpers.createImage(dir.resolve("img2.jpg"))
      val configImage = Config(oldFile = img1, newFile = img2, outputDir = dir)
      assert(compareAndExtract(configImage).summary.isImageComparison)

      // Test with PDFs
      val pdf1 = PdfTestHelpers.createEmptyPdf(dir.resolve("doc1.pdf"))
      val pdf2 = PdfTestHelpers.createEmptyPdf(dir.resolve("doc2.pdf"))
      val configPdf = Config(oldFile = pdf1, newFile = pdf2, outputDir = dir)
      assert(!compareAndExtract(configPdf).summary.isImageComparison)
    }

    it("generates diff images for image comparisons") {
      val dir = Files.createTempDirectory("diff_image_output")
      val (img1, img2) = ImageTestHelpers.createImagePair(dir.resolve("img1.jpg"), dir.resolve("img2.jpg"))
      val config = Config(oldFile = img1, newFile = img2, outputDir = dir, thresholdPixel = 0.0)

      val result = compareAndExtract(config)
      val pageDiff = result.pageDiffs.head

      assert(pageDiff.oldImagePath.isDefined)
      assert(pageDiff.newImagePath.isDefined)
      assert(pageDiff.diffImagePath.isDefined)

      // Verify image files were created
      assert(Files.exists(dir.resolve(pageDiff.oldImagePath.get)))
      assert(Files.exists(dir.resolve(pageDiff.newImagePath.get)))
      assert(Files.exists(dir.resolve(pageDiff.diffImagePath.get)))
    }
  }

}
