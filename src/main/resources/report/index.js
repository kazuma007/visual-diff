/**
 * =============================================================================
 * File Comparison Report - Interactive UI Controller
 * =============================================================================
 *
 * This module manages the interactive features of the file comparison report:
 * - Theme switching (light/dark mode)
 * - Navigation and scrolling
 * - Category expand/collapse
 * - Filtering and search
 * - Table sorting
 * - Export functionality
 */

'use strict';


/**
 * =============================================================================
 * CONSTANTS & CONFIGURATION
 * =============================================================================
 */

const CONFIG = {
  DEFAULT_THEME: 'light',
  SCROLL_OFFSET: 200,
  THEME_STORAGE_KEY: 'theme',
  SELECTORS: {
    themeToggle: '.theme-toggle',
    pageNavItem: '.page-nav-item',
    pageSection: '.page-section',
    categoryHeader: '.category-header',
    categoryContent: '.category-content',
    diffCategory: '.diff-category',
    batchTable: '#batch-table',
    batchSearch: '#batch-search',
    batchEmptyState: '.batch-empty-state',
    batchTableContainer: '.batch-table-container'
  },
  SORT_INDICATORS: {
    unsorted: ' ⇅',
    ascending: ' ↑',
    descending: ' ↓'
  }
};


/**
 * =============================================================================
 * THEME MANAGEMENT
 * =============================================================================
 */

class ThemeManager {
  constructor() {
    this.html = document.documentElement;
    this.button = null;
  }

  /**
   * Initializes theme from localStorage and sets up the UI
   */
  initialize() {
    const savedTheme = localStorage.getItem(CONFIG.THEME_STORAGE_KEY) || CONFIG.DEFAULT_THEME;
    this.setTheme(savedTheme);
    this.button = document.querySelector(CONFIG.SELECTORS.themeToggle);
  }

  /**
   * Toggles between light and dark theme
   */
  toggle() {
    const currentTheme = this.html.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    this.setTheme(newTheme);
  }

  /**
   * Sets the theme and updates all related UI elements
   * @param {string} theme - The theme to set ('light' or 'dark')
   */
  setTheme(theme) {
    this.html.setAttribute('data-theme', theme);
    this.updateButton(theme);
    localStorage.setItem(CONFIG.THEME_STORAGE_KEY, theme);
  }

  /**
   * Updates the theme toggle button text
   * @param {string} theme - The current theme
   */
  updateButton(theme) {
    if (this.button) {
      this.button.textContent = theme === 'dark' ? '☀️ Light Mode' : '🌙 Dark Mode';
    }
  }
}


/**
 * =============================================================================
 * NAVIGATION
 * =============================================================================
 */

class NavigationManager {
  /**
   * Scrolls to a specific page section
   * @param {number} pageNum - The page number to scroll to
   * @param {Event} event - The event object
   */
  scrollToPage(pageNum, event) {
    const element = document.getElementById(`page-${pageNum}`);
    if (!element) return;

    element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    this.updateActiveNavItem(event?.currentTarget);
  }

