// Cache DOM elements for better performance
const toast = document.getElementById("toast");
const cardsContainer = document.querySelector('.cards-container');
const viewToggle = document.querySelector('.view-toggle');

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

// Single view toggle event listener
viewToggle.addEventListener('click', (event) => {
  const btn = event.target.closest('.view-btn');
  if (btn && btn.dataset.view) {
    setViewMode(btn.dataset.view);
  }
});

const showToast = (message, type = "info") => {
  // Clear existing timeout to prevent overlapping toasts
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

// Event delegation for better performance
cardsContainer.addEventListener('click', (event) => {
  if (event.target.classList.contains('copy-btn')) {
    copyToClipboard(event.target);
  }
});

// Initialize on page load
loadViewMode();
