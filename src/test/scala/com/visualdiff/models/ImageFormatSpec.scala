package com.visualdiff.models

import java.nio.file.Paths

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._

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
      val testCases = Table(
        "filename", "photo.jpg", "image.JPEG", "screenshot.png", "animation.gif", "bitmap.bmp", "scan.tif",
        "document.tiff",
      )

      forAll(testCases) { filename =>
        assert(ImageFormat.isSupported(filename), s"Failed to detect $filename as supported")
      }
    }

    it("rejects non-image files") {
      val testCases = Table(
        "filename", "document.pdf", "text.txt", "data.json", "image.webp", "video.mp4", "photo.jpeg.pdf", // Edge case: ends with .pdf
        "file.doc", "spreadsheet.xlsx", "archive.zip", "script.sh",
      )

      forAll(testCases) { filename =>
        assert(!ImageFormat.isSupported(filename), s"Incorrectly detected $filename as supported")
      }
    }

    it("is case-insensitive for extension detection") {
      val testCases = Table(
        ("filename", "expectedSupport"),
        ("photo.JPG", true),
        ("image.Png", true),
        ("file.GIF", true),
        ("doc.TIF", true),
        ("scan.TIFF", true),
        ("image.BMP", true),
        ("photo.JPEG", true),
        ("file.JpG", true),
        ("doc.PnG", true),
        ("FILE.PDF", false),
        ("DOC.TXT", false),
      )

      forAll(testCases) { (filename, expectedSupport) =>
        assert(ImageFormat.isSupported(filename) == expectedSupport, s"Case-insensitive check failed for $filename")
      }
    }

    it("detects format from path") {
      val testCases = Table(
        ("filename", "expected"),
        ("photo.jpg", Some(ImageFormat.JPEG)),
        ("image.jpeg", Some(ImageFormat.JPEG)),
        ("screen.png", Some(ImageFormat.PNG)),
        ("anim.gif", Some(ImageFormat.GIF)),
        ("bitmap.bmp", Some(ImageFormat.BMP)),
        ("scan.tif", Some(ImageFormat.TIFF)),
        ("doc.tiff", Some(ImageFormat.TIFF)),
        ("document.pdf", None),
        ("file.txt", None),
        ("Photo.JPG", Some(ImageFormat.JPEG)),
        ("IMAGE.PNG", Some(ImageFormat.PNG)),
      )

      forAll(testCases) { (filename, expected) =>
        val path = Paths.get(filename)
        val detected = ImageFormat.fromPath(path)
        assert(detected == expected, s"Failed for $filename: expected $expected, got $detected")
      }
    }

    it("handles paths with directories") {
      val testCases = Table(
        ("path", "expected"),
        ("/home/user/images/photo.jpg", Some(ImageFormat.JPEG)),
        ("C:\\Users\\Documents\\image.png", Some(ImageFormat.PNG)),
        ("./relative/path/file.gif", Some(ImageFormat.GIF)),
        ("../parent/scan.tif", Some(ImageFormat.TIFF)),
        ("/tmp/bitmap.bmp", Some(ImageFormat.BMP)),
        ("/no/extension/file", None),
      )

      forAll(testCases) { (pathStr, expected) =>
        val path = Paths.get(pathStr)
        assert(ImageFormat.fromPath(path) == expected, s"Failed for path: $pathStr")
      }
    }

    it("provides correct display names for each format") {
      val testCases = Table(
        ("format", "expectedName"),
        (ImageFormat.JPEG, "JPG/JPEG"),
        (ImageFormat.PNG, "PNG"),
        (ImageFormat.GIF, "GIF"),
        (ImageFormat.BMP, "BMP"),
        (ImageFormat.TIFF, "TIF/TIFF"),
      )

      forAll(testCases) { (format, expectedName) =>
        assert(format.displayName == expectedName)
      }
    }

    it("provides correct extensions for each format") {
      val testCases = Table(
        ("format", "expectedExtensions"),
        (ImageFormat.JPEG, Seq(".jpg", ".jpeg")),
        (ImageFormat.PNG, Seq(".png")),
        (ImageFormat.GIF, Seq(".gif")),
        (ImageFormat.BMP, Seq(".bmp")),
        (ImageFormat.TIFF, Seq(".tif", ".tiff")),
      )

      forAll(testCases) { (format, expectedExtensions) =>
        assert(format.extensions == expectedExtensions)
      }
    }

    it("lists all formats") {
      val allFormats = ImageFormat.all
      assert(allFormats.size == 5)

      val expectedFormats = Table(
        "format", ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.GIF, ImageFormat.BMP, ImageFormat.TIFF,
      )

      forAll(expectedFormats) { format =>
        assert(allFormats.contains(format), s"Missing format: $format")
      }
    }

    it("handles edge cases in filename detection") {
      val testCases = Table(
        ("description", "filename", "expectedSupport"),
        ("no extension", "file", false),
        ("dot only", ".", false),
        ("extension only", ".jpg", true),
        ("multiple dots", "file.backup.jpg", true),
        ("hidden file", ".hidden.png", true),
        ("ends with dot", "file.jpg.", false),
        ("mixed case middle", "file.JpG", true),
        ("extra dots", "file...png", true),
        ("space before ext", "file .jpg", true),
      )

      forAll(testCases) { (desc, filename, expectedSupport) =>
        assert(ImageFormat.isSupported(filename) == expectedSupport, s"Failed: $desc for $filename")
      }
    }

    it("correctly identifies all supported extensions") {
      val testCases = Table(
        "extension", ".jpg", ".jpeg", ".JPG", ".JPEG", ".png", ".PNG", ".gif", ".GIF", ".bmp", ".BMP", ".tif", ".tiff",
        ".TIF", ".TIFF",
      )

      forAll(testCases) { ext =>
        val filename = s"testfile$ext"
        assert(ImageFormat.isSupported(filename), s"Should support $ext extension")
      }
    }
  }

}
