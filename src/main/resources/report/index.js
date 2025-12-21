// ==========================================
// Theme Management
// ==========================================

/**
 * Toggles between light and dark theme
 */
const toggleTheme = () => {
  const html = document.documentElement;
  const currentTheme = html.getAttribute('data-theme');
  const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

  html.setAttribute('data-theme', newTheme);
  updateThemeButton(newTheme);
  localStorage.setItem('theme', newTheme);
};

/**
 * Updates the theme toggle button text
 * @param {string} theme - The current theme ('dark' or 'light')
 */
const updateThemeButton = (theme) => {
  const btn = document.querySelector('.theme-toggle');
  if (btn) {
    btn.textContent = theme === 'dark' ? '☀️ Light Mode' : '🌙 Dark Mode';
  }
};

/**
 * Loads and applies the saved theme from localStorage
 */
const initializeTheme = () => {
  const savedTheme = localStorage.getItem('theme') || 'light';
  document.documentElement.setAttribute('data-theme', savedTheme);
  updateThemeButton(savedTheme);
};

// Initialize theme on page load
initializeTheme();


// ==========================================
// Navigation
// ==========================================

/**
 * Scrolls to a specific page section
 * @param {number} pageNum - The page number to scroll to
 * @param {Event} event - The event object
 */
const scrollToPage = (pageNum, event) => {
  const element = document.getElementById(`page-${pageNum}`);

  if (element) {
    element.scrollIntoView({ behavior: 'smooth', block: 'start' });

    // Update active state
    document.querySelectorAll('.page-nav-item').forEach(item => {
      item.classList.remove('active');
    });

    if (event?.currentTarget) {
      event.currentTarget.classList.add('active');
    }
  }
};

/**
 * Jumps to the first diff section
 */
