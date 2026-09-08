(() => {
    let requestInFlight = false;
    const planMain = () => document.querySelector('main[data-project-id]');
    const stateKey = main => `projectflow:plan-state:${main.dataset.projectId}`;

    const readState = main => {
        try {
            return JSON.parse(sessionStorage.getItem(stateKey(main))) || {};
        } catch (_error) {
            return {};
        }
    };

    const captureState = main => {
        if (!main) return;
        const state = {
            openSectionIds: [...main.querySelectorAll('details.plan-section[open]')]
                .map(section => section.dataset.sectionId)
                .filter(Boolean),
            scrollY: window.scrollY
        };
        try {
            sessionStorage.setItem(stateKey(main), JSON.stringify(state));
        } catch (_error) {
            // The plan remains usable when browser storage is unavailable.
        }
    };

    const restoreState = main => {
        if (!main) return;
        const state = readState(main);
        const openSectionIds = new Set(state.openSectionIds || []);
        main.querySelectorAll('details.plan-section').forEach(section => {
            if (openSectionIds.has(section.dataset.sectionId)) {
                section.open = true;
            }
        });
        if (Number.isFinite(state.scrollY)) {
            requestAnimationFrame(() => window.scrollTo(0, state.scrollY));
        }
    };

    const setBusy = (form, busy) => {
        form.setAttribute('aria-busy', String(busy));
        form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(control => {
            control.disabled = busy;
        });
    };

    const showRequestError = () => {
        let message = document.querySelector('#plan-request-error');
        if (!message) {
            message = document.createElement('div');
            message.id = 'plan-request-error';
            message.className = 'pf-alert pf-alert--error';
            message.role = 'alert';
            planMain()?.prepend(message);
        }
        if (message) {
            message.textContent = 'Die Änderung konnte nicht gespeichert werden. Bitte versuche es erneut.';
        }
    };

    const replacePlan = (html, sourceMain) => {
        const parsed = new DOMParser().parseFromString(html, 'text/html');
        const replacement = [...parsed.querySelectorAll('main[data-project-id]')]
            .find(main => main.dataset.projectId === sourceMain.dataset.projectId);
        if (!replacement) return false;

        captureState(sourceMain);
        sourceMain.replaceWith(replacement);
        if (parsed.title) document.title = parsed.title;
        restoreState(replacement);
        document.dispatchEvent(new CustomEvent('projectflow:plan-updated'));
        return true;
    };

    const submitRequest = async (url, body, sourceMain, form = null) => {
        if (requestInFlight) return;
        requestInFlight = true;
        sourceMain.setAttribute('aria-busy', 'true');
        if (form) setBusy(form, true);
        try {
            captureState(sourceMain);
            const response = await fetch(url, {
                method: 'POST',
                body,
                credentials: 'same-origin',
                headers: {
                    'Accept': 'text/html',
                    'X-Requested-With': 'XMLHttpRequest'
                }
            });
            const html = await response.text();
            if (replacePlan(html, sourceMain)) return;

            if (response.redirected) {
                window.location.assign(response.url);
                return;
            }
            showRequestError();
        } catch (_error) {
            showRequestError();
        } finally {
            if (form?.isConnected) setBusy(form, false);
            if (sourceMain.isConnected) sourceMain.removeAttribute('aria-busy');
            requestInFlight = false;
        }
    };

    const submitFields = (url, fields) => {
        const main = planMain();
        if (!main) return;
        const body = new FormData();
        const version = main.querySelector('input[name="projectLockVersion"]')?.value
            || main.dataset.lockVersion;
        body.set('projectLockVersion', version);
        Object.entries(fields).forEach(([name, value]) => body.set(name, value ?? ''));
        const csrf = main.querySelector('input[name="_csrf"]');
        if (csrf) body.set(csrf.name, csrf.value);
        void submitRequest(url, body, main);
    };

    document.addEventListener('submit', event => {
        const form = event.target;
        const main = form.closest('main[data-project-id]');
        if (!main || form.method.toLowerCase() !== 'post') return;

        event.preventDefault();
        const body = event.submitter
            ? new FormData(form, event.submitter)
            : new FormData(form);
        void submitRequest(form.action, body, main, form);
    });

    document.addEventListener('toggle', event => {
        if (event.target.matches('details.plan-section')) captureState(planMain());
    }, true);
    window.addEventListener('pagehide', () => captureState(planMain()));
    document.addEventListener('DOMContentLoaded', () => restoreState(planMain()));

    window.ProjectFlowPlan = { submit: submitFields };
})();
