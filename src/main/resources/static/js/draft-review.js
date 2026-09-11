const review = document.querySelector('main[data-sort-mode]');
let dragged = null;

const submitMove = (url, fields) => {
    window.ProjectFlowScrollState?.capture();
    const form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    const version = document.querySelector('input[name="lockVersion"]')?.value;
    const csrf = document.querySelector('input[name="_csrf"]');
    const values = {...fields, lockVersion: version};
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

document.querySelectorAll('[draggable="true"]').forEach(item => {
    const requiredHandle = item.classList.contains('draft-section') ? '.section-drag-handle' : '.element-drag-handle';
    item.querySelector(requiredHandle)?.addEventListener('pointerdown', () => item.dataset.dragArmed = 'true');
    item.addEventListener('dragstart', event => {
        if (item.dataset.dragArmed !== 'true') {
            event.preventDefault();
            return;
        }
        event.stopPropagation();
        dragged = item;
        item.classList.add('is-dragging');
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', item.dataset.elementId || item.dataset.sectionId);
    });
    item.addEventListener('dragend', () => {
        delete item.dataset.dragArmed;
        item.classList.remove('is-dragging');
        document.querySelectorAll('.drop-target').forEach(target => target.classList.remove('drop-target'));
        dragged = null;
    });
});

document.addEventListener('pointerup', () => {
    document.querySelectorAll('[data-drag-armed]').forEach(item => delete item.dataset.dragArmed);
});

document.querySelectorAll('.plan-elements').forEach(list => {
    list.addEventListener('dragover', event => {
        if (!dragged?.classList.contains('plan-element')) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        list.classList.add('drop-target');
    });
    list.addEventListener('dragleave', event => {
        if (!list.contains(event.relatedTarget)) list.classList.remove('drop-target');
    });
    list.addEventListener('drop', event => {
        event.preventDefault();
        list.classList.remove('drop-target');
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

const sections = document.querySelector('#draft-sections');
sections?.addEventListener('dragover', event => {
    if (!dragged?.classList.contains('draft-section')) return;
    event.preventDefault();
    sections.classList.add('drop-target');
});
sections?.addEventListener('drop', event => {
    event.preventDefault();
    sections.classList.remove('drop-target');
    if (!dragged?.classList.contains('draft-section')) return;
    const siblings = [...sections.querySelectorAll(':scope > .draft-section:not(.is-dragging)')];
    const before = siblings.find(item => event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2);
    submitMove(dragged.dataset.moveUrl, {targetPosition: before ? siblings.indexOf(before) : siblings.length});
});
