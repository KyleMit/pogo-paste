// Cache DOM elements for better performance
const toast = document.getElementById("toast");
const cardsContainer = document.querySelector('.cards-container');

let toastTimeout;

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
