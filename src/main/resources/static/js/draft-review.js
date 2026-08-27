document.querySelectorAll('.critical-assumption').forEach(marker => {
    const button = marker.querySelector('button');
    const description = marker.querySelector('[role="tooltip"]');
    let pinned = false;
    const show = visible => {
        description.hidden = !visible;
        button.setAttribute('aria-expanded', String(visible));
        if (visible) {
            const anchor = marker.getBoundingClientRect();
            const width = description.getBoundingClientRect().width;
            const viewportWidth = document.documentElement.clientWidth;
            description.style.left = `${Math.max(8, Math.min(anchor.left, viewportWidth - width - 8)) - anchor.left}px`;
        }
    };
    marker.addEventListener('mouseenter', () => show(true));
    marker.addEventListener('mouseleave', () => show(pinned || marker.contains(document.activeElement)));
    marker.addEventListener('focusin', () => show(true));
    marker.addEventListener('focusout', event => {
        if (!marker.contains(event.relatedTarget)) { pinned = false; show(marker.matches(':hover')); }
    });
    button.addEventListener('click', () => { pinned = !pinned; show(pinned); });
    marker.addEventListener('keydown', event => {
        if (event.key === 'Escape') { pinned = false; show(false); }
    });
});
