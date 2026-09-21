(function () {
    'use strict';

    const review = document.querySelector('main[data-sort-mode]');
    let dragged = null;
    let dropIndicator = null;

    function getDropIndicator() {
        if (!dropIndicator) {
            dropIndicator = document.createElement('div');
            dropIndicator.className = 'pf-drop-indicator';
        }
        return dropIndicator;
    }

    function removeDropIndicator() {
        if (dropIndicator && dropIndicator.parentNode) {
            dropIndicator.parentNode.removeChild(dropIndicator);
        }
    }

    const submitMove = (url, fields) => {
        window.ProjectFlowScrollState?.capture();
        const form = document.createElement('form');
        form.method = 'post';
        form.action = url;
        const version = document.querySelector('input[name="lockVersion"]')?.value;
        const csrf = document.querySelector('input[name="_csrf"]');
        const values = { ...fields, lockVersion: version };
        if (csrf) values[csrf.name] = csrf.value;
        Object.entries(values).forEach(([name, value]) => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = value;
            form.append(input);
        });
        document.body.append(form);
        form.submit();
    };

    const isInteractive = (target) => {
        if (target.closest('.pf-drag-handle-visual')) {
            return false;
        }
        return !!target.closest('button, a, input, select, textarea, label, form, .pf-phase-inline-edit, .review-actions-wrap, .pf-element-edit-btn, .pf-dropdown, .pf-card-menu, .pf-phase-toggle-btn');
    };

    let dragNotice = null;
    function showDragDateNotice(text) {
        if (!dragNotice) {
            dragNotice = document.createElement('div');
            dragNotice.className = 'pf-drag-date-notice';
            dragNotice.setAttribute('role', 'status');
            dragNotice.style.cssText = 'position:fixed;bottom:1.5rem;left:50%;transform:translateX(-50%);background:#1e293b;color:#f8fafc;padding:0.5rem 1rem;border-radius:6px;font-size:0.85rem;box-shadow:0 4px 12px rgba(0,0,0,0.15);z-index:9999;pointer-events:none;transition:opacity 0.15s ease;';
            document.body.appendChild(dragNotice);
        }
        dragNotice.textContent = text;
        dragNotice.style.opacity = '1';
        dragNotice.style.display = 'block';
    }

    function hideDragDateNotice() {
        if (dragNotice) {
            dragNotice.style.opacity = '0';
            setTimeout(() => { if (dragNotice) dragNotice.style.display = 'none'; }, 150);
        }
    }

    // 1. Draggable items setup (drag handle initiates drag)
    const handleSelector = '.pf-drag-handle, .pf-drag-handle-visual, .element-drag-handle, .pf-compact-drag-handle, .pf-compact-phase-drag-handle, .drag-handle, .pf-phase-drag-handle, .pf-section-drag-handle';
    document.querySelectorAll('[draggable="true"]').forEach(item => {
        item.querySelectorAll(handleSelector).forEach(handle => {
            handle.addEventListener('pointerdown', () => { item.dataset.dragArmed = 'true'; });
        });
        item.addEventListener('dragstart', event => {
            if (isInteractive(event.target)) {
                event.preventDefault();
                return;
            }
            const isHandle = !!event.target.closest(handleSelector) || item.dataset.dragArmed === 'true';
            if (!isHandle) {
                event.preventDefault();
                return;
            }
            event.stopPropagation();
            dragged = item;
            item.classList.add('is-dragging');
            if (review?.dataset.sortMode === 'DATE' && item.classList.contains('plan-element') && item.dataset.date) {
                showDragDateNotice('Hinweis: Bei Datumssortierung bestimmt das Datum die Reihenfolge.');
            }
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', item.dataset.elementId || item.dataset.sectionId || '');
        });
        item.addEventListener('dragend', () => {
            delete item.dataset.dragArmed;
            item.classList.remove('is-dragging');
            document.querySelectorAll('.drop-target').forEach(target => target.classList.remove('drop-target'));
            removeDropIndicator();
            hideDragDateNotice();
            dragged = null;
        });
        item.addEventListener('pointerup', () => {
            delete item.dataset.dragArmed;
        });
    });

    // Mobile review status dropdown handler
    document.addEventListener('change', event => {
        const select = event.target.closest('.draft-review-status-select');
        if (!select) return;
        const val = select.value;
        let targetUrl = '';
        if (val === 'ACCEPTED') targetUrl = select.dataset.acceptUrl;
        else if (val === 'REJECTED') targetUrl = select.dataset.rejectUrl;
        else if (val === 'PENDING') targetUrl = select.dataset.resetUrl;

        if (targetUrl) {
            submitMove(targetUrl, { lockVersion: select.dataset.lockVersion });
        }
    });

    // 2. Elements drop zones (.plan-elements) with insertion line
    document.querySelectorAll('.plan-elements').forEach(list => {
        list.addEventListener('dragover', event => {
            if (!dragged?.classList.contains('plan-element')) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
            list.classList.add('drop-target');

            const indicator = getDropIndicator();
            const siblings = [...list.querySelectorAll('.plan-element:not(.is-dragging)')];
            const before = siblings.find(item => event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2);
            if (before) {
                list.insertBefore(indicator, before);
            } else {
                list.appendChild(indicator);
            }
        });

        list.addEventListener('dragleave', event => {
            if (!list.contains(event.relatedTarget)) {
                list.classList.remove('drop-target');
                removeDropIndicator();
            }
        });

        list.addEventListener('drop', event => {
            event.preventDefault();
            list.classList.remove('drop-target');
            removeDropIndicator();
            if (!dragged?.classList.contains('plan-element')) return;

            const draggedDate = dragged.dataset.date || '';
            const siblings = [...list.querySelectorAll('.plan-element:not(.is-dragging)')];
            const before = siblings.find(item => event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2);
            const position = before ? siblings.indexOf(before) : siblings.length;

            submitMove(dragged.dataset.moveUrl, {
                targetSectionId: list.dataset.sectionId || '',
                targetDate: draggedDate,
                targetPosition: position
            });
        });
    });

    // 3. Sections drop zone (#draft-sections)
    const sections = document.querySelector('#draft-sections');
    sections?.addEventListener('dragover', event => {
        if (!dragged?.classList.contains('draft-section')) return;
        event.preventDefault();
        sections.classList.add('drop-target');

        const indicator = getDropIndicator();
        const siblings = [...sections.querySelectorAll(':scope > .draft-section:not(.is-dragging)')];
        const before = siblings.find(item => event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2);
        if (before) {
            sections.insertBefore(indicator, before);
        } else {
            sections.appendChild(indicator);
        }
    });

    sections?.addEventListener('dragleave', event => {
        if (!sections.contains(event.relatedTarget)) {
            sections.classList.remove('drop-target');
            removeDropIndicator();
        }
    });

    sections?.addEventListener('drop', event => {
        event.preventDefault();
        sections.classList.remove('drop-target');
        removeDropIndicator();
        if (!dragged?.classList.contains('draft-section')) return;
        const siblings = [...sections.querySelectorAll(':scope > .draft-section:not(.is-dragging)')];
        const before = siblings.find(item => event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2);
        submitMove(dragged.dataset.moveUrl, { targetPosition: before ? siblings.indexOf(before) : siblings.length });
    });

    // 4. Phase collapse behavior: Only toggle when clicking caret (.pf-phase-toggle-btn)
    document.addEventListener('click', event => {
        const toggleBtn = event.target.closest('.pf-phase-toggle-btn');
        if (toggleBtn) {
            event.preventDefault();
            event.stopPropagation();
            const details = toggleBtn.closest('details.draft-section');
            if (details) {
                details.open = !details.open;
            }
            return;
        }

        // Prevent header click from toggling <details>
        const header = event.target.closest('summary.pf-plan-section__header');
        if (header) {
            // If the user clicked interactive buttons or forms inside summary, don't interfere
            if (event.target.closest('button, input, textarea, a, form, .pf-phase-inline-edit')) {
                return;
            }
            event.preventDefault();
        }
    });

    // 5. Inline Phase Editing (Reveal / Cancel)
    document.addEventListener('click', event => {
        const editBtn = event.target.closest('.pf-phase-edit-btn');
        if (editBtn) {
            event.preventDefault();
            event.stopPropagation();
            const section = editBtn.closest('.draft-section');
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

        const cancelBtn = event.target.closest('.pf-phase-cancel-btn');
        if (cancelBtn) {
            event.preventDefault();
            event.stopPropagation();
            const section = cancelBtn.closest('.draft-section');
            if (section) {
                const displayWrap = section.querySelector('.pf-phase-display-wrap');
                const editForm = section.querySelector('.pf-phase-inline-edit');
                if (editForm) editForm.hidden = true;
                if (displayWrap) displayWrap.hidden = false;
            }
        }
    });

    // 6. Make entire task/milestone card clickable to open detail page
    document.addEventListener('click', event => {
        const card = event.target.closest('.plan-element[data-detail-url]');
        if (!card) return;

        // Do not navigate if clicking an interactive control
        if (event.target.closest('button, input, textarea, select, a, label, form, .drag-handle, .pf-drag-handle-visual, .pf-compact-drag-handle, .review-actions, .review-actions-wrap, .pf-element-edit-btn, summary')) {
            return;
        }

        const url = card.dataset.detailUrl;
        if (url) {
            window.location.href = url;
        }
    });

    // 7. Regenerate Plan Modal Dialog wiring
    const regenBtn = document.getElementById('open-regenerate-modal-btn');
    const regenDialog = document.getElementById('regenerate-plan-dialog');
    const closeRegenBtn = document.getElementById('close-regenerate-modal-btn');
    const cancelRegenBtn = document.getElementById('cancel-regenerate-modal-btn');

    if (regenBtn && regenDialog) {
        regenBtn.addEventListener('click', () => {
            if (typeof regenDialog.showModal === 'function') {
                regenDialog.showModal();
            } else {
                regenDialog.setAttribute('open', '');
            }
        });

        const closeDialog = () => {
            if (typeof regenDialog.close === 'function') {
                regenDialog.close();
            } else {
                regenDialog.removeAttribute('open');
            }
        };

        closeRegenBtn?.addEventListener('click', closeDialog);
        cancelRegenBtn?.addEventListener('click', closeDialog);

        regenDialog.addEventListener('click', event => {
            if (event.target === regenDialog) closeDialog();
        });
    }

    // 8. Auto-dismiss success/info alerts on next relevant interaction
    document.addEventListener('click', event => {
        if (event.target.closest('.pf-alert__close')) return;
        if (event.target.closest('button, a, input, select, textarea, .plan-element, .pf-dropdown__item')) {
            document.querySelectorAll('.pf-alert--success, .pf-alert--info').forEach(alert => {
                alert.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
                alert.style.opacity = '0';
                setTimeout(() => alert.remove(), 200);
            });
        }
    }, { capture: true });

    // 9. Auto-collapse toolbar on mobile devices (Task 11)
    if (window.innerWidth <= 640) {
        document.querySelectorAll('.pf-toolbar-collapse[open]').forEach(el => el.removeAttribute('open'));
    }

})();
