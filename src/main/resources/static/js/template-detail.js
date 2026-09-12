(() => {
    'use strict';

    // Phase collapse behavior: Only toggle when clicking caret (.pf-phase-toggle-btn)
    document.addEventListener('click', event => {
        const toggleBtn = event.target.closest('.pf-phase-toggle-btn');
        if (toggleBtn) {
            event.preventDefault();
            event.stopPropagation();
            const details = toggleBtn.closest('details.pf-template-phase');
            if (details) {
                details.open = !details.open;
            }
            return;
        }

        // Prevent header click from toggling <details> (only the caret button toggles)
        const header = event.target.closest('summary.pf-plan-section__header');
        if (header) {
            if (event.target.closest('button, input, textarea, a, form')) {
                return;
            }
            event.preventDefault();
        }
    });
})();
