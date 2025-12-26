package com.visualdiff.helper

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.util.Using

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts

/** Generates test PDF files for benchmarking */
object TestDataGenerator:

  /** Generates PDF file pairs with known differences
    *
    * @param baseDir Base directory for test data
    * @param count Number of file pairs to generate
    * @param pages Number of pages per PDF
    * @return Tuple of (oldDir, newDir) paths
    */
  def generatePdfPairs(
      baseDir: Path = Paths.get("./test_data"),
      count: Int = 10,
      pages: Int = 1,
  ): (Path, Path) =
    val oldDir = baseDir.resolve("old")
    val newDir = baseDir.resolve("new")

    Files.createDirectories(oldDir)
    Files.createDirectories(newDir)

    (1 to count).foreach { i =>
      val filename = f"document_$i%03d.pdf"
      createPdfPair(oldDir.resolve(filename), newDir.resolve(filename), i, pages)
    }

    (oldDir, newDir)

  /** Creates a single PDF file pair with differences
    *
    * @param oldPath Path for old version
    * @param newPath Path for new version
    * @param docNumber Document number (for content)
    * @param pages Number of pages to generate
    */
  private def createPdfPair(oldPath: Path, newPath: Path, docNumber: Int, pages: Int): Unit =
    createPdf(oldPath, docNumber, pages, isNew = false)
    createPdf(newPath, docNumber, pages, isNew = true)

  /** Creates a single PDF document
    *
    * @param path Output path
    * @param docNumber Document number
    * @param pages Number of pages
    * @param isNew If true, includes "NEW" version marker and additional content
    */
  private def createPdf(path: Path, docNumber: Int, pages: Int, isNew: Boolean): Unit =
    Using.resource(new PDDocument()) { doc =>
      (1 to pages).foreach { pageNum =>
        val page = new PDPage(PDRectangle.A4)
        doc.addPage(page)

        Using.resource(new PDPageContentStream(doc, page)) { content =>
          content.beginText()

          // Title
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24)
          content.newLineAtOffset(50, 750)
          content.showText(f"Test Document #$docNumber")

          // Version marker (this is the main difference)
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16)
          content.newLineAtOffset(0, -30)
          content.showText(if isNew then "Version: NEW" else "Version: OLD")

          // Page number
          content.newLineAtOffset(0, -30)
          content.showText(f"Page: $pageNum of $pages")

          // Sample content
          content.newLineAtOffset(0, -60)
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
          content.showText("This is sample content for testing.")
          content.newLineAtOffset(0, -20)
          content.showText("Lorem ipsum dolor sit amet, consectetur adipiscing elit.")

          // Additional content in NEW version
          if isNew then
            content.newLineAtOffset(0, -40)
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14)
            content.showText("UPDATED CONTENT")

          content.endText()
        }
      }
      doc.save(path.toFile)
    }

  /** Recursively deletes a directory and all its contents
    *
    * @param baseDir Directory to delete
    */
  def cleanup(baseDir: Path): Unit =
    if Files.exists(baseDir) then
      Files
        .walk(baseDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(file => Files.delete(file))
