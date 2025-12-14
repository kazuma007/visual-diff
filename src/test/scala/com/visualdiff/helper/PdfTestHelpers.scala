package com.visualdiff.helper

import java.awt.Color
import java.nio.file.Path

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts

object PdfTestHelpers {

  def createPdf(path: Path, text: String, bgColor: Color, x: Float = 100, y: Float = 100): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.setNonStrokingColor(bgColor)
    cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
    cs.fill()
    cs.setNonStrokingColor(Color.BLACK)
    cs.beginText()
    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
    cs.newLineAtOffset(x, y)
    cs.showText(text)
    cs.endText()
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createPdfWithFont(
      path: Path,
      text: String,
      fontName: Standard14Fonts.FontName,
      x: Float = 100,
      y: Float = 100,
  ): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.setNonStrokingColor(Color.WHITE)
    cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
    cs.fill()
    cs.setNonStrokingColor(Color.BLACK)
    cs.beginText()
    cs.setFont(new PDType1Font(fontName), 12)
    cs.newLineAtOffset(x, y)
    cs.showText(text)
    cs.endText()
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createPdfWithText(path: Path, text: String, x: Float = 100, y: Float = 100): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.beginText()
    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
    cs.newLineAtOffset(x, y)
    cs.showText(text)
    cs.endText()
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createPdfWithTextColor(path: Path, text: String, textColor: Color, x: Float = 100, y: Float = 100): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.setNonStrokingColor(Color.WHITE)
    cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
    cs.fill()
    cs.setNonStrokingColor(textColor)
    cs.beginText()
    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 48)
    cs.newLineAtOffset(x, y)
    cs.showText(text)
    cs.endText()
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createPdfWithFontAndColor(
      path: Path,
      text: String,
      fontName: Standard14Fonts.FontName,
      textColor: Color,
      x: Float = 100,
      y: Float = 100,
  ): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.setNonStrokingColor(Color.WHITE)
    cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
    cs.fill()
    cs.setNonStrokingColor(textColor)
    cs.beginText()
    cs.setFont(new PDType1Font(fontName), 48)
    cs.newLineAtOffset(x, y)
    cs.showText(text)
    cs.endText()
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createPdfWithColor(path: Path, text: String, textColor: Color, x: Float, y: Float): Path =
    createPdfWithFontAndColor(path, text, Standard14Fonts.FontName.HELVETICA, textColor, x, y)

  def createPdfWithMultipleFonts(path: Path, texts: Seq[(String, Standard14Fonts.FontName)]): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.setNonStrokingColor(Color.WHITE)
    cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
    cs.fill()
    cs.setNonStrokingColor(Color.BLACK)
    var yOffset = 100f
    texts.foreach { case (text, fontName) =>
      cs.beginText()
      cs.setFont(new PDType1Font(fontName), 12)
      cs.newLineAtOffset(100, yOffset)
      cs.showText(text)
      cs.endText()
      yOffset += 20
    }
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createPdfWithMixedColors(path: Path, texts: Seq[(String, Color)]): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.setNonStrokingColor(Color.WHITE)
    cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
    cs.fill()
    var xOffset = 100f
    texts.foreach { case (text, color) =>
      cs.setNonStrokingColor(color)
      cs.beginText()
      cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 48)
      cs.newLineAtOffset(xOffset, 100)
      cs.showText(text)
      cs.endText()
      xOffset += text.length * 30
    }
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createMultiTextPdf(path: Path, texts: Seq[(String, Float, Float)]): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    texts.foreach { case (text, x, y) =>
      cs.beginText()
      cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
      cs.newLineAtOffset(x, y)
      cs.showText(text)
      cs.endText()
    }
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createMultiPagePdf(path: Path, texts: Seq[String]): Path = {
    val doc = new PDDocument()
    texts.foreach { text =>
      val page = new PDPage()
      doc.addPage(page)
      val cs = new PDPageContentStream(doc, page)
      cs.setNonStrokingColor(Color.WHITE)
      cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
      cs.fill()
      cs.setNonStrokingColor(Color.BLACK)
      cs.beginText()
      cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
      cs.newLineAtOffset(100, 100)
      cs.showText(text)
      cs.endText()
      cs.close()
    }
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createPdfWithDot(
      path: Path,
      text: String,
      bgColor: Color,
      x: Float = 100,
      y: Float = 100,
      dotColor: Color = Color.BLACK,
  ): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    val cs = new PDPageContentStream(doc, page)
    cs.setNonStrokingColor(bgColor)
    cs.addRect(0, 0, page.getMediaBox.getWidth, page.getMediaBox.getHeight)
    cs.fill()
    cs.setNonStrokingColor(dotColor)
    cs.addRect(10, 10, 2, 2)
    cs.fill()
    cs.setNonStrokingColor(Color.BLACK)
    cs.beginText()
    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
    cs.newLineAtOffset(x, y)
    cs.showText(text)
    cs.endText()
    cs.close()
    doc.save(path.toFile)
    doc.close()
    path
  }

  def createEmptyPdf(path: Path): Path = {
    val doc = new PDDocument()
    val page = new PDPage()
    doc.addPage(page)
    doc.save(path.toFile)
    doc.close()
    path
  }

}
