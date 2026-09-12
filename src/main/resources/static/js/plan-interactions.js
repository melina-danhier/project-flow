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
        if (!main || form.method.toLowerCase() !== 'post' || form.hasAttribute('data-native-submit')) return;

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

    // Global dropdown click-outside closer
    document.addEventListener('click', event => {
        document.querySelectorAll('details.pf-dropdown[open]').forEach(dropdown => {
            if (!dropdown.contains(event.target)) {
                dropdown.removeAttribute('open');
            }
        });
    });

    // Phase collapse behavior: Only toggle when clicking caret (.pf-phase-toggle-btn)
    document.addEventListener('click', event => {
        const phaseMenuOrEdit = event.target.closest('.pf-phase-menu, .pf-phase-inline-edit');
        if (phaseMenuOrEdit) {
            event.stopPropagation();
            return;
        }

        const toggleBtn = event.target.closest('.pf-phase-toggle-btn');
        if (toggleBtn) {
            event.preventDefault();
            const details = toggleBtn.closest('details.plan-section');
            if (details) {
                details.open = !details.open;
                captureState(planMain());
            }
            return;
        }

        const header = event.target.closest('.pf-plan-section__header');
        if (header) {
            event.preventDefault();
        }
    });

    // Phase Inline Edit: Reveal edit form on 'Bearbeiten'
    document.addEventListener('click', event => {
        const editBtn = event.target.closest('.pf-phase-edit-btn');
        if (editBtn) {
            event.preventDefault();
            event.stopPropagation();
            const section = editBtn.closest('details.plan-section');
            const menu = editBtn.closest('details.pf-phase-menu');
            if (menu) menu.removeAttribute('open');
            if (section) {
                const displayWrap = section.querySelector('.pf-phase-display-wrap');
                const editForm = section.querySelector('.pf-phase-inline-edit');
                if (displayWrap) displayWrap.hidden = true;
                if (editForm) {
                    editForm.hidden = false;
                    const input = editForm.querySelector('.pf-phase-edit-title');
                    if (input) {
                        input.focus();
                        input.select();
                    }
                }
            }
            return;
        }

        // Phase Inline Edit: Cancel button
        const cancelBtn = event.target.closest('.pf-phase-cancel-btn');
        if (cancelBtn) {
            event.preventDefault();
            event.stopPropagation();
            const section = cancelBtn.closest('details.plan-section');
            if (section) {
                const displayWrap = section.querySelector('.pf-phase-display-wrap');
                const editForm = section.querySelector('.pf-phase-inline-edit');
                if (editForm) editForm.hidden = true;
                if (displayWrap) displayWrap.hidden = false;
            }
            return;
        }
    });

    // Modals: New Section & Delete Section & Back to Top
    function setupPlanModals() {
        const newSectionDialog = document.getElementById('new-section-dialog');
        const openNewSectionBtn = document.getElementById('open-new-section-btn');
        const closeNewSectionBtn = document.getElementById('close-new-section-btn');

        if (openNewSectionBtn && newSectionDialog) {
            openNewSectionBtn.onclick = () => {
                const actionsDropdown = document.getElementById('project-actions-dropdown');
                if (actionsDropdown) actionsDropdown.removeAttribute('open');
                newSectionDialog.showModal();
                newSectionDialog.querySelector('#new-section-title')?.focus();
            };
        }
        if (closeNewSectionBtn && newSectionDialog) {
            closeNewSectionBtn.onclick = () => {
                newSectionDialog.close();
            };
        }
        if (newSectionDialog && newSectionDialog.querySelector('.pf-alert--error')) {
            newSectionDialog.showModal();
        }

        const deleteSectionDialog = document.getElementById('delete-section-dialog');
        const deleteSectionForm = document.getElementById('delete-section-form');
        const closeDeleteSectionBtn = document.getElementById('close-delete-section-btn');
        const deleteModeSelect = document.getElementById('delete-mode-select');
        const targetSectionWrap = document.getElementById('delete-section-target-wrap');
        const targetSectionSelect = document.getElementById('target-section-select');
        const deleteSectionTitleDisplay = document.getElementById('delete-section-title-display');
        const deleteSectionDescDisplay = document.getElementById('delete-section-desc-display');
        const deleteSectionHandlingGroup = document.getElementById('delete-section-handling-group');

        if (deleteModeSelect && targetSectionWrap) {
            deleteModeSelect.onchange = () => {
                const isMove = deleteModeSelect.value === 'MOVE_CONTENT';
                targetSectionWrap.hidden = !isMove;
                if (targetSectionSelect) {
                    targetSectionSelect.required = isMove;
                }
            };
        }

        if (closeDeleteSectionBtn && deleteSectionDialog) {
            closeDeleteSectionBtn.onclick = () => {
                deleteSectionDialog.close();
            };
        }

        document.addEventListener('click', event => {
            const deleteBtn = event.target.closest('.pf-phase-delete-trigger');
            if (deleteBtn && deleteSectionDialog && deleteSectionForm) {
                event.preventDefault();
                event.stopPropagation();
                const menu = deleteBtn.closest('details.pf-phase-menu');
                if (menu) menu.removeAttribute('open');

                const sectionId = deleteBtn.dataset.sectionId;
                const sectionTitle = deleteBtn.dataset.sectionTitle || 'Bereich';
                const count = parseInt(deleteBtn.dataset.elementCount || '0', 10);
                const projectId = document.getElementById('project-id-holder')?.value
                    || document.querySelector('main[data-project-id]')?.dataset.projectId;

                deleteSectionForm.action = `/projects/${projectId}/sections/${sectionId}/delete`;
                if (deleteSectionTitleDisplay) {
                    deleteSectionTitleDisplay.textContent = `Bereich „${sectionTitle}“ löschen`;
                }

                if (count > 0) {
                    if (deleteSectionDescDisplay) {
                        deleteSectionDescDisplay.textContent = `Dieser Bereich enthält ${count} ${count === 1 ? 'Element' : 'Elemente'}. Was möchtest du mit den Inhalten tun?`;
                    }
                    if (deleteSectionHandlingGroup) deleteSectionHandlingGroup.hidden = false;
                    if (deleteModeSelect) deleteModeSelect.value = 'DELETE_CONTENT';
                    if (targetSectionWrap) targetSectionWrap.hidden = true;
                    if (targetSectionSelect) {
                        targetSectionSelect.required = false;
                        Array.from(targetSectionSelect.options).forEach(opt => {
                            opt.hidden = (opt.value === sectionId);
                            opt.disabled = (opt.value === sectionId);
                        });
                        targetSectionSelect.value = '';
                    }
                } else {
                    if (deleteSectionDescDisplay) {
                        deleteSectionDescDisplay.textContent = `Möchtest du diesen Bereich wirklich löschen?`;
                    }
                    if (deleteSectionHandlingGroup) deleteSectionHandlingGroup.hidden = true;
                    if (deleteModeSelect) deleteModeSelect.value = 'DELETE_CONTENT';
                    if (targetSectionWrap) targetSectionWrap.hidden = true;
                }

                deleteSectionDialog.showModal();
            }
        });

        // Back to top button
        const backToTopBtn = document.getElementById('pf-back-to-top-btn');
        if (backToTopBtn) {
            backToTopBtn.onclick = () => {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            };
        }
    }

    document.addEventListener('DOMContentLoaded', setupPlanModals);
    document.addEventListener('projectflow:plan-updated', setupPlanModals);

    window.ProjectFlowPlan = { submit: submitFields };
})();
