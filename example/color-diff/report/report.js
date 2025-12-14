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
