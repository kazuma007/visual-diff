package com.visualdiff.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

import scala.concurrent._
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import com.visualdiff.models._
import com.visualdiff.report.Reporter
import com.visualdiff.util.FileUtils

/** Engine for batch comparison of multiple file pairs.
  *
  * Discovers file pairs by matching filenames between two directories, then executes comparisons
  * either sequentially or in parallel based on configuration.
  */
final class DiffBatchEngine(batchConfig: BatchConfig) extends LazyLogging:

  // Create ExecutionContext based on Config.parallelism setting
  private given ExecutionContext =
    if batchConfig.enableParallel then
      ExecutionContext.fromExecutorService(
        Executors.newWorkStealingPool(batchConfig.parallelism),
      )
    else ExecutionContext.global

  /** Compares all discovered file pairs and returns batch result with performance metrics */
  def compareAll(): BatchResult =
    val startTime = Instant.now()
    logger.info(s"Starting batch comparison: ${batchConfig.dirOld} vs ${batchConfig.dirNew}")

    // Log execution mode
    if batchConfig.enableParallel then logger.info(s"Parallel mode enabled with ${batchConfig.parallelism} worker(s)")
    else logger.info("Sequential mode enabled")

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

    // 2. Execute comparisons (sequential or parallel based on config)
    val resultsFuture =
      if batchConfig.enableParallel then compareParallel(pairs)
      else Future.successful(compareSequential(pairs))

    val results = Try {
      val timeout = calculateTimeout(pairs)
      Await.result(resultsFuture, timeout)
    } match
      case Success(res) => res
      case Failure(ex: TimeoutException) =>
        logger.error(s"Batch comparison timed out: ${ex.getMessage}")
        throw new RuntimeException("Batch comparison exceeded timeout", ex)
      case Failure(ex) if NonFatal(ex) =>
        logger.error(s"Batch comparison failed: ${ex.getMessage}", ex)
        throw new RuntimeException("Batch comparison failed", ex)
      case Failure(ex) =>
        logger.error(s"Fatal error in batch comparison: ${ex.getMessage}", ex)
        throw ex

    // 3. Create batch result
    val endTime = Instant.now()
    val totalDuration = Duration.between(startTime, endTime)
    val summary = createSummary(results, totalDuration, unmatchedOld.size, unmatchedNew.size)
    val batchResult = BatchResult(results, summary, startTime, endTime, unmatchedOld, unmatchedNew)

    logger.info(s"Batch completed in ${totalDuration.toSeconds}s (mode: ${
        if batchConfig.enableParallel then "parallel" else "sequential"
      })")
    logSummary(summary)
    batchResult

  /** Executes comparisons sequentially (original implementation) */
  private def compareSequential(pairs: Seq[BatchPair]): Seq[PairResult] =
    pairs.zipWithIndex.map { case (pair, index) =>
      comparePair(pair, index + 1, pairs.size)
    }

  /** Executes comparisons in parallel using thread pool */
  private def compareParallel(pairs: Seq[BatchPair])(using ec: ExecutionContext): Future[Seq[PairResult]] =
    val futures = pairs.zipWithIndex.map { case (pair, index) =>
      Future {
        comparePair(pair, index + 1, pairs.size)
      }.recoverWith { case NonFatal(e) =>
        val duration = Duration.ZERO
        val errorMsg = s"${e.getClass.getSimpleName}: ${e.getMessage}"
        logger.error(s"Failed to compare ${pair.oldFile.getFileName}: $errorMsg", e)
        Future.successful(
          PairResult(pair, None, Some(errorMsg), duration, Paths.get("")),
        )
      }
    }

    Future.sequence(futures)

  /** Calculates adaptive timeout based on file count with sensible bounds */
  private def calculateTimeout(pairs: Seq[BatchPair]): FiniteDuration =
    val baseTimeoutPerFile = 90.seconds
    val minimumTimeout = 30.seconds
    val maximumTimeout = 2.hours

    val calculatedTimeout = baseTimeoutPerFile * pairs.size
    minimumTimeout.max(calculatedTimeout.min(maximumTimeout))

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

    // Thread-safe logging with thread info in parallel mode
    val threadInfo =
      if batchConfig.enableParallel then s" [Thread-${Thread.currentThread().threadId()}]"
      else ""

    logger.info(s"[$current/$total]$threadInfo Comparing: $pairName")

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
        s" ✓$threadInfo Completed in ${duration.toMillis}ms" +
          (if diffResult.hasDifferences then s" (differences found)" else ""),
      )

      PairResult(pair, Some(diffResult), None, duration, pairOutputDir)

    catch
      case NonFatal(e) =>
        val duration = Duration.between(startTime, Instant.now())
        val errorMsg = s"${e.getClass.getSimpleName}: ${e.getMessage}"

        if batchConfig.continueOnError then
          logger.error(s" ✗$threadInfo Failed: $errorMsg")
          PairResult(pair, None, Some(errorMsg), duration, Paths.get(""))
        else
          logger.error(s" ✗$threadInfo Failed: $errorMsg - Stopping batch")
          throw e

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