  /**
   * Jumps to the first diff section
   */
  jumpToFirstDiff() {
    const firstPage = document.querySelector(CONFIG.SELECTORS.pageSection);
    if (firstPage) {
      firstPage.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  /**
   * Updates the active state of navigation items
   * @param {HTMLElement} activeItem - The item to mark as active
   */
  updateActiveNavItem(activeItem) {
    document.querySelectorAll(CONFIG.SELECTORS.pageNavItem).forEach(item => {
      item.classList.remove('active');
    });
    if (activeItem) {
      activeItem.classList.add('active');
    }
  }
}


/**
 * =============================================================================
 * SCROLL TRACKING
 * =============================================================================
 */

class ScrollTracker {
  constructor() {
    this.ticking = false;
    this.init();
  }

  /**
   * Initializes scroll tracking
   */
  init() {
    window.addEventListener('scroll', () => this.onScroll());
  }

  /**
   * Handles scroll events with request animation frame throttling
   */
  onScroll() {
    if (this.ticking) return;

    window.requestAnimationFrame(() => {
      this.updateActiveSection();
      this.ticking = false;
    });

    this.ticking = true;
  }

  /**
   * Updates the active navigation item based on scroll position
   */
  updateActiveSection() {
    const sections = document.querySelectorAll(CONFIG.SELECTORS.pageSection);

    sections.forEach(section => {
      const rect = section.getBoundingClientRect();

      if (rect.top <= CONFIG.SCROLL_OFFSET && rect.bottom >= CONFIG.SCROLL_OFFSET) {
        const pageNum = section.id.replace('page-', '');
        this.highlightNavItem(pageNum);
      }
    });
  }

  /**
   * Highlights the navigation item for the given page number
   * @param {string} pageNum - The page number to highlight
   */
  highlightNavItem(pageNum) {
    document.querySelectorAll(CONFIG.SELECTORS.pageNavItem).forEach(item => {
      item.classList.remove('active');
      if (item.textContent.includes(`Page ${pageNum}`)) {
        item.classList.add('active');
      }
    });
  }
}


/**
 * =============================================================================
 * CATEGORY MANAGEMENT
 * =============================================================================
 */

class CategoryManager {
  /**
   * Toggles a single category's expand/collapse state
   * @param {HTMLElement} header - The category header element
   */
  toggleCategory(header) {
    const content = header.nextElementSibling;
    if (!content) return;

    const isExpanded = content.classList.contains('expanded');

    if (isExpanded) {
      this.collapseCategory(header, content);
    } else {
      this.expandCategory(header, content);
    }
  }

  /**
   * Expands a category
   * @param {HTMLElement} header - The category header
   * @param {HTMLElement} content - The category content
   */
  expandCategory(header, content) {
    content.classList.add('expanded');
    header.classList.remove('collapsed');
  }

  /**
   * Collapses a category
   * @param {HTMLElement} header - The category header
   * @param {HTMLElement} content - The category content
   */
  collapseCategory(header, content) {
    content.classList.remove('expanded');
    header.classList.add('collapsed');
  }

  /**
   * Toggles all categories between expanded and collapsed
   */
  toggleAll() {
    const allExpanded = this.areAllExpanded();
    const headers = document.querySelectorAll(CONFIG.SELECTORS.categoryHeader);

    headers.forEach(header => {
      const content = header.nextElementSibling;
      if (!content) return;

      if (allExpanded) {
        this.collapseCategory(header, content);
      } else {
        this.expandCategory(header, content);
      }
    });
  }

  /**
   * Checks if all categories are currently expanded
   * @returns {boolean} True if all categories are expanded
   */
  areAllExpanded() {
    const contents = Array.from(document.querySelectorAll(CONFIG.SELECTORS.categoryContent));
    return contents.every(content => content.classList.contains('expanded'));
  }
}


/**
 * =============================================================================
 * FILTER MANAGEMENT
 * =============================================================================
 */

class FilterManager {
  /**
   * Applies filters to show/hide categories based on selected types
   */
  applyFilters() {
    const filters = this.getFilterStates();
    this.updateCategoryVisibility(filters);
  }

  /**
   * Gets the current state of all filters
   * @returns {Object} Object with filter states
   */
  getFilterStates() {
    return {
      visual: document.getElementById('filter-visual')?.checked ?? true,
      color: document.getElementById('filter-color')?.checked ?? true,
      text: document.getElementById('filter-text')?.checked ?? true,
      layout: document.getElementById('filter-layout')?.checked ?? true,
      font: document.getElementById('filter-font')?.checked ?? true
    };
  }

  /**
   * Updates category visibility based on filter states
   * @param {Object} filters - The filter states
   */
  updateCategoryVisibility(filters) {
    document.querySelectorAll(CONFIG.SELECTORS.diffCategory).forEach(category => {
      const type = category.getAttribute('data-type');
      category.style.display = filters[type] ? 'block' : 'none';
    });
  }
}


/**
 * =============================================================================
 * EXPORT & PRINT
 * =============================================================================
 */

class ExportManager {
  /**
   * Exports the diff report as JSON
   */
  async exportReport() {
    try {
      const response = await fetch('diff.json');

      if (!response.ok) {
        throw new Error('diff.json not found');
      }

      const blob = await response.blob();
      this.downloadBlob(blob, 'diff.json');
    } catch (error) {
      alert(`Could not export JSON: ${error.message}`);
    }
  }

  /**
   * Downloads a blob as a file
   * @param {Blob} blob - The blob to download
   * @param {string} filename - The filename for the download
   */
  downloadBlob(blob, filename) {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.style.display = 'none';
    link.href = url;
    link.download = filename;

    document.body.appendChild(link);
    link.click();

    // Cleanup
    window.URL.revokeObjectURL(url);
    document.body.removeChild(link);
  }

  /**
   * Triggers the browser print dialog
   */
  printReport() {
    window.print();
  }
}


/**
 * =============================================================================
 * BATCH TABLE MANAGEMENT
 * =============================================================================
 */

class BatchTableManager {
  constructor() {
    this.currentSortColumn = -1;
    this.currentSortAscending = true;
  }

  /**
   * Filters the batch table based on search input
   */
  filterTable() {
    const input = document.getElementById(CONFIG.SELECTORS.batchSearch.slice(1));
    if (!input) return;

    const filter = input.value.toLowerCase();
    const table = document.getElementById(CONFIG.SELECTORS.batchTable.slice(1));
    if (!table) return;

    const rows = table.getElementsByTagName('tr');
    let visibleCount = 0;

    // Skip header row (index 0)
    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      const filenameCell = row.getElementsByClassName('filename-cell')[0];

      if (filenameCell) {
        const text = filenameCell.textContent || filenameCell.innerText;
        const isVisible = text.toLowerCase().includes(filter);

        row.classList.toggle('hidden', !isVisible);
        if (isVisible) visibleCount++;
      }
    }

    this.updateEmptyState(visibleCount);
  }

  /**
   * Sorts the batch table by column
   * @param {number} columnIndex - The column index to sort by
   */
  sortTable(columnIndex) {
    const table = document.getElementById(CONFIG.SELECTORS.batchTable.slice(1));
    if (!table) return;

    const tbody = table.getElementsByTagName('tbody')[0];
    const rows = Array.from(tbody.getElementsByTagName('tr'));

    this.updateSortDirection(columnIndex);
    this.sortRows(rows, columnIndex);
    this.reorderRows(tbody, rows);
    this.updateSortIndicators(table, columnIndex);
  }

  /**
   * Updates the sort direction based on the column clicked
   * @param {number} columnIndex - The column index
   */
  updateSortDirection(columnIndex) {
    if (this.currentSortColumn === columnIndex) {
      this.currentSortAscending = !this.currentSortAscending;
    } else {
      this.currentSortAscending = true;
      this.currentSortColumn = columnIndex;
    }
  }

  /**
   * Sorts rows based on column values
   * @param {Array} rows - Array of table rows
   * @param {number} columnIndex - The column to sort by
   */
  sortRows(rows, columnIndex) {
    rows.sort((a, b) => {
      const aCells = a.getElementsByTagName('td');
      const bCells = b.getElementsByTagName('td');

      if (!aCells[columnIndex] || !bCells[columnIndex]) return 0;

      const aValue = this.getCellValue(aCells[columnIndex], columnIndex);
      const bValue = this.getCellValue(bCells[columnIndex], columnIndex);

      return this.compareValues(aValue, bValue);
    });
  }

  /**
   * Gets the sortable value from a cell
   * @param {HTMLElement} cell - The table cell
   * @param {number} columnIndex - The column index
   * @returns {string|number} The sortable value
   */
  getCellValue(cell, columnIndex) {
    // Status column
    if (columnIndex === 0) {
      return cell.getAttribute('data-status') || '';
    }

    // Number columns
    if (cell.classList.contains('number-cell')) {
      return parseInt(cell.getAttribute('data-value') || '0', 10);
    }

    // Text columns
    return cell.textContent.trim().toLowerCase();
  }

  /**
   * Compares two values for sorting
   * @param {*} a - First value
   * @param {*} b - Second value
   * @returns {number} Comparison result
   */
  compareValues(a, b) {
    let comparison = 0;

    if (a > b) comparison = 1;
    else if (a < b) comparison = -1;

    return this.currentSortAscending ? comparison : -comparison;
  }

  /**
   * Reorders rows in the DOM
   * @param {HTMLElement} tbody - The table body
   * @param {Array} rows - Sorted array of rows
   */
  reorderRows(tbody, rows) {
    rows.forEach(row => tbody.appendChild(row));
  }

  /**
   * Updates sort indicators in table headers
   * @param {HTMLElement} table - The table element
   * @param {number} sortedColumn - The currently sorted column
   */
  updateSortIndicators(table, sortedColumn) {
    const headers = table.getElementsByTagName('th');

    for (let i = 0; i < headers.length; i++) {
      const header = headers[i];
      if (!header.classList.contains('sortable')) continue;

      let text = header.textContent.replace(/ [⇅↑↓]/g, '');

      if (i === sortedColumn) {
        text += this.currentSortAscending
          ? CONFIG.SORT_INDICATORS.ascending
          : CONFIG.SORT_INDICATORS.descending;
      } else {
        text += CONFIG.SORT_INDICATORS.unsorted;
      }

      header.textContent = text;
    }
  }

  /**
   * Resets all batch table filters and sorting
   */
  resetFilters() {
    this.clearSearch();
    this.showAllRows();
    this.resetSort();
  }

  /**
   * Clears the search input
   */
  clearSearch() {
    const searchInput = document.getElementById(CONFIG.SELECTORS.batchSearch.slice(1));
    if (searchInput) {
      searchInput.value = '';
    }
  }

  /**
   * Shows all table rows
   */
  showAllRows() {
    const table = document.getElementById(CONFIG.SELECTORS.batchTable.slice(1));
    if (!table) return;

    const rows = table.getElementsByTagName('tr');
    for (let i = 1; i < rows.length; i++) {
      rows[i].classList.remove('hidden');
    }

    this.updateEmptyState(rows.length - 1);
  }

  /**
   * Resets sort state
   */
  resetSort() {
    this.currentSortColumn = -1;
    this.currentSortAscending = true;
  }

  /**
   * Updates the empty state message for the batch table
   * @param {number} visibleCount - Number of visible rows
   */
  updateEmptyState(visibleCount) {
    let emptyState = document.querySelector(CONFIG.SELECTORS.batchEmptyState);
    const tableContainer = document.querySelector(CONFIG.SELECTORS.batchTableContainer);

    if (visibleCount === 0) {
      if (!emptyState && tableContainer) {
        emptyState = this.createEmptyStateElement();
        tableContainer.parentNode.insertBefore(emptyState, tableContainer.nextSibling);
      }
      if (tableContainer) tableContainer.style.display = 'none';
    } else {
      if (emptyState) emptyState.remove();
      if (tableContainer) tableContainer.style.display = 'block';
    }
  }

  /**
   * Creates the empty state element
   * @returns {HTMLElement} The empty state element
   */
  createEmptyStateElement() {
    const div = document.createElement('div');
    div.className = 'batch-empty-state';
    div.innerHTML = `
      <p>🔍 No results found</p>
      <p>Try adjusting your search terms</p>
    `;
    return div;
  }
}


/**
 * =============================================================================
 * INITIALIZATION & GLOBAL FUNCTIONS
 * =============================================================================
 */

// Initialize managers
const themeManager = new ThemeManager();
const navigationManager = new NavigationManager();
const categoryManager = new CategoryManager();
const filterManager = new FilterManager();
const exportManager = new ExportManager();
const batchTableManager = new BatchTableManager();

// Initialize theme on page load
themeManager.initialize();

// Initialize scroll tracking
const scrollTracker = new ScrollTracker();

/**
 * Global functions for HTML onclick attributes
 * These provide backwards compatibility with existing HTML
 */

const toggleTheme = () => themeManager.toggle();
const scrollToPage = (pageNum, event) => navigationManager.scrollToPage(pageNum, event);
const jumpToFirstDiff = () => navigationManager.jumpToFirstDiff();
const toggleCategory = (header) => categoryManager.toggleCategory(header);
const toggleAllSections = () => categoryManager.toggleAll();
const applyFilters = () => filterManager.applyFilters();
const exportReport = () => exportManager.exportReport();
const printReport = () => exportManager.printReport();
const filterBatchTable = () => batchTableManager.filterTable();
const sortBatchTable = (columnIndex) => batchTableManager.sortTable(columnIndex);
const resetBatchFilters = () => batchTableManager.resetFilters();
