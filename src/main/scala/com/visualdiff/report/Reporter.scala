package com.visualdiff.report

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import scala.io.Source.fromResource

import com.visualdiff.models.BatchResult
import com.visualdiff.models.Config
import com.visualdiff.models.DiffResult
import com.visualdiff.models.DiffResult.given
import upickle.default.write

final class Reporter(config: Config):

  /** Generates reports for single-file comparison */
  def generateReports(result: DiffResult): Unit =
    generateJsonReport(result)
    copyStaticAssets(config.outputDir)
    generateHtmlReport(result)

  /** Generates batch comparison report */
  def generateBatchReport(batchResult: BatchResult, outputDir: Path, oldDir: Path, newDir: Path): Unit =
    val html = HtmlComponents.batchReportTemplate(batchResult, oldDir, newDir)
    writeStringToPath(outputDir.resolve("batch_report.html"), html)
    copyStaticAssets(outputDir)

  private def generateJsonReport(result: DiffResult): Unit =
    val json = write(result, indent = 2)
    writeString("diff.json", json)

  private def generateHtmlReport(result: DiffResult): Unit =
    // Use the hasDifferences field from PageDiff to filter pages
    val pageDiffsToShow = result.pageDiffs.filter(_.hasDifferences)

    val fullHtml = HtmlComponents.docTemplate(
      "Visual Diff Report",
      if result.hasDifferences then pageDiffsToShow else Seq.empty,
      result.summary,
      result.summary.isImageComparison,
    )

    writeString("report.html", fullHtml)

  /** Copies static assets (CSS/JS) to the specified output directory */
  private def copyStaticAssets(outputDir: Path): Unit =
    // Explicitly use the class's classloader instead of the thread's context classloader.
    // In parallel mode, worker threads don't have the correct context classloader set,
    // causing fromResource() to fail with FileNotFoundException when trying to load classpath resources.
    val classLoader = getClass.getClassLoader

    val cssContent = fromResource("report/index.css", classLoader).mkString
    writeStringToPath(outputDir.resolve("report.css"), cssContent)

    val jsContent = fromResource("report/index.js", classLoader).mkString
    writeStringToPath(outputDir.resolve("report.js"), jsContent)

  /** Writes string to file relative to config.outputDir */
  private def writeString(filename: String, content: String): Unit =
    writeStringToPath(config.outputDir.resolve(filename), content)

  /** Writes string to absolute path */
  private def writeStringToPath(path: Path, content: String): Unit =
    Files.writeString(
      path,
      content,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
    )
