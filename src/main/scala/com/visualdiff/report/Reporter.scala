package com.visualdiff.report

import java.nio.file.Files
import java.nio.file.StandardOpenOption

import scala.io.Source.fromResource

import com.visualdiff.cli.Config
import com.visualdiff.models.DiffResult
import com.visualdiff.models.DiffResult.given
import upickle.default.write

final class Reporter(config: Config):

  def generateReports(result: DiffResult): Unit =
    generateJsonReport(result)
    copyStaticAssets()
    generateHtmlReport(result)

  private def generateJsonReport(result: DiffResult): Unit =
    val json = write(result, indent = 2)
    writeString("diff.json", json)

  private def copyStaticAssets(): Unit =
    // Copy CSS file
    val cssContent = fromResource("report/index.css").mkString
    writeString("report.css", cssContent)

    // Copy JS file
    val jsContent = fromResource("report/index.js").mkString
    writeString("report.js", jsContent)

  private def generateHtmlReport(result: DiffResult): Unit =
    val pageDiffsToShow = result.pageDiffs.filter(p =>
      p.visualDiff.exists(_.pixelDifferenceRatio > config.thresholdPixel) ||
        p.colorDiffs.nonEmpty ||
        p.textDiffs.nonEmpty ||
        p.layoutDiffs.nonEmpty ||
        p.fontDiffs.nonEmpty ||
        !p.existsInOld ||
        !p.existsInNew,
    )

    val fullHtml = HtmlComponents.docTemplate(
      "Visual Diff Report",
      if result.hasDifferences then pageDiffsToShow else Seq.empty,
      result.summary,
      result.isImageComparison,
    )

    writeString("report.html", fullHtml)

  private def writeString(filename: String, content: String): Unit =
    Files.writeString(
      config.outputDir.resolve(filename),
      content,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
    )
