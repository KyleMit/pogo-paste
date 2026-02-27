
// Generic toggle — driven entirely by data attributes on the container + buttons:
//   container: data-toggle-storage-key, data-toggle-default
//   buttons:   data-toggle-value, data-toggle-body-class
const applyToggle = (group, value) => {
  // Remove every body class owned by this group
  const classes = [...group.querySelectorAll('[data-toggle-body-class]')]
    .map(btn => btn.dataset.toggleBodyClass)
    .filter(Boolean);
  document.body.classList.remove(...classes);
  // Apply the active button's body class
  const active = group.querySelector(`[data-toggle-value="${value}"]`);
  if (active?.dataset.toggleBodyClass) document.body.classList.add(active.dataset.toggleBodyClass);
};

document.querySelectorAll('[data-toggle-storage-key]').forEach(group => {
  const { toggleStorageKey, toggleDefault } = group.dataset;

  applyToggle(group, localStorage.getItem(toggleStorageKey) || toggleDefault);

  group.addEventListener('click', event => {
    const btn = event.target.closest('[data-toggle-value]');
    if (!btn) return;
    applyToggle(group, btn.dataset.toggleValue);
    localStorage.setItem(toggleStorageKey, btn.dataset.toggleValue);
  });
});
