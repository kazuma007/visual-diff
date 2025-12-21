package com.visualdiff.models

import java.nio.file.Paths

import org.scalatest.funspec.AnyFunSpec

class ImageFormatSpec extends AnyFunSpec {

  describe("ImageFormat enum") {

    it("provides formatted display names") {
      val displayNames = ImageFormat.displayNames
      assert(displayNames.contains("JPG/JPEG"))
      assert(displayNames.contains("PNG"))
      assert(displayNames.contains("GIF"))
      assert(displayNames.contains("BMP"))
      assert(displayNames.contains("TIF/TIFF"))
      // Verify comma-separated format
      assert(displayNames.split(",").map(_.trim).length == 5)
    }

    it("detects supported image files correctly") {
      val supportedFiles = Seq(
        "photo.jpg", "image.JPEG", "screenshot.png", "animation.gif", "bitmap.bmp", "scan.tif", "document.tiff",
      )

      supportedFiles.foreach { filename =>
        assert(ImageFormat.isSupported(filename), s"Failed to detect $filename as supported")
      }
    }

    it("rejects non-image files") {
      val unsupportedFiles = Seq(
        "document.pdf", "text.txt", "data.json", "image.webp", "video.mp4", "photo.jpeg.pdf", // Edge case: ends with .pdf
      )

      unsupportedFiles.foreach { filename =>
        assert(!ImageFormat.isSupported(filename), s"Incorrectly detected $filename as supported")
      }
    }

    it("is case-insensitive for extension detection") {
      assert(ImageFormat.isSupported("photo.JPG"))
      assert(ImageFormat.isSupported("image.Png"))
      assert(ImageFormat.isSupported("file.GIF"))
      assert(ImageFormat.isSupported("doc.TIF"))
    }

    it("detects format from path") {
      val testCases = Seq(
        ("photo.jpg", Some(ImageFormat.JPEG)),
        ("image.jpeg", Some(ImageFormat.JPEG)),
        ("screen.png", Some(ImageFormat.PNG)),
        ("anim.gif", Some(ImageFormat.GIF)),
        ("bitmap.bmp", Some(ImageFormat.BMP)),
        ("scan.tif", Some(ImageFormat.TIFF)),
        ("doc.tiff", Some(ImageFormat.TIFF)),
        ("document.pdf", None),
        ("file.txt", None),
      )

      testCases.foreach { case (filename, expected) =>
        val path = Paths.get(filename)
        val detected = ImageFormat.fromPath(path)
        assert(detected == expected, s"Failed for $filename: expected $expected, got $detected")
      }
    }

    it("handles paths with directories") {
      val path = Paths.get("/home/user/images/photo.jpg")
      assert(ImageFormat.fromPath(path).contains(ImageFormat.JPEG))
    }

    it("provides correct display names for each format") {
      assert(ImageFormat.JPEG.displayName == "JPG/JPEG")
      assert(ImageFormat.PNG.displayName == "PNG")
      assert(ImageFormat.GIF.displayName == "GIF")
      assert(ImageFormat.BMP.displayName == "BMP")
      assert(ImageFormat.TIFF.displayName == "TIF/TIFF")
    }

    it("provides correct extensions for each format") {
      assert(ImageFormat.JPEG.extensions == Seq(".jpg", ".jpeg"))
      assert(ImageFormat.PNG.extensions == Seq(".png"))
      assert(ImageFormat.GIF.extensions == Seq(".gif"))
      assert(ImageFormat.BMP.extensions == Seq(".bmp"))
      assert(ImageFormat.TIFF.extensions == Seq(".tif", ".tiff"))
    }

    it("lists all formats") {
      val allFormats = ImageFormat.all
      assert(allFormats.size == 5)
      assert(allFormats.contains(ImageFormat.JPEG))
      assert(allFormats.contains(ImageFormat.PNG))
      assert(allFormats.contains(ImageFormat.GIF))
      assert(allFormats.contains(ImageFormat.BMP))
      assert(allFormats.contains(ImageFormat.TIFF))
    }
  }

}
