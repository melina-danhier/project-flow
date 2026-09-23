(() => {
    'use strict';

    const setSectionExpanded = (section, expanded) => {
        section.classList.toggle('is-collapsed', !expanded);
        const trigger = section.querySelector(':scope > .pf-plan-section__header .pf-phase-toggle-btn');
        const content = section.querySelector(':scope > .pf-collapsible-content');
        if (trigger) trigger.setAttribute('aria-expanded', String(expanded));
        if (content) content.hidden = !expanded;
    };

    // Phase collapse behavior: Only toggle when clicking caret (.pf-phase-toggle-btn)
    document.addEventListener('click', event => {
        const toggleBtn = event.target.closest('.pf-phase-toggle-btn');
        if (toggleBtn) {
            event.preventDefault();
            event.stopPropagation();
            const section = toggleBtn.closest('.pf-template-phase');
            if (section) setSectionExpanded(section, section.classList.contains('is-collapsed'));
            return;
        }
    });
})();