const jumpToFirstDiff = () => {
  const firstPage = document.querySelector('.page-section');
  if (firstPage) {
    firstPage.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
};


// ==========================================
// Category Management
// ==========================================

/**
 * Toggles the expand/collapse state of a category
 * @param {HTMLElement} header - The category header element
 */
const toggleCategory = (header) => {
  const content = header.nextElementSibling;
  const isExpanded = content.classList.contains('expanded');

  if (isExpanded) {
    content.classList.remove('expanded');
    header.classList.add('collapsed');
  } else {
    content.classList.add('expanded');
    header.classList.remove('collapsed');
  }
};

/**
 * Toggles all category sections between expanded and collapsed
 */
const toggleAllSections = () => {
  const allExpanded = Array.from(document.querySelectorAll('.category-content'))
    .every(content => content.classList.contains('expanded'));

  document.querySelectorAll('.category-header').forEach(header => {
    const content = header.nextElementSibling;

    if (allExpanded) {
      content.classList.remove('expanded');
      header.classList.add('collapsed');
    } else {
      content.classList.add('expanded');
      header.classList.remove('collapsed');
    }
  });
};


// ==========================================
// Filters
// ==========================================

/**
 * Applies filters to show/hide categories based on selected types
 */
const applyFilters = () => {
  const filters = {
    visual: document.getElementById('filter-visual')?.checked ?? true,
    color: document.getElementById('filter-color')?.checked ?? true,
    text: document.getElementById('filter-text')?.checked ?? true,
    layout: document.getElementById('filter-layout')?.checked ?? true,
    font: document.getElementById('filter-font')?.checked ?? true,
  };

  document.querySelectorAll('.diff-category').forEach(category => {
    const type = category.getAttribute('data-type');
    category.style.display = filters[type] ? 'block' : 'none';
  });
};


// ==========================================
// Export & Print
// ==========================================

/**
 * Exports the diff report as JSON
 */
const exportReport = async () => {
  try {
    const response = await fetch('diff.json');

    if (!response.ok) {
      throw new Error('diff.json not found');
    }

    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.style.display = 'none';
    link.href = url;
    link.download = 'diff.json';

    document.body.appendChild(link);
    link.click();

    // Cleanup
    window.URL.revokeObjectURL(url);
    document.body.removeChild(link);
  } catch (error) {
    alert(`Could not export JSON: ${error.message}`);
  }
};

/**
 * Triggers the browser print dialog
 */
const printReport = () => {
  window.print();
};


// ==========================================
// Scroll Tracking
// ==========================================

/**
 * Tracks the active page section during scrolling
 */
let scrollTicking = false;

const trackActivePageOnScroll = () => {
  if (!scrollTicking) {
    window.requestAnimationFrame(() => {
      const sections = document.querySelectorAll('.page-section');

      sections.forEach(section => {
        const rect = section.getBoundingClientRect();

        // Check if section is in view
        if (rect.top <= 200 && rect.bottom >= 200) {
          const pageNum = section.id.replace('page-', '');

          document.querySelectorAll('.page-nav-item').forEach(item => {
            item.classList.remove('active');

            if (item.textContent.includes(`Page ${pageNum}`)) {
              item.classList.add('active');
            }
          });
        }
      });

      scrollTicking = false;
    });

    scrollTicking = true;
  }
};

window.addEventListener('scroll', trackActivePageOnScroll);

// ==========================================
// Batch Report Functions
// ==========================================

/**
 * Filters the batch table based on search input
 */
const filterBatchTable = () => {
  const input = document.getElementById('batch-search');
  if (!input) return;

  const filter = input.value.toLowerCase();
  const table = document.getElementById('batch-table');
  if (!table) return;

  const rows = table.getElementsByTagName('tr');

  let visibleCount = 0;
  for (let i = 1; i < rows.length; i++) { // Skip header row
    const row = rows[i];
    const filename = row.getElementsByClassName('filename-cell')[0];

    if (filename) {
      const text = filename.textContent || filename.innerText;
      if (text.toLowerCase().indexOf(filter) > -1) {
        row.classList.remove('hidden');
        visibleCount++;
      } else {
        row.classList.add('hidden');
      }
    }
  }

  // Show empty state if no results
  updateBatchEmptyState(visibleCount);
};

/**
 * Sorts the batch table by column
 * @param {number} columnIndex - The column index to sort by
 */
let currentSortColumn = -1;
let currentSortAscending = true;

const sortBatchTable = (columnIndex) => {
  const table = document.getElementById('batch-table');
  if (!table) return;

  const tbody = table.getElementsByTagName('tbody')[0];
  const rows = Array.from(tbody.getElementsByTagName('tr'));

  // Toggle sort direction if same column
  if (currentSortColumn === columnIndex) {
    currentSortAscending = !currentSortAscending;
  } else {
    currentSortAscending = true;
    currentSortColumn = columnIndex;
  }

  // Sort rows
  rows.sort((a, b) => {
    let aValue, bValue;

    const aCells = a.getElementsByTagName('td');
    const bCells = b.getElementsByTagName('td');

    if (!aCells[columnIndex] || !bCells[columnIndex]) return 0;

    // Get values for comparison
    if (columnIndex === 0) {
      // Status column
      aValue = aCells[columnIndex].getAttribute('data-status') || '';
      bValue = bCells[columnIndex].getAttribute('data-status') || '';
    } else if (aCells[columnIndex].classList.contains('number-cell')) {
      // Number columns - use data-value attribute
      aValue = parseInt(aCells[columnIndex].getAttribute('data-value') || '0');
      bValue = parseInt(bCells[columnIndex].getAttribute('data-value') || '0');
    } else {
      // Text columns
      aValue = aCells[columnIndex].textContent.trim().toLowerCase();
      bValue = bCells[columnIndex].textContent.trim().toLowerCase();
    }

    // Compare
    let comparison = 0;
    if (aValue > bValue) {
      comparison = 1;
    } else if (aValue < bValue) {
      comparison = -1;
    }

    return currentSortAscending ? comparison : -comparison;
  });

  // Reorder rows in DOM
  rows.forEach(row => tbody.appendChild(row));

  // Update header indicators
  updateSortIndicators(table, columnIndex);
};

/**
 * Updates sort indicators in table headers
 */
const updateSortIndicators = (table, sortedColumn) => {
  const headers = table.getElementsByTagName('th');
  for (let i = 0; i < headers.length; i++) {
    const header = headers[i];
    if (!header.classList.contains('sortable')) continue;

    // Remove existing arrows, add default
    let text = header.textContent.replace(/ [⇅↑↓]/g, '');

    if (i === sortedColumn) {
      text += currentSortAscending ? ' ↑' : ' ↓';
    } else {
      text += ' ⇅';
    }

    header.textContent = text;
  }
};

/**
 * Resets all batch table filters
 */
const resetBatchFilters = () => {
  // Clear search
  const searchInput = document.getElementById('batch-search');
  if (searchInput) {
    searchInput.value = '';
  }

  // Show all rows
  const table = document.getElementById('batch-table');
  if (table) {
    const rows = table.getElementsByTagName('tr');
    for (let i = 1; i < rows.length; i++) {
      rows[i].classList.remove('hidden');
    }
  }

  // Reset sort
  currentSortColumn = -1;
  currentSortAscending = true;

  // Update empty state
  updateBatchEmptyState(table ? table.getElementsByTagName('tr').length - 1 : 0);
};

/**
 * Updates the empty state message for batch table
 */
const updateBatchEmptyState = (visibleCount) => {
  let emptyState = document.querySelector('.batch-empty-state');
  const tableContainer = document.querySelector('.batch-table-container');

  if (visibleCount === 0) {
    if (!emptyState && tableContainer) {
      emptyState = document.createElement('div');
      emptyState.className = 'batch-empty-state';
      emptyState.innerHTML = '<p>🔍 No results found</p><p style="font-size: 14px;">Try adjusting your search terms</p>';
      tableContainer.parentNode.insertBefore(emptyState, tableContainer.nextSibling);
    }
    if (tableContainer) tableContainer.style.display = 'none';
  } else {
    if (emptyState) {
      emptyState.remove();
    }
    if (tableContainer) tableContainer.style.display = 'block';
  }
};
