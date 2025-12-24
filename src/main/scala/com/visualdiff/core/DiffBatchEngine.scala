package com.visualdiff.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.models._
import com.visualdiff.report.Reporter
import com.visualdiff.util.FileUtils

/** Engine for batch comparison of multiple file pairs.
  *
  * Discovers file pairs by matching filenames between two directories, then executes comparisons
  * sequentially using the existing DiffEngine.
  */
final class DiffBatchEngine(batchConfig: BatchConfig) extends LazyLogging:

  def compareAll(): BatchResult =
    val startTime = Instant.now()
    logger.info(s"Starting batch comparison: ${batchConfig.dirOld} vs ${batchConfig.dirNew}")

    // 1. Discover file pairs and unmatched files
    val (pairs, unmatchedOld, unmatchedNew) = discoverPairs()
    logger.info(s"Found ${pairs.size} file pair(s) to compare")

    if unmatchedOld.nonEmpty then logger.warn(s"${unmatchedOld.size} file(s) only in OLD directory")
    if unmatchedNew.nonEmpty then logger.warn(s"${unmatchedNew.size} file(s) only in NEW directory")

    if pairs.isEmpty then
      logger.warn("No matching files found")
      return BatchResult(
        Seq.empty,
        BatchSummary(0, 0, 0, 0, 0, 0, Duration.ZERO, unmatchedOld.size, unmatchedNew.size),
        startTime,
        Instant.now(),
        unmatchedOld,
        unmatchedNew,
      )

    // 2. Execute comparisons sequentially
    val results = pairs.zipWithIndex.map { case (pair, index) =>
      comparePair(pair, index + 1, pairs.size)
    }

    // 3. Create batch result
    val endTime = Instant.now()
    val totalDuration = Duration.between(startTime, endTime)
    val summary = createSummary(results, totalDuration, unmatchedOld.size, unmatchedNew.size)

    val batchResult = BatchResult(results, summary, startTime, endTime, unmatchedOld, unmatchedNew)

    logger.info(s"Batch completed in ${totalDuration.toSeconds}s")
    logSummary(summary)

    batchResult

  /** Discovers file pairs by matching filenames between old and new directories */
  private def discoverPairs(): (Seq[BatchPair], Seq[Path], Seq[Path]) =
    val oldFiles = scanDirectory(batchConfig.dirOld)
    val newFiles = scanDirectory(batchConfig.dirNew)

    logger.debug(s"Scanned ${oldFiles.size} old file(s), ${newFiles.size} new file(s)")

    // Match by filename only
    val newFilesByName = newFiles.groupBy(_.getFileName.toString)

    val pairs = oldFiles.flatMap { oldFile =>
      val filename = oldFile.getFileName.toString
      newFilesByName.get(filename).map { matchedNewFiles =>
        val newFile = matchedNewFiles.head
        val relativePath = batchConfig.dirOld.relativize(oldFile).toString
        BatchPair(oldFile, newFile, relativePath)
      }
    }

    // Find unmatched files
    val matchedOldFiles = pairs.map(_.oldFile).toSet
    val matchedNewFiles = pairs.map(_.newFile).toSet

    val unmatchedOld = oldFiles.filterNot(matchedOldFiles.contains)
    val unmatchedNew = newFiles.filterNot(matchedNewFiles.contains)

    (pairs, unmatchedOld, unmatchedNew)

  /** Creates summary statistics from all pair results */
  private def createSummary(
      results: Seq[PairResult],
      totalDuration: Duration,
      unmatchedOldCount: Int,
      unmatchedNewCount: Int,
  ): BatchSummary =
    val successful = results.count(_.isSuccess)
    val successfulWithDiff = results.count(_.hasDifferences)
    val failed = results.count(!_.isSuccess)

    val totalPages = results.flatMap(_.result).map(_.summary.totalPages).sum
    val totalDifferences = results.flatMap(_.result).count(_.hasDifferences)

    BatchSummary(
      results.size, successful, successfulWithDiff, failed, totalPages, totalDifferences, totalDuration,
      unmatchedOldCount, unmatchedNewCount,
    )

  /** Scans directory for supported files (PDF and image formats) */
  private def scanDirectory(dir: Path): Seq[Path] =
    if !Files.exists(dir) then
      logger.warn(s"Directory does not exist: $dir")
      return Seq.empty

    val maxDepth = if batchConfig.recursive then Int.MaxValue else 1

    Files
      .walk(dir, maxDepth)
      .iterator()
      .asScala
      .filter(Files.isRegularFile(_))
      .filter(isSupported)
      .toSeq
      .sorted

  /** Checks if file is supported (PDF or image format from ImageFormat enum) */
  private def isSupported(path: Path): Boolean =
    val filename = path.toString.toLowerCase
    filename.endsWith(".pdf") || ImageFormat.isSupported(filename)

  /** Compares a single file pair and generates its individual report */
  private def comparePair(pair: BatchPair, current: Int, total: Int): PairResult =
    val startTime = Instant.now()
    val pairName = pair.oldFile.getFileName.toString

    logger.info(s"[$current/$total] Comparing: $pairName")

    try
      // Create subdirectory for this pair's output
      val sanitizedName = FileUtils.sanitizeFilename(pairName)
      val pairOutputDir =
        batchConfig.baseConfig.outputDir.resolve(f"pair_$current%03d_$sanitizedName")
      Files.createDirectories(pairOutputDir)

      // Create config for this specific pair
      val pairConfig = batchConfig.baseConfig.copy(
        oldFile = pair.oldFile,
        newFile = pair.newFile,
        outputDir = pairOutputDir,
      )

      // Run comparison using existing DiffEngine
      val engine = new DiffEngine(pairConfig)
      val diffResult = engine.compare()

      // Generate individual report using Reporter
      val reporter = new Reporter(pairConfig)
      reporter.generateReports(diffResult)

      val duration = Duration.between(startTime, Instant.now())
      logger.info(
        s"  ✓ Completed in ${duration.toMillis}ms" +
          (if diffResult.hasDifferences then s" (differences found)" else ""),
      )

      PairResult(pair, Some(diffResult), None, duration, pairOutputDir)

    catch
      case e: Exception =>
        val duration = Duration.between(startTime, Instant.now())
        val errorMsg = s"${e.getClass.getSimpleName}: ${e.getMessage}"

        if batchConfig.continueOnError then
          logger.error(s"  ✗ Failed: $errorMsg")
          PairResult(pair, None, Some(errorMsg), duration, Paths.get(""))
        else
          logger.error(s"  ✗ Failed: $errorMsg - Stopping batch")
          throw e

  /** Creates summary statistics from all pair results */
  private def createSummary(results: Seq[PairResult], totalDuration: Duration): BatchSummary =
    val successful = results.count(_.isSuccess)
    val successfulWithDiff = results.count(_.hasDifferences)
    val failed = results.count(!_.isSuccess)

    val totalPages = results.flatMap(_.result).map(_.summary.totalPages).sum
    val totalDifferences = results.flatMap(_.result).count(_.hasDifferences)

    BatchSummary(
      results.size, successful, successfulWithDiff, failed, totalPages, totalDifferences, totalDuration,
    )

  /** Logs batch summary to console */
  private def logSummary(summary: BatchSummary): Unit =
    val report =
      s"""
        |============================================================
        |BATCH SUMMARY
        |============================================================
        |Total pairs:              ${summary.totalPairs}
        |Successful (no diff):     ${summary.successful - summary.successfulWithDiff}
        |Successful (with diff):   ${summary.successfulWithDiff}
        |Failed:                   ${summary.failed}
        |Total pages compared:     ${summary.totalPages}
        |Unmatched in OLD:         ${summary.unmatchedOldCount}
        |Unmatched in NEW:         ${summary.unmatchedNewCount}
        |Total duration:           ${summary.totalDuration.toSeconds}s
        |============================================================
    """.stripMargin

    logger.info(report)
