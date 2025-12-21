package com.visualdiff.util

object FileUtils:

  /** Sanitizes filename for use in directory names and URLs
    *
    * Replaces non-alphanumeric characters (except dots and dashes) with underscores
    * and limits the length to prevent filesystem issues.
    *
    * @param filename the filename to sanitize
    * @param maxLength maximum length of the sanitized filename (default: 50)
    * @return sanitized filename safe for filesystem use
    */
  def sanitizeFilename(filename: String, maxLength: Int = 50): String =
    filename
      .replaceAll("[^a-zA-Z0-9.-]", "_")
      .take(maxLength)
