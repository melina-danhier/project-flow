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
        if (!busy) {
            delete form.dataset.submitting;
        }
        form.querySelectorAll('button[type="submit"], input[type="submit"], button:not([type])').forEach(control => {
            control.disabled = busy;
            if (!busy) {
                control.removeAttribute('aria-disabled');
                control.classList.remove('is-loading');
            }
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
            if (response.ok) {
                const html = await response.text();
                if (replacePlan(html, sourceMain)) return;
            }

            if (response.redirected) {
                window.location.assign(response.url);
                return;
            }
            showRequestError();
            document.dispatchEvent(new CustomEvent('projectflow:plan-error'));
        } catch (_error) {
            showRequestError();
            document.dispatchEvent(new CustomEvent('projectflow:plan-error'));
        } finally {
            if (form?.isConnected) setBusy(form, false);
            if (sourceMain.isConnected) sourceMain.removeAttribute('aria-busy');
            requestInFlight = false;
            if (window.projectFlowReenableButtons) {
                window.projectFlowReenableButtons(form);
            } else {
                document.querySelectorAll('form[data-submitting]').forEach(f => delete f.dataset.submitting);
                document.querySelectorAll('button.is-loading, input.is-loading, button[aria-disabled="true"]').forEach(btn => {
                    btn.disabled = false;
                    btn.removeAttribute('aria-disabled');
                    btn.classList.remove('is-loading');
                });
            }
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
                if (window.closePfDropdown) {
                    window.closePfDropdown(dropdown);
                } else {
                    dropdown.removeAttribute('open');
                }
            }
        });
    });

    // Phase collapse behavior: Only toggle when clicking caret (.pf-phase-toggle-btn)
    document.addEventListener('click', event => {
        const phaseMenuOrEdit = event.target.closest('.pf-phase-menu, .pf-phase-inline-edit');
        if (phaseMenuOrEdit) {
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
            if (menu) {
                if (window.closePfDropdown) window.closePfDropdown(menu);
                else menu.removeAttribute('open');
            }
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
                if (actionsDropdown) {
                    if (window.closePfDropdown) window.closePfDropdown(actionsDropdown);
                    else actionsDropdown.removeAttribute('open');
                }
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

        const moveElementDialog = document.getElementById('move-element-dialog');
        const moveElementForm = document.getElementById('move-element-form');
        const closeMoveElementBtn = document.getElementById('close-move-element-btn');
        const moveTargetSectionSelect = document.getElementById('move-target-section-select');
        const moveElementTitleDisplay = document.getElementById('move-element-title-display');
        const moveElementTargetPosition = document.getElementById('move-element-target-position');
        const moveElementTargetDate = document.getElementById('move-element-target-date');

        if (closeMoveElementBtn && moveElementDialog) {
            closeMoveElementBtn.onclick = () => moveElementDialog.close();
        }

        if (moveTargetSectionSelect && moveElementTargetPosition) {
            moveTargetSectionSelect.onchange = () => {
                const selected = moveTargetSectionSelect.selectedOptions[0];
                if (selected) {
                    moveElementTargetPosition.value = selected.dataset.count || '0';
                }
            };
        }

        [newSectionDialog, deleteSectionDialog, moveElementDialog].forEach(dialog => {
            if (dialog) {
                dialog.addEventListener('click', event => {
                    if (event.target === dialog) dialog.close();
                });
            }
        });

        document.addEventListener('click', event => {
            const moveBtn = event.target.closest('.open-move-dialog-btn');
            if (moveBtn && moveElementDialog && moveElementForm) {
                event.preventDefault();
                event.stopPropagation();
                const menu = moveBtn.closest('details.pf-item-menu, details.pf-dropdown');
                if (menu) {
                    if (window.closePfDropdown) window.closePfDropdown(menu);
                    else menu.removeAttribute('open');
                }

                const title = moveBtn.dataset.elementTitle || 'Element';
                const moveUrl = moveBtn.dataset.moveUrl;
                const currentSectionId = moveBtn.dataset.currentSectionId || '';
                const elementDate = moveBtn.dataset.date || '';

                moveElementForm.action = moveUrl;
                if (moveElementTargetDate) moveElementTargetDate.value = elementDate;
                if (moveElementTitleDisplay) {
                    moveElementTitleDisplay.textContent = `Wähle den Zielbereich für „${title}“ aus:`;
                }

                if (moveTargetSectionSelect) {
                    Array.from(moveTargetSectionSelect.options).forEach(opt => {
                        const isCurrent = (opt.value === currentSectionId);
                        opt.disabled = isCurrent;
                        const baseTitle = opt.dataset.baseTitle || opt.text.replace(' (aktuell)', '');
                        opt.dataset.baseTitle = baseTitle;
                        opt.text = isCurrent ? `${baseTitle} (aktuell)` : baseTitle;
                    });
                    const firstAvailable = Array.from(moveTargetSectionSelect.options).find(opt => !opt.disabled);
                    if (firstAvailable) {
                        moveTargetSectionSelect.value = firstAvailable.value;
                        if (moveElementTargetPosition) {
                            moveElementTargetPosition.value = firstAvailable.dataset.count || '0';
                        }
                    }
                }

                moveElementDialog.showModal();
                return;
            }

            const deleteBtn = event.target.closest('.pf-phase-delete-trigger');
            if (deleteBtn && deleteSectionDialog && deleteSectionForm) {
                event.preventDefault();
                event.stopPropagation();
                const menu = deleteBtn.closest('details.pf-phase-menu');
                if (menu) {
                    if (window.closePfDropdown) window.closePfDropdown(menu);
                    else menu.removeAttribute('open');
                }

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

    // Task 1: Make whole element card clickable to navigate to task / milestone detail page
    document.addEventListener('click', event => {
        const card = event.target.closest('.plan-element[data-detail-url], .pf-element-item[data-detail-url]');
        if (!card) return;

        // If user is selecting text, do not navigate
        const selection = window.getSelection();
        if (selection && selection.toString().trim().length > 0) {
            return;
        }

        // Check if the click target is or is inside an interactive control within this card
        const interactive = event.target.closest(
            'a, button, input, select, textarea, label, summary, form, ' +
            '.element-drag-handle, .pf-drag-handle-visual, .pf-drag-handle, .drag-handle, ' +
            '.pf-dropdown, .pf-item-menu, .pf-card-menu, .element-move-btn, .open-move-dialog-btn'
        );
        if (interactive && card.contains(interactive)) {
            return;
        }

        const url = card.dataset.detailUrl;
        if (url) {
            window.location.href = url;
        }
    });

    // Keyboard support for focused cards
    document.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
            const card = document.activeElement;
            if (card && card.matches('.pf-element-item[data-detail-url]') && event.target === card) {
                event.preventDefault();
                const url = card.dataset.detailUrl;
                if (url) window.location.href = url;
            }
        }
    });

    // Filters setup & handling (Status + Assignment filter)
    function applyFilters() {
        const main = planMain();
        if (!main) return;

        const statusSelect = document.getElementById('plan-task-filter-select');
        const assignSelect = document.getElementById('plan-assignment-filter-select');

        const statusFilter = statusSelect ? statusSelect.value : 'ALL';
        const assignmentFilter = assignSelect ? assignSelect.value : 'ALL';

        try {
            sessionStorage.setItem(`projectflow:task-filter:${main.dataset.projectId}`, statusFilter);
            sessionStorage.setItem(`projectflow:assignment-filter:${main.dataset.projectId}`, assignmentFilter);
        } catch (_e) {}

        const elements = main.querySelectorAll('.plan-element');
        elements.forEach(el => {
            const type = el.dataset.elementType;
            if (type === 'TASK') {
                const status = el.dataset.taskStatus || 'OPEN';
                const completed = el.dataset.taskCompleted === 'true';

                let matchesStatus = true;
                if (statusFilter === 'OPEN') {
                    matchesStatus = (status === 'OPEN' && !completed);
                } else if (statusFilter === 'IN_PROGRESS') {
                    matchesStatus = (status === 'IN_PROGRESS');
                } else if (statusFilter === 'COMPLETED') {
                    matchesStatus = (status === 'COMPLETED' || completed);
                } else if (statusFilter === 'UNCOMPLETED') {
                    matchesStatus = (status !== 'COMPLETED' && !completed);
                }

                let matchesAssignment = true;
                if (assignmentFilter === 'MINE') {
                    matchesAssignment = (el.dataset.assignedMe === 'true');
                } else if (assignmentFilter === 'UNASSIGNED') {
                    matchesAssignment = (el.dataset.hasAssignees === 'false');
                }

                el.style.display = (matchesStatus && matchesAssignment) ? '' : 'none';
            } else if (type === 'MILESTONE') {
                const completed = el.dataset.milestoneCompleted === 'true' || el.dataset.taskCompleted === 'true';
                let matchesStatus = true;
                if (statusFilter === 'OPEN' || statusFilter === 'UNCOMPLETED') {
                    matchesStatus = !completed;
                } else if (statusFilter === 'COMPLETED') {
                    matchesStatus = completed;
                } else if (statusFilter === 'IN_PROGRESS') {
                    matchesStatus = false;
                }
                // Assignment filter does not change milestone visibility
                el.style.display = matchesStatus ? '' : 'none';
            }
        });

        document.dispatchEvent(new CustomEvent('projectflow:filters-changed', {
            detail: { statusFilter, assignmentFilter }
        }));
    }

    function setupFilters() {
        const statusSelect = document.getElementById('plan-task-filter-select');
        const assignSelect = document.getElementById('plan-assignment-filter-select');
        const main = planMain();
        if (!main) return;

        if (statusSelect) {
            let saved = 'ALL';
            try {
                saved = sessionStorage.getItem(`projectflow:task-filter:${main.dataset.projectId}`) || 'ALL';
            } catch (_e) {}
            statusSelect.value = saved;
            statusSelect.onchange = () => applyFilters();
        }

        if (assignSelect) {
            let savedAssign = 'ALL';
            try {
                savedAssign = sessionStorage.getItem(`projectflow:assignment-filter:${main.dataset.projectId}`) || 'ALL';
            } catch (_e) {}
            assignSelect.value = savedAssign;
            assignSelect.onchange = () => applyFilters();
        }

        applyFilters();
    }


    const setupMobileCollapses = () => {
        if (window.innerWidth <= 640) {
            document.querySelectorAll('.pf-plan-control-collapse[open]').forEach(el => el.removeAttribute('open'));
        }
    };

    const updateProjectActionsDropdownClass = () => {
        const dropdown = document.getElementById('project-actions-dropdown');
        if (!dropdown) return;
        if (window.innerWidth <= 640) {
            dropdown.classList.remove('pf-dropdown--right');
            dropdown.classList.add('pf-dropdown--left');
        } else {
            dropdown.classList.remove('pf-dropdown--left');
            dropdown.classList.add('pf-dropdown--right');
        }
    };

    window.addEventListener('resize', updateProjectActionsDropdownClass);

    document.addEventListener('DOMContentLoaded', () => {
        setupPlanModals();
        setupFilters();
        setupMobileCollapses();
        updateProjectActionsDropdownClass();
    });
    document.addEventListener('projectflow:plan-updated', () => {
        setupPlanModals();
        setupFilters();
        setupMobileCollapses();
        updateProjectActionsDropdownClass();
    });

    window.ProjectFlowPlan = { submit: submitFields };
})();
