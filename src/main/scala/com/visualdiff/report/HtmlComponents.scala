package com.visualdiff.report

import com.visualdiff.models._
import scalatags.Text.all._
import scalatags.Text.tags2.details

/** HtmlComponents provides utility functions to generate HTML reports for visual diffs between PDFs. */
object HtmlComponents:

  def docTemplate(
      titleText: String,
      pageDiffs: Seq[PageDiff],
      summaryData: DiffSummary,
      hasImageInput: Boolean,
  ): String =
    "<!DOCTYPE html>" + html(lang := "en")(
      head(
        meta(charset := "UTF-8"),
        meta(name    := "viewport", attr("content") := "width=device-width, initial-scale=1.0"),
        tag("title")(titleText),
        link(rel := "stylesheet", href := "report.css"),
      ),
      body(
        div(cls := "header")(
          div(cls := "header-content")(
            h1("📊 " + titleText),
            div(cls := "header-controls")(
              button(cls := "theme-toggle", onclick := "toggleTheme()")("🌙 Dark Mode"),
            ),
          ),
        ),
        summaryBar(summaryData),
        renderImageFormatNotice(hasImageInput),
        div(cls := "main-container")(
          sidebar(pageDiffs, summaryData),
          div(cls := "content")(
            quickActions(),
            if (pageDiffs.isEmpty) noContentMessage() else pageDiffs.map(renderPageSection),
          ),
        ),
        script(src := "report.js"),
      ),
    ).render

  private def summaryBar(s: DiffSummary): Frag =
    div(cls := "summary-bar")(
      div(cls := "summary-content")(
        summaryItem(s.totalPages.toString, "Total Pages"),
        summaryItem(s.pagesWithDiff.toString, "Pages w/ Diff"),
        summaryItem(s.visualDiffCount.toString, "Visual Diffs"),
        summaryItem(s.colorDiffCount.toString, "Color Diffs"),
        summaryItem(s.textDiffCount.toString, "Text Diffs"),
        summaryItem(s.layoutDiffCount.toString, "Layout Diffs"),
        summaryItem(s.fontDiffCount.toString, "Font Diffs"),
      ),
    )

  /** Renders a global notice when image files are being compared instead of PDFs */
  private def renderImageFormatNotice(hasImageInput: Boolean): Frag =
    if hasImageInput then
      div(cls := "image-format-notice")(
        div(cls := "notice-header")(
          span(cls := "notice-icon")("⚠️"),
          span("Image Format Detected"),
        ),
        p(style := "margin: 12px 0 8px 0; color: var(--text-secondary);")(
          s"One or both input files are images (${ImageFormat.displayNames}). These have been converted to PDF for comparison.",
        ),
        ul(cls := "notice-list")(
          li(strong("Available:"), " Visual differences and Color differences"),
          li(
            strong("Not Available:"),
            " Text, Layout, and Font analysis require native PDF files with embedded text layers",
          ),
        ),
        details(cls := "notice-details", style := "margin-top: 8px;")(
          tag("summary")("💡 Why can't text be detected?"),
          p(
            "Image files contain only pixels, not searchable text. ",
            "To enable text/layout/font analysis, convert your documents to PDF format first, ",
            "or use source files that already have text layers (e.g., PDFs created from Word/LaTeX).",
          ),
        ),
      )
    else frag()

  private def summaryItem(value: String, label: String): Frag =
    div(cls := "summary-item")(
      strong(value),
      span(label),
    )

  private def sidebar(pageDiffs: Seq[PageDiff], summaryData: DiffSummary): Frag =
    div(cls := "sidebar")(
      h3("Pages with Differences"),
      pageDiffs.zipWithIndex.map { case (pd, idx) =>
        div(
          cls     := s"page-nav-item${if (idx == 0) " active" else ""}",
          onclick := s"scrollToPage(${pd.pageNumber})",
        )(
          span(s"📄 Page ${pd.pageNumber}"),
        )
      },
      div(cls := "filters")(
        h3("Filter Differences"),
        div(cls := "filter-group")(
          filterCheckbox("visual", "Visual", summaryData.visualDiffCount),
          filterCheckbox("color", "Color", summaryData.colorDiffCount),
          filterCheckbox("text", "Text", summaryData.textDiffCount),
          filterCheckbox("layout", "Layout", summaryData.layoutDiffCount),
          filterCheckbox("font", "Font", summaryData.fontDiffCount),
        ),
      ),
    )

  private def filterCheckbox(id: String, label: String, count: Int): Frag =
    div(cls := "filter-checkbox")(
      input(tpe := "checkbox", attr("id") := s"filter-$id", checked, onchange := "applyFilters()"),
      tag("label")(attr("for") := s"filter-$id")(
        label,
        span(cls := "filter-badge")(count.toString),
      ),
    )

  private def quickActions(): Frag =
    div(cls := "quick-actions")(
      button(cls := "action-btn", onclick := "jumpToFirstDiff()")("⬆️ First Difference"),
      button(cls := "action-btn", onclick := "toggleAllSections()")("📂 Expand All"),
      button(cls := "action-btn", onclick := "exportReport()")("💾 Export JSON"),
      button(cls := "action-btn", onclick := "printReport()")("🖨️ Print Report"),
    )

  private def noContentMessage(): Frag =
    div(cls := "no-diff")("✅ No differences found between the PDFs.")

  private def renderPageSection(pd: PageDiff): Frag =
    div(cls := "page-section", id := s"page-${pd.pageNumber}")(
      div(cls := "page-header")(
        h2(s"Page ${pd.pageNumber}"),
      ),
      renderPageExistenceWarning(pd),
      renderCascadingNotice(pd.suppressedDiffs),
      renderFontCategory(pd.fontDiffs),
      renderVisualCategory(pd.visualDiff, pd.oldImagePath, pd.newImagePath, pd.diffImagePath),
      renderTextCategory(pd.textDiffs),
      renderLayoutCategory(pd.layoutDiffs),
      renderColorCategory(pd.colorDiffs, pd.colorImagePath),
    )

  private def renderPageExistenceWarning(pd: PageDiff): Frag =
    if (!pd.existsInOld && pd.existsInNew) {
      div(cls := "page-warning added")(
        p(strong("ℹ️ Page Only Exists in NEW File")),
        p("This page is present in the new file but not in the old file."),
      )
    } else if (pd.existsInOld && !pd.existsInNew) {
      div(cls := "page-warning removed")(
        p(strong("ℹ️ Page Only Exists in OLD file")),
        p("This page was present in the old file but is missing in the new file."),
      )
    } else {
      frag()
    }

  private def renderFontCategory(diffs: Seq[FontDiff]): Frag =
    if (diffs.isEmpty)
      div(cls := "diff-category no-diff-category", attr("data-type") := "font")(
        div(cls := "category-header-static")(
          div(cls := "category-title")(
            span("🔤 Font Differences"),
            span(cls := "count-badge")("0"),
          ),
        ),
        div(cls := "category-content-static")(
          p(cls := "no-diff-message")("✅ No font differences found."),
        ),
      )
    else
      div(cls := "diff-category", attr("data-type") := "font")(
        div(cls := "category-header", onclick := "toggleCategory(this)")(
          div(cls := "category-title")(
            span("🔤 Font Differences"),
            span(cls := "count-badge")(diffs.size.toString),
          ),
          span(cls := "expand-icon")("▼"),
        ),
        div(cls := "category-content expanded")(
          div(cls := "category-body")(
            div(cls := "scrollable-list")(
              diffs.map(renderFontDiff),
            ),
          ),
        ),
      )

  private def renderFontDiff(d: FontDiff): Frag =
    div(cls := "diff-item font")(
      strong(d.diffType.toString.capitalize + ": "),
      renderFontInfo(d),
      d.affectedText.map { text =>
        frag(
          br,
          tag("small")(style := "color: var(--text-secondary)")(s"Affects: '${text.take(60)}'"),
        )
      }.getOrElse(frag()),
    )

  private def renderFontInfo(fontDiff: FontDiff): Frag =
    fontDiff.diffType match
      case DiffType.Added =>
        fontDiff.newFont.map { f =>
          span(s"Font '${f.fontName}' added")
        }.getOrElse(span("Unknown font added"))
      case DiffType.Changed =>
        (fontDiff.oldFont, fontDiff.newFont) match {
          case (Some(oldFont), Some(newFont)) =>
            frag(
              span(s"Font changed from '${oldFont.fontName}' to '${newFont.fontName}'"),
              br,
              tag("small")(style := "color: var(--text-secondary)")(
                s"Old: ${formatFontStatus(oldFont)} → New: ${formatFontStatus(newFont)}",
              ),
            )
          case _ => frag()
        }
      case DiffType.Removed =>
        fontDiff.oldFont.map { f =>
          span(s"Font '${f.fontName}' removed")
        }.getOrElse(span("Unknown font removed"))

  private def formatFontStatus(fontInfo: FontInfo): String =
    val embedded = if (fontInfo.isEmbedded) "embedded" else "NOT embedded"
    val outlined = if (fontInfo.isOutlined) "outlined" else "standard"
    s"$embedded, $outlined"

  private def renderVisualCategory(
      visualDiff: Option[VisualDiff],
      oldImagePath: Option[String],
      newImagePath: Option[String],
      diffImagePath: Option[String],
  ): Frag =
    visualDiff match {
      case Some(vd) =>
        div(cls := "diff-category", attr("data-type") := "visual")(
          div(cls := "category-header", onclick := "toggleCategory(this)")(
            div(cls := "category-title")(
              span("👁️ Visual Differences"),
              span(cls := "count-badge")("1"),
            ),
            span(cls := "expand-icon")("▼"),
          ),
          div(cls := "category-content expanded")(
            div(cls := "category-body")(
              div(cls := "visual-comparison")(
                p(
                  strong("Pixel difference: "),
                  f"${vd.pixelDifferenceRatio * 100}%.2f%% pixels differ (${vd.differenceCount} pixels)",
                ),
                if (oldImagePath.isDefined && newImagePath.isDefined) {
                  frag(
                    div(cls := "comparison-container")(
                      div(cls := "comparison-panel")(
                        h4("📄 Old Version"),
                        img(src := oldImagePath.get, alt := "Old version"),
                      ),
                      div(cls := "comparison-panel")(
                        h4("📄 New Version"),
                        img(src := newImagePath.get, alt := "New version"),
                      ),
                    ),
                    diffImagePath.map { path =>
                      div(cls := "diff-highlight")(
                        h4("🔴 Differences Highlighted"),
                        img(src := path, alt := "Diff visualization", style := "width: 100%; max-width: 800px;"),
                        p(
                          style := "font-size: 13px; color: var(--text-secondary); font-style: italic; margin-top: 8px;",
                        )(
                          "Red areas indicate pixel-level differences",
                        ),
                      )
                    }.getOrElse(frag()),
                  )
                } else frag(),
              ),
            ),
          ),
        )
      case None =>
        div(cls := "diff-category no-diff-category", attr("data-type") := "visual")(
          div(cls := "category-header-static")(
            div(cls := "category-title")(
              span("👁️ Visual Differences"),
              span(cls := "count-badge")("0"),
            ),
          ),
          div(cls := "category-content-static")(
            p(cls := "no-diff-message")("✅ No visual differences found."),
          ),
        )
    }

  private def renderTextCategory(diffs: Seq[TextDiff]): Frag =
    if (diffs.isEmpty)
      div(cls := "diff-category no-diff-category", attr("data-type") := "text")(
        div(cls := "category-header-static")(
          div(cls := "category-title")(
            span("📝 Text Differences"),
            span(cls := "count-badge")("0"),
          ),
        ),
        div(cls := "category-content-static")(
          p(cls := "no-diff-message")("✅ No text differences found."),
        ),
      )
    else
      div(cls := "diff-category", attr("data-type") := "text")(
        div(cls := "category-header", onclick := "toggleCategory(this)")(
          div(cls := "category-title")(
            span("📝 Text Differences"),
            span(cls := "count-badge")(diffs.size.toString),
          ),
          span(cls := "expand-icon")("▼"),
        ),
        div(cls := "category-content")(
          div(cls := "category-body")(
            diffs.map { d =>
              div(cls := s"diff-item text-${d.diffType.name}")(
                strong(d.diffType.toString.capitalize + ": "),
                d.oldText.map(t => span(s"'$t' → ")).getOrElse(frag()),
                d.newText.map(t => span(s"'$t'")).getOrElse(frag()),
                br,
                tag("small")(style := "color: var(--text-secondary)")(
                  s"bbox=(${d.bbox.x.toInt}, ${d.bbox.y.toInt}, ${d.bbox.width.toInt}, ${d.bbox.height.toInt})",
                ),
              )
            },
          ),
        ),
      )

  private def renderLayoutCategory(diffs: Seq[LayoutDiff]): Frag =
    if (diffs.isEmpty)
      div(cls := "diff-category no-diff-category", attr("data-type") := "layout")(
        div(cls := "category-header-static")(
          div(cls := "category-title")(
            span("📐 Layout Shifts"),
            span(cls := "count-badge")("0"),
          ),
        ),
        div(cls := "category-content-static")(
          p(cls := "no-diff-message")("✅ No layout differences found."),
        ),
      )
    else
      div(cls := "diff-category", attr("data-type") := "layout")(
        div(cls := "category-header", onclick := "toggleCategory(this)")(
          div(cls := "category-title")(
            span("📐 Layout Shifts"),
            span(cls := "count-badge")(diffs.size.toString),
          ),
          span(cls := "expand-icon")("▼"),
        ),
        div(cls := "category-content")(
          div(cls := "category-body")(
            diffs.map { d =>
              div(cls := "diff-item layout")(
                strong("Moved: "),
                span(s"'${d.text.take(40)}' by "),
                strong(f"${d.displacement}%.1f px"),
                br,
                tag("small")(style := "color: var(--text-secondary)")(
                  s"from=(${d.oldBbox.x.toInt}, ${d.oldBbox.y.toInt}) to=(${d.newBbox.x.toInt}, ${d.newBbox.y.toInt})",
                ),
              )
            },
          ),
        ),
      )

  private def renderColorCategory(diffs: Seq[ColorDiff], imagePath: Option[String]): Frag =
    if (diffs.isEmpty)
      div(cls := "diff-category no-diff-category", attr("data-type") := "color")(
        div(cls := "category-header-static")(
          div(cls := "category-title")(
            span("🎨 Color Differences"),
            span(cls := "count-badge")("0"),
          ),
        ),
        div(cls := "category-content-static")(
          p(cls := "no-diff-message")("✅ No color differences found."),
        ),
      )
    else
      div(cls := "diff-category", attr("data-type") := "color")(
        div(cls := "category-header", onclick := "toggleCategory(this)")(
          div(cls := "category-title")(
            span("🎨 Color Differences"),
            span(cls := "count-badge")(diffs.size.toString),
          ),
          span(cls := "expand-icon")("▼"),
        ),
        div(cls := "category-content")(
          div(cls := "category-body")(
            div(cls := "color-grid")(
              diffs.take(20).map { cd =>
                div(cls := "color-comparison")(
                  div(
                    cls   := "color-swatch",
                    style := s"background: rgb(${cd.oldRgb.r},${cd.oldRgb.g},${cd.oldRgb.b})",
                  ),
                  span(cls := "color-arrow")("→"),
                  div(
                    cls   := "color-swatch",
                    style := s"background: rgb(${cd.newRgb.r},${cd.newRgb.g},${cd.newRgb.b})",
                  ),
                  div(
                    tag("small")(style := "display: block; color: var(--text-secondary)")(
                      s"RGB(${cd.oldRgb.r},${cd.oldRgb.g},${cd.oldRgb.b}) → RGB(${cd.newRgb.r},${cd.newRgb.g},${cd.newRgb.b})",
                    ),
                    tag("small")(style := "color: #e91e63; font-weight: 600;")(f"Δ=${cd.distance}%.1f"),
                  ),
                )
              },
            ),
            imagePath.map { path =>
              div(style := "margin-top: 16px;")(
                img(src := path, alt := "Color diff visualization", style := "max-width: 600px; width: 100%;"),
                p(style := "font-size: 13px; color: var(--text-secondary); font-style: italic; margin-top: 8px;")(
                  "Magenta markers indicate significant color changes",
                ),
              )
            }.getOrElse(frag()),
          ),
        ),
      )

  private def renderCascadingNotice(suppressed: Option[SuppressedDiffs]): Frag =
    suppressed match
      case Some(s) if s.reason.nonEmpty =>
        div(cls := "cascading-notice")(
          div(cls := "notice-header")(
            span(cls := "notice-icon")("ℹ️"),
            span(s" ${s.reason}"),
          ),
          p(style := "margin: 8px 0; color: var(--text-secondary);")(
            "The following differences are likely caused by this root change:",
          ),
          ul(cls := "notice-list")(
            li("Visual, layout, and color differences may be cascading effects of the font change"),
            li("All differences are shown below for your review"),
          ),
          details(cls := "notice-details")(
            tag("summary")("💡 Why is this important?"),
            p(
              "When fonts change, they often cause cascading effects on layout, colors, and visual appearance. ",
              "Understanding that these changes stem from a font difference helps you focus on the root cause. ",
              "All differences are displayed so you can verify the complete impact.",
            ),
          ),
        )
      case _ => frag()
