package com.visualdiff.helper

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

/** Helper object for creating test image files in various formats */
object ImageTestHelpers {

  /** Creates a simple solid color image */
  def createImage(
      path: Path,
      width: Int = 200,
      height: Int = 200,
      color: Color = Color.WHITE,
  ): Path = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(color)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()

    val format = getFormatFromPath(path)
    ImageIO.write(image, format, path.toFile)
    path
  }

  /** Creates an image with text rendered on it */
  def createImageWithText(
      path: Path,
      text: String,
      width: Int = 400,
      height: Int = 200,
      bgColor: Color = Color.WHITE,
      textColor: Color = Color.BLACK,
  ): Path = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()

    // Background
    graphics.setColor(bgColor)
    graphics.fillRect(0, 0, width, height)

    // Text
    graphics.setColor(textColor)
    graphics.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 24))
    graphics.drawString(text, 50, 100)
    graphics.dispose()

    val format = getFormatFromPath(path)
    ImageIO.write(image, format, path.toFile)
    path
  }

  /** Creates an image with a rectangle/shape */
  def createImageWithShape(
      path: Path,
      width: Int = 200,
      height: Int = 200,
      bgColor: Color = Color.WHITE,
      shapeColor: Color = Color.RED,
  ): Path = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()

    // Background
    graphics.setColor(bgColor)
    graphics.fillRect(0, 0, width, height)

    // Shape - centered rectangle
    graphics.setColor(shapeColor)
    graphics.fillRect(50, 50, 100, 100)
    graphics.dispose()

    val format = getFormatFromPath(path)
    ImageIO.write(image, format, path.toFile)
    path
  }

  /** Creates an image with gradient colors */
  def createImageWithGradient(
      path: Path,
      width: Int = 200,
      height: Int = 200,
  ): Path = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    for (y <- 0 until height) {
      val colorValue = 255 * y / height
      val color = new Color(colorValue, colorValue, colorValue)
      for (x <- 0 until width)
        image.setRGB(x, y, color.getRGB)
    }

    val format = getFormatFromPath(path)
    ImageIO.write(image, format, path.toFile)
    path
  }

  /** Creates two images with slight differences for testing visual diff */
  def createImagePair(
      path1: Path,
      path2: Path,
      addDifference: Boolean = true,
  ): (Path, Path) = {
    // Create first image
    val img1 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
    val g1 = img1.createGraphics()
    g1.setColor(Color.WHITE)
    g1.fillRect(0, 0, 200, 200)
    g1.setColor(Color.BLUE)
    g1.fillRect(50, 50, 100, 100)
    g1.dispose()

    // Create second image (identical or with difference)
    val img2 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
    val g2 = img2.createGraphics()
    g2.setColor(Color.WHITE)
    g2.fillRect(0, 0, 200, 200)
    g2.setColor(if (addDifference) Color.RED else Color.BLUE)
    g2.fillRect(50, 50, 100, 100)
    g2.dispose()

    val format1 = getFormatFromPath(path1)
    val format2 = getFormatFromPath(path2)
    ImageIO.write(img1, format1, path1.toFile)
    ImageIO.write(img2, format2, path2.toFile)

    (path1, path2)
  }

  /** Extracts image format from file path */
  private def getFormatFromPath(path: Path): String = {
    val filename = path.getFileName.toString.toLowerCase
    if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) "jpg"
    else if (filename.endsWith(".png")) "png"
    else if (filename.endsWith(".gif")) "gif"
    else if (filename.endsWith(".bmp")) "bmp"
    else if (filename.endsWith(".tif") || filename.endsWith(".tiff")) "tiff"
    else "png" // default
  }

}
