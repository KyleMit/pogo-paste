// Cache DOM elements for better performance
const toast = document.getElementById("toast");
const viewToggle = document.querySelector('.view-toggle');
const sectionToggle = document.querySelector('.section-toggle');

let toastTimeout;

// View mode management
const VIEW_MODE_KEY = 'pogo-paste-view-mode';

const setViewMode = (mode) => {
  if (mode === 'grid') {
    document.body.classList.add('grid-view');
  } else {
    document.body.classList.remove('grid-view');
  }
  localStorage.setItem(VIEW_MODE_KEY, mode);
};

const loadViewMode = () => {
  const savedMode = localStorage.getItem(VIEW_MODE_KEY) || 'list';
  setViewMode(savedMode);
};

viewToggle.addEventListener('click', (event) => {
  const btn = event.target.closest('.view-btn');
  if (btn && btn.dataset.view) {
    setViewMode(btn.dataset.view);
  }
});

// Section management
const SECTION_KEY = 'pogo-paste-section';

const setSection = (section) => {
  if (section === 'search') {
    document.body.classList.add('section-search');
  } else {
    document.body.classList.remove('section-search');
  }
  localStorage.setItem(SECTION_KEY, section);
};

const loadSection = () => {
  const savedSection = localStorage.getItem(SECTION_KEY) || 'symbols';
  setSection(savedSection);
};

sectionToggle.addEventListener('click', (event) => {
  const btn = event.target.closest('.section-btn');
  if (btn && btn.dataset.section) {
    setSection(btn.dataset.section);
  }
});

const showToast = (message, type = "info") => {
  if (toastTimeout) clearTimeout(toastTimeout);
  toast.textContent = message;
  toast.className = `toast ${type}`;
  toastTimeout = setTimeout(() => {
    toast.classList.add("hidden");
  }, 3000);
};

const copyToClipboard = async (button) => {
  try {
    await navigator.clipboard.writeText(button.value);
    showToast("Copied to clipboard!", "success");
  } catch (error) {
    showToast("Failed to copy", "error");
  }
};

// Event delegation covering all copy buttons on the page
document.body.addEventListener('click', (event) => {
  const btn = event.target.closest('.copy-btn, .search-compact-copy');
  if (btn) {
    copyToClipboard(btn);
  }
});

// Initialize on page load
loadViewMode();
loadSection();
