// Generic toggle — driven entirely by data attributes on the container + buttons:
//   container: data-toggle-storage-key, data-toggle-default, data-toggle-class-prefix
//   buttons:   data-toggle-value

const SECTION_ANIM_MS = 300;

// symbols is visually "right", search is visually "left"
const animateSectionTransition = (from, to) => {
  const entering = document.querySelector(`.${to}-container`);
  const exiting  = document.querySelector(`.${from}-container`);
  if (!entering || !exiting) return;

  const enterFrom = to === 'symbols' ? 'right' : 'left';
  const exitTo    = to === 'symbols' ? 'left'  : 'right';

  entering.classList.add('section-entering', `from-${enterFrom}`);
  exiting.classList.add('section-exiting', `to-${exitTo}`);

  setTimeout(() => {
    entering.classList.remove('section-entering', `from-${enterFrom}`);
    exiting.classList.remove('section-exiting', `to-${exitTo}`);
  }, SECTION_ANIM_MS);
};

const applyToggle = (group, value) => {
  const { toggleClassPrefix, togglePreviousValue } = group.dataset;

  if (toggleClassPrefix === 'section-' && togglePreviousValue && togglePreviousValue !== value) {
    animateSectionTransition(togglePreviousValue, value);
  }

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
