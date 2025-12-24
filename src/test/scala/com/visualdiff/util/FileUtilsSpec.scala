package com.visualdiff.util

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._

class FileUtilsSpec extends AnyFunSpec {

  describe("FileUtils.sanitizeFilename") {

    it("replaces special characters with underscores") {
      val testCases = Table(
        ("description", "input", "expected"),
        ("spaces", "file with spaces.pdf", "file_with_spaces.pdf"),
        ("special chars", "file@#$%^&*.pdf", "file_______.pdf"),
        ("brackets", "file[test](2024).pdf", "file_test__2024_.pdf"),
        ("slashes", "path/to/file.pdf", "path_to_file.pdf"),
        ("backslashes", "path\\to\\file.pdf", "path_to_file.pdf"),
        ("colons", "file:name:test.pdf", "file_name_test.pdf"),
        ("semicolons", "file;name;test.pdf", "file_name_test.pdf"),
        ("quotes", "file\"name'test.pdf", "file_name_test.pdf"),
        ("pipes", "file|name|test.pdf", "file_name_test.pdf"),
        ("question marks", "file?name?test.pdf", "file_name_test.pdf"),
        ("asterisks", "file*name*test.pdf", "file_name_test.pdf"),
        ("less/greater than", "file<name>test.pdf", "file_name_test.pdf"),
        ("ampersands", "file&name&test.pdf", "file_name_test.pdf"),
        ("percent", "file%20name.pdf", "file_20name.pdf"),
        ("equals", "file=name=test.pdf", "file_name_test.pdf"),
        ("plus", "file+name+test.pdf", "file_name_test.pdf"),
        ("tilde", "file~name~test.pdf", "file_name_test.pdf"),
        ("backtick", "file`name`test.pdf", "file_name_test.pdf"),
        ("exclamation", "file!name!test.pdf", "file_name_test.pdf"),
        ("at symbol", "file@example.com.pdf", "file_example.com.pdf"),
        ("hash", "file#123#test.pdf", "file_123_test.pdf"),
        ("dollar", "file$price$test.pdf", "file_price_test.pdf"),
        ("caret", "file^name^test.pdf", "file_name_test.pdf"),
        ("parentheses", "file(2024).pdf", "file_2024_.pdf"),
        ("curly braces", "file{name}.pdf", "file_name_.pdf"),
        ("square brackets", "file[name].pdf", "file_name_.pdf"),
        ("mixed special", "doc@2024#test!.pdf", "doc_2024_test_.pdf"),
      )

      forAll(testCases) { (desc, input, expected) =>
        assert(FileUtils.sanitizeFilename(input) == expected, s"Failed: $desc")
      }
    }

    it("preserves valid characters") {
      val testCases = Table(
        ("description", "input", "expected"),
        ("alphanumeric", "file123.pdf", "file123.pdf"),
        ("dots", "file.name.test.pdf", "file.name.test.pdf"),
        ("hyphens", "file-name-test.pdf", "file-name-test.pdf"),
        ("underscores", "file_name_test.pdf", "file_name_test.pdf"),
        ("uppercase", "FILE.PDF", "FILE.PDF"),
        ("lowercase", "file.pdf", "file.pdf"),
        ("mixed case", "FileNameTest.pdf", "FileNameTest.pdf"),
        ("numbers only", "123456.pdf", "123456.pdf"),
        ("dots and hyphens", "file-1.0.pdf", "file-1.0.pdf"),
        ("complex valid", "my-file_v2.1.pdf", "my-file_v2.1.pdf"),
      )

      forAll(testCases) { (desc, input, expected) =>
        assert(FileUtils.sanitizeFilename(input) == expected, s"Failed: $desc")
      }
    }

    it("truncates long filenames") {
      val testCases = Table(
        ("description", "input", "maxLength", "expectedLength"),
        ("default max", "a" * 100 + ".pdf", 50, 50),
        ("custom max 20", "a" * 50 + ".pdf", 20, 20),
        ("custom max 10", "verylongfilename.pdf", 10, 10),
        ("exactly at limit", "a" * 50 + ".pdf", 54, 54),
        ("below limit", "short.pdf", 50, 9),
        ("custom max 30", "a" * 100 + ".pdf", 30, 30),
        ("max 5", "longfilename.pdf", 5, 5),
      )

      forAll(testCases) { (desc, input, maxLength, expectedLength) =>
        val result = FileUtils.sanitizeFilename(input, maxLength)
        assert(result.length == expectedLength, s"Failed: $desc (got ${result.length}, expected $expectedLength)")
      }
    }

    it("handles edge cases") {
      val testCases = Table(
        ("description", "input", "expected"),
        ("empty string", "", ""),
        ("only special chars", "@#$%^&*()", "_________"),
        ("only spaces", "     ", "_____"),
        ("single char", "a", "a"),
        ("single special", "@", "_"),
        ("unicode chars", "file_世界.pdf", "file___.pdf"),
        ("emoji", "file_😀_test.pdf", "file___test.pdf"),
        ("newline", "file\nname.pdf", "file_name.pdf"),
        ("tab", "file\tname.pdf", "file_name.pdf"),
        ("carriage return", "file\rname.pdf", "file_name.pdf"),
        ("multiple dots", "file...name.pdf", "file...name.pdf"),
        ("leading dot", ".hiddenfile.pdf", ".hiddenfile.pdf"),
        ("trailing dot", "filename.pdf.", "filename.pdf."),
        ("only dot", ".", "."),
        ("only dash", "-", "-"),
        ("only underscore", "_", "_"),
        ("mixed whitespace", "file \t\n name.pdf", "file____name.pdf"),
      )

      forAll(testCases) { (desc, input, expected) =>
        assert(FileUtils.sanitizeFilename(input) == expected, s"Failed: $desc")
      }
    }

    it("handles truncation with special characters") {
      val testCases = Table(
        ("description", "input", "maxLength", "expected"),
        ("truncate with spaces", "a" * 60 + " spaces.pdf", 50, "a" * 50),
        ("truncate with specials", "file@@@" + "a" * 50 + ".pdf", 50, "file___" + "a" * 43),
        ("truncate preserves valid", "valid-name-" + "a" * 50 + ".pdf", 50, "valid-name-" + "a" * 39),
      )

      forAll(testCases) { (desc, input, maxLength, expected) =>
        val result = FileUtils.sanitizeFilename(input, maxLength)
        assert(result == expected, s"Failed: $desc")
      }
    }

    it("handles realistic filenames") {
      val testCases = Table(
        ("description", "input", "expected"),
        ("versioned", "document_v1.2.3.pdf", "document_v1.2.3.pdf"),
        ("dated", "report_2024-12-21.pdf", "report_2024-12-21.pdf"),
        ("timestamped", "backup_2024-12-21_14:30:45.pdf", "backup_2024-12-21_14_30_45.pdf"),
        ("windows path", "C:\\Users\\John\\Documents\\file.pdf", "C__Users_John_Documents_file.pdf"),
        ("unix path", "/home/user/documents/file.pdf", "_home_user_documents_file.pdf"),
        ("url encoded", "file%20with%20spaces.pdf", "file_20with_20spaces.pdf"),
        ("email style", "document@company.com.pdf", "document_company.com.pdf"),
        ("compressed", "archive.tar.gz.pdf", "archive.tar.gz.pdf"),
        ("multiple extensions", "file.backup.old.pdf", "file.backup.old.pdf"),
        ("version control", "file (copy 1).pdf", "file__copy_1_.pdf"),
      )

      forAll(testCases) { (desc, input, expected) =>
        assert(FileUtils.sanitizeFilename(input) == expected, s"Failed: $desc")
      }
    }

    it("is idempotent for already sanitized names") {
      val testCases = Table(
        "filename", "clean-file.pdf", "file_name_123.pdf", "document-v1.0.pdf", "report.final.pdf", "simple.pdf",
      )

      forAll(testCases) { filename =>
        val sanitized = FileUtils.sanitizeFilename(filename)
        val doubleSanitized = FileUtils.sanitizeFilename(sanitized)
        assert(sanitized == doubleSanitized, "Sanitization should be idempotent")
      }
    }
  }

}
