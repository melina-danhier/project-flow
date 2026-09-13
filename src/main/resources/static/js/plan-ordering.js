const initializePlanOrdering = () => {
    const main = document.querySelector('main[data-sort-mode]');
    if (!main || main.dataset.editable !== 'true' || main.dataset.orderingInitialized === 'true') return;
    main.dataset.orderingInitialized = 'true';

    let dragged = null;

    const submitMove = (url, fields) => {
        if (window.ProjectFlowPlan?.submit) {
            window.ProjectFlowPlan.submit(url, fields);
            return;
        }
        const form = document.createElement('form');
        form.method = 'post';
        form.action = url;
        const version = document.querySelector('input[name="projectLockVersion"]')?.value || main.dataset.lockVersion;
        const csrf = document.querySelector('input[name="_csrf"]');
        const values = { ...fields, projectLockVersion: version };
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

    let dropIndicator = null;
    const getDropIndicator = () => {
        if (!dropIndicator) {
            dropIndicator = document.createElement('div');
            dropIndicator.className = 'pf-drop-indicator';
        }
        return dropIndicator;
    };
    const removeDropIndicator = () => {
        if (dropIndicator && dropIndicator.parentNode) {
            dropIndicator.remove();
        }
    };

    const isInteractive = (target) => {
        return !!target.closest('button, a, input, select, textarea, label, form, details, summary, .pf-phase-inline-edit, .pf-dropdown, .pf-card-menu');
    };

    // 1. Draggable items setup (Sections and Elements - whole container)
    document.querySelectorAll('[draggable="true"]').forEach(item => {
        item.addEventListener('dragstart', event => {
            if (isInteractive(event.target)) {
                event.preventDefault();
                return;
            }
            event.stopPropagation();
            dragged = item;
            item.classList.add('is-dragging');
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', item.dataset.elementId || item.dataset.sectionId || '');
        });

        item.addEventListener('dragend', () => {
            item.classList.remove('is-dragging');
            document.querySelectorAll('.drop-target').forEach(target => target.classList.remove('drop-target'));
            removeDropIndicator();
            dragged = null;
        });
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

    // 3. Sections drop zone (#plan-sections) with insertion line
    const sectionsContainer = document.querySelector('#plan-sections');
    if (sectionsContainer) {
        sectionsContainer.addEventListener('dragover', event => {
            if (!dragged?.classList.contains('plan-section')) return;
            event.preventDefault();
            sectionsContainer.classList.add('drop-target');

            const indicator = getDropIndicator();
            const siblings = [...sectionsContainer.querySelectorAll(':scope > .plan-section:not(.is-dragging)')];
            const before = siblings.find(item => event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2);
            if (before) {
                sectionsContainer.insertBefore(indicator, before);
            } else {
                sectionsContainer.appendChild(indicator);
            }
        });

        sectionsContainer.addEventListener('dragleave', event => {
            if (!sectionsContainer.contains(event.relatedTarget)) {
                sectionsContainer.classList.remove('drop-target');
                removeDropIndicator();
            }
        });

        sectionsContainer.addEventListener('drop', event => {
            event.preventDefault();
            sectionsContainer.classList.remove('drop-target');
            removeDropIndicator();
            if (!dragged?.classList.contains('plan-section')) return;

            const siblings = [...sectionsContainer.querySelectorAll(':scope > .plan-section:not(.is-dragging)')];
            const before = siblings.find(item => event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2);
            const position = before ? siblings.indexOf(before) : siblings.length;

            submitMove(dragged.dataset.moveUrl, { targetPosition: position });
        });
    }

    // 4. Accessible buttons for elements (Up / Down)
    document.querySelectorAll('.element-move-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const elementItem = btn.closest('.plan-element');
            if (!elementItem) return;
            const list = elementItem.closest('.plan-elements');
            if (!list) return;

            const elementDate = elementItem.dataset.date || '';
            const siblings = [...list.querySelectorAll('.plan-element')];

            const currentIndex = siblings.indexOf(elementItem);
            if (currentIndex === -1) return;

            let targetPosition;
            if (btn.classList.contains('element-move-up')) {
                if (currentIndex === 0) return;
                targetPosition = currentIndex - 1;
            } else if (btn.classList.contains('element-move-down')) {
                if (currentIndex >= siblings.length - 1) return;
                targetPosition = currentIndex + 1;
            } else {
                return;
            }

            submitMove(elementItem.dataset.moveUrl, {
                targetSectionId: list.dataset.sectionId || '',
                targetDate: elementDate,
                targetPosition: targetPosition
            });
        });
    });
};

document.addEventListener('DOMContentLoaded', initializePlanOrdering);
document.addEventListener('projectflow:plan-updated', initializePlanOrdering);
