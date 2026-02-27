// Generic toggle — driven entirely by data attributes on the container + buttons:
//   container: data-toggle-storage-key, data-toggle-default, data-toggle-class-prefix
//   buttons:   data-toggle-value
const applyToggle = (group, value) => {
  const { toggleClassPrefix, togglePreviousValue } = group.dataset;
  if (togglePreviousValue) document.body.classList.remove(toggleClassPrefix + togglePreviousValue);
  document.body.classList.add(toggleClassPrefix + value);
  group.dataset.togglePreviousValue = value;
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
