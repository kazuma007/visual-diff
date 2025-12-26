package com.visualdiff.report

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import scala.io.Source
import scala.util.Failure
import scala.util.Try
import scala.util.Using
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.models.BatchResult
import com.visualdiff.models.Config
import com.visualdiff.models.DiffResult
import com.visualdiff.models.DiffResult.given
import upickle.default.write

final class Reporter(config: Config) extends LazyLogging:

  /** Generates reports for single-file comparison */
  def generateReports(result: DiffResult): Try[Unit] =
    for
      _ <- generateJsonReport(result)
      _ <- copyStaticAssets(config.outputDir)
      _ <- generateHtmlReport(result)
    yield ()

  /** Generates batch comparison report */
  def generateBatchReport(batchResult: BatchResult, outputDir: Path, oldDir: Path, newDir: Path): Try[Unit] =
    for
      html <- Try(HtmlComponents.batchReportTemplate(batchResult, oldDir, newDir))
      _ <- writeStringToPath(outputDir.resolve("batch_report.html"), html)
      _ <- copyStaticAssets(outputDir)
    yield ()

  private def generateJsonReport(result: DiffResult): Try[Unit] =
    Try {
      val json = write(result, indent = 2)
      writeString("diff.json", json)
    }.flatten.recoverWith { case NonFatal(e) =>
      logger.error(s"Failed to generate JSON report: ${e.getMessage}", e)
      Failure(e)
    }

  private def generateHtmlReport(result: DiffResult): Try[Unit] =
    Try {
      val pageDiffsToShow = result.pageDiffs.filter(_.hasDifferences)
      val fullHtml = HtmlComponents.docTemplate(
        "Visual Diff Report",
        if result.hasDifferences then pageDiffsToShow else Seq.empty,
        result.summary,
        result.summary.isImageComparison,
      )
      writeString("report.html", fullHtml)
    }.flatten.recoverWith { case NonFatal(e) =>
      logger.error(s"Failed to generate HTML report: ${e.getMessage}", e)
      Failure(e)
    }

  /** Copies static assets (CSS/JS) to the specified output directory using scala.util.Using */
  private def copyStaticAssets(outputDir: Path): Try[Unit] =
    val classLoader = getClass.getClassLoader

    val copyAssets = for
      _ <- Using(Source.fromResource("report/index.css", classLoader)) { source =>
        val cssContent = source.mkString
        writeStringToPath(outputDir.resolve("report.css"), cssContent)
      }.flatten
      _ <- Using(Source.fromResource("report/index.js", classLoader)) { source =>
        val jsContent = source.mkString
        writeStringToPath(outputDir.resolve("report.js"), jsContent)
      }.flatten
    yield ()

    copyAssets.recoverWith { case NonFatal(e) =>
      logger.error(s"Failed to copy static assets: ${e.getMessage}", e)
      Failure(e)
    }

  /** Writes string to file relative to config.outputDir */
  private def writeString(filename: String, content: String): Try[Unit] =
    writeStringToPath(config.outputDir.resolve(filename), content)

  /** Writes string to absolute path */
  private def writeStringToPath(path: Path, content: String): Try[Unit] =
    Try {
      Files.writeString(
        path,
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
      )
      ()
    }.recoverWith { case NonFatal(e) =>
      logger.error(s"Failed to write file $path: ${e.getMessage}", e)
      Failure(e)
    }
