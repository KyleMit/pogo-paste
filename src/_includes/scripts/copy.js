const toast = document.getElementById("toast");
let toastTimeout;


// Toast
const showToast = (message, type = "info") => {
  if (toastTimeout) clearTimeout(toastTimeout);
  toast.textContent = message;
  toast.className = `toast ${type}`;
  toastTimeout = setTimeout(() => toast.classList.add("hidden"), 3000);
};

// Copy to clipboard
const copyToClipboard = async (button) => {
  try {
    await navigator.clipboard.writeText(button.value);
    showToast("Copied to clipboard!", "success");
  } catch {
    showToast("Failed to copy", "error");
  }
};

document.body.addEventListener('click', event => {
  const btn = event.target.closest('.copy-btn, .search-compact-copy');
  if (btn) copyToClipboard(btn);
});
