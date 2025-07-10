function showToast(message, type = "info") {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.className = `toast ${type}`;
  toast.classList.remove("hidden");

  setTimeout(() => {
    toast.classList.add("hidden");
  }, 3000);
}

async function copyToClipboard(el) {
  const value = el.value;

  try {
    await navigator.clipboard.writeText(value);
    showToast("Copied to clipboard!", "success");
  } catch (error) {
    showToast("Failed to copy to clipboard", "error");
  }
}

document.getElementById('symbolsTable').addEventListener('click', function(event) {
  const el = event.target;

  if (el.classList.contains('copy-btn')) {
    copyToClipboard(el);
  }
});
