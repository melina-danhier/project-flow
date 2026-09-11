(function () {
    'use strict';

function initializePlanViews() {
    var switcher = document.querySelector('.pf-plan-view-switcher');
    var alternativeView = document.getElementById('plan-alternative-view');
    var sections = document.getElementById('plan-sections') || document.getElementById('draft-sections');
    var unsectioned = document.querySelector('.plan-section.unsectioned, .draft-section.unsectioned');
    if (!switcher || !alternativeView || !sections) return;
    if (switcher.dataset.initialized === 'true') return;
    switcher.dataset.initialized = 'true';

    var monthCursor;
    var cardPage = 0;
    var main = document.querySelector('main[data-project-id]');
    var projectId = main.dataset.projectId;
    var isDraft = Boolean(main.dataset.draftStatus);
    var progressDisplay = main.dataset.progressDisplay || 'STATUS';
    var editable = isDraft ? main.dataset.draftStatus !== 'APPLIED' : main.dataset.editable === 'true';
    var storageKey = 'projectflow.planView.' + projectId;

    function text(element, selector) {
        var match = element.querySelector(selector);
        return match ? match.textContent.trim() : '';
    }

    function collectPhases() {
        var phaseNodes = Array.from(sections.querySelectorAll(':scope > .plan-phase'));
        if (unsectioned) phaseNodes.push(unsectioned);
        return phaseNodes.map(function (phase) {
            var header = phase.querySelector('.pf-plan-section__header, .section-heading');
            var title = phase.classList.contains('unsectioned')
                ? (isDraft ? 'Ohne Bereich' : 'Ohne Phase')
                : (phase.dataset.sectionTitle || text(header, '.section-title h2') || text(header, 'strong'));
            var description = phase.dataset.sectionDescription || (header ? text(header, '.section-title p, p') : '');
            var elements = Array.from(phase.querySelectorAll(':scope > .plan-elements > .plan-element'))
                .map(function (element) {
                    var taskLink = element.querySelector('.pf-element-item__left a[href*="/tasks/"]');
                    var milestoneLink = element.querySelector('.pf-element-item__left a[href*="/milestones/"]');
                    var link = taskLink || milestoneLink;
                    var draftTitle = text(element, '.element-heading a, .element-heading strong');
                    var draftType = text(element, '.element-heading .element-type');
                    var type = element.dataset.elementType
                        ? element.dataset.elementType.toLowerCase()
                        : (taskLink || draftType === 'Aufgabe' ? 'task' : 'milestone');
                    return {
                        title: element.dataset.elementTitle || (link ? link.textContent.trim() : draftTitle),
                        href: element.dataset.detailUrl || (link ? link.getAttribute('href') : '/projects/' + projectId + '/draft/'
                            + (type === 'task' ? 'tasks/' : 'milestones/') + element.dataset.elementId),
                        type: type,
                        date: element.dataset.date || '',
                        dateLabel: text(element, '.pf-badge--outline') || text(element, '.element-facts time'),
                        state: text(element, '.pf-badge--gray') || text(element, '.review-status'),
                        completed: element.dataset.taskCompleted === 'true',
                        lockVersion: element.dataset.lockVersion || '',
                        elementId: element.dataset.elementId,
                        sectionId: element.dataset.sectionId || '',
                        moveUrl: element.dataset.moveUrl || ''
                    };
                });
            return {
                title: title,
                description: description,
                elements: elements,
                sectionId: phase.dataset.sectionId || '',
                moveUrl: phase.dataset.moveUrl || ''
            };
        });
    }

    function submit(url, fields) {
        window.ProjectFlowScrollState?.capture();
        if (!isDraft && window.ProjectFlowPlan?.submit) {
            window.ProjectFlowPlan.submit(url, fields);
            return;
        }
        var form = document.createElement('form');
        form.method = 'post';
        form.action = url;
        var values = Object.assign({}, fields);
        values[isDraft ? 'lockVersion' : 'projectLockVersion'] = isDraft
            ? document.getElementById('draft-lock-version')?.value
            : main.dataset.lockVersion;
        var csrf = document.getElementById('draft-csrf') || document.querySelector('input[name="_csrf"]');
        if (csrf) values[csrf.name] = csrf.value;
        Object.entries(values).forEach(function (entry) {
            var input = document.createElement('input');
            input.type = 'hidden'; input.name = entry[0]; input.value = entry[1]; form.appendChild(input);
        });
        document.body.appendChild(form);
        form.submit();
    }

    function elementCard(item) {
        var li = document.createElement('li');
        li.className = 'pf-plan-compact-element pf-plan-compact-element--' + item.type;
        li.dataset.elementId = item.elementId;
        li.dataset.sectionId = item.sectionId;
        li.dataset.date = item.date;
        li.dataset.moveUrl = item.moveUrl;
        if (editable) {
            li.draggable = true;
            var handle = document.createElement('button');
            handle.type = 'button'; handle.className = 'pf-compact-drag-handle';
            handle.textContent = '↕'; handle.title = 'Element verschieben'; handle.setAttribute('aria-label', 'Element verschieben');
            li.appendChild(handle);
        }
        var leading = null;
        if (item.type === 'milestone') {
            leading = document.createElement('span');
            leading.className = 'pf-plan-compact-element__icon';
            leading.textContent = '◆';
        } else if (!isDraft && progressDisplay === 'CHECKBOX') {
            var completion = document.createElement('input');
            completion.type = 'checkbox';
            completion.checked = item.completed;
            completion.setAttribute('aria-label', (item.completed ? 'Aufgabe als offen markieren: ' : 'Aufgabe als erledigt markieren: ') + item.title);
            completion.addEventListener('change', function () {
                completion.disabled = true;
                window.ProjectFlowPlan?.submit('/projects/' + projectId + '/tasks/' + item.elementId + '/completion', {
                    completed: completion.checked,
                    taskLockVersion: item.lockVersion
                });
            });
            leading = completion;
        }
        var content = document.createElement('div');
        var link = document.createElement('a');
        link.href = item.href;
        link.textContent = item.title;
        content.appendChild(link);
        if (item.dateLabel || item.state) {
            var meta = document.createElement('small');
            meta.textContent = [item.dateLabel, item.state].filter(Boolean).join(' · ');
            content.appendChild(meta);
        }
        if (leading) li.appendChild(leading);
        li.appendChild(content);
        return li;
    }

    function phaseCard(phase, board) {
        var article = document.createElement('article');
        article.className = board ? 'pf-board-column' : 'pf-phase-card';
        article.dataset.sectionId = phase.sectionId;
        article.dataset.moveUrl = phase.moveUrl;
        if (editable && phase.moveUrl) article.draggable = true;
        var header = document.createElement('header');
        var title = document.createElement('h3');
        title.textContent = phase.title;
        if (editable && phase.moveUrl) {
            var handle = document.createElement('button');
            handle.type = 'button'; handle.className = 'pf-compact-phase-drag-handle';
            handle.textContent = '↕'; handle.title = 'Phase verschieben'; handle.setAttribute('aria-label', 'Phase verschieben');
            header.appendChild(handle);
        }
        var count = document.createElement('span');
        count.className = 'pf-badge pf-badge--blue';
        count.textContent = phase.elements.length + (phase.elements.length === 1 ? ' Element' : ' Elemente');
        header.append(title, count);
        if (phase.description) {
            var description = document.createElement('p');
            description.textContent = phase.description;
            header.appendChild(description);
        }
        var list = document.createElement('ul');
        list.className = 'pf-plan-compact-list';
        list.dataset.sectionId = phase.sectionId;
        if (!phase.elements.length) {
            var empty = document.createElement('li');
            empty.className = 'pf-plan-view-empty';
            empty.textContent = 'Noch keine Planelemente';
            list.appendChild(empty);
        } else {
            phase.elements.forEach(function (item) { list.appendChild(elementCard(item)); });
        }
        article.append(header, list);
        return article;
    }

    function renderCards(phases) {
        var pageSize = 6;
        var pageCount = Math.max(1, Math.ceil(phases.length / pageSize));
        cardPage = Math.min(cardPage, pageCount - 1);
        var wrapper = document.createElement('div');
        var grid = document.createElement('div');
        grid.className = 'pf-phase-card-grid';
        phases.slice(cardPage * pageSize, (cardPage + 1) * pageSize)
            .forEach(function (phase) { grid.appendChild(phaseCard(phase, false)); });
        wrapper.appendChild(grid);
        if (pageCount > 1) {
            var pagination = document.createElement('nav');
            pagination.className = 'pf-plan-pagination';
            pagination.setAttribute('aria-label', 'Kartenseiten');
            var previous = document.createElement('button');
            previous.type = 'button'; previous.className = 'pf-btn pf-btn--sm pf-btn--outline';
            previous.textContent = 'Zurück'; previous.disabled = cardPage === 0;
            previous.addEventListener('click', function () { cardPage--; showView('cards'); });
            var status = document.createElement('span');
            status.textContent = 'Seite ' + (cardPage + 1) + ' von ' + pageCount;
            var next = document.createElement('button');
            next.type = 'button'; next.className = 'pf-btn pf-btn--sm pf-btn--outline';
            next.textContent = 'Weiter'; next.disabled = cardPage === pageCount - 1;
            next.addEventListener('click', function () { cardPage++; showView('cards'); });
            pagination.append(previous, status, next);
            wrapper.appendChild(pagination);
        }
        return wrapper;
    }

    function renderBoard(phases) {
        var board = document.createElement('div');
        board.className = 'pf-board';
        phases.forEach(function (phase) { board.appendChild(phaseCard(phase, true)); });
        return board;
    }

    function calendarTasks(phases) {
        return phases.flatMap(function (phase) {
            return phase.elements.filter(function (item) { return item.date; });
        });
    }

    function renderCalendar(phases) {
        var tasks = calendarTasks(phases);
        if (!monthCursor) {
            var initial = tasks.length ? new Date(tasks[0].date + 'T12:00:00') : new Date();
            monthCursor = new Date(initial.getFullYear(), initial.getMonth(), 1);
        }
        var wrapper = document.createElement('section');
        wrapper.className = 'pf-calendar';
        var toolbar = document.createElement('div');
        toolbar.className = 'pf-calendar__toolbar';
        var previous = document.createElement('button');
        previous.type = 'button'; previous.className = 'pf-btn pf-btn--sm pf-btn--outline'; previous.textContent = '‹ Vorheriger Monat';
        previous.addEventListener('click', function () { monthCursor.setMonth(monthCursor.getMonth() - 1); showView('calendar'); });
        var heading = document.createElement('h3');
        heading.textContent = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' }).format(monthCursor);
        var next = document.createElement('button');
        next.type = 'button'; next.className = 'pf-btn pf-btn--sm pf-btn--outline'; next.textContent = 'Nächster Monat ›';
        next.addEventListener('click', function () { monthCursor.setMonth(monthCursor.getMonth() + 1); showView('calendar'); });
        toolbar.append(previous, heading, next);
        var grid = document.createElement('div');
        grid.className = 'pf-calendar__grid';
        ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].forEach(function (weekday) {
            var label = document.createElement('div'); label.className = 'pf-calendar__weekday'; label.textContent = weekday; grid.appendChild(label);
        });
        var year = monthCursor.getFullYear();
        var month = monthCursor.getMonth();
        var firstOffset = (new Date(year, month, 1).getDay() + 6) % 7;
        var days = new Date(year, month + 1, 0).getDate();
        for (var blank = 0; blank < firstOffset; blank++) {
            var spacer = document.createElement('div'); spacer.className = 'pf-calendar__day is-outside'; grid.appendChild(spacer);
        }
        for (var day = 1; day <= days; day++) {
            var cell = document.createElement('div'); cell.className = 'pf-calendar__day';
            var number = document.createElement('span'); number.className = 'pf-calendar__date'; number.textContent = day; cell.appendChild(number);
            var iso = year + '-' + String(month + 1).padStart(2, '0') + '-' + String(day).padStart(2, '0');
            cell.dataset.date = iso;
            tasks.filter(function (task) { return task.date === iso; }).forEach(function (task) {
                var link = document.createElement('a'); link.className = 'pf-calendar__task'; link.href = task.href;
                link.textContent = task.title; link.title = task.title;
                link.dataset.elementId = task.elementId; link.dataset.date = task.date;
                if (editable) link.draggable = true;
                cell.appendChild(link);
            });
            grid.appendChild(cell);
        }
        wrapper.append(toolbar, grid);
        var undatedTasks = phases.flatMap(function (phase) {
            return phase.elements.filter(function (item) { return !item.date; });
        });
        if (undatedTasks.length) {
            var undated = document.createElement('section');
            undated.className = 'pf-calendar__undated';
            var undatedHeading = document.createElement('h4');
            undatedHeading.textContent = 'Aufgaben ohne Datum';
            var undatedList = document.createElement('ul');
            undatedList.className = 'pf-plan-compact-list';
            undatedTasks.forEach(function (task) { undatedList.appendChild(elementCard(task)); });
            undated.append(undatedHeading, undatedList);
            wrapper.appendChild(undated);
        }
        if (!tasks.length && !undatedTasks.length) {
            var note = document.createElement('p'); note.className = 'pf-text-muted'; note.textContent = 'Aufgaben mit einem Datum erscheinen hier im Kalender.'; wrapper.appendChild(note);
        }
        return wrapper;
    }

    function enableAlternativeDragAndDrop(viewRoot, view) {
        if (!editable) return;
        var dragged = null;
        var handleSelector = '.pf-compact-drag-handle, .pf-compact-phase-drag-handle';
        viewRoot.querySelectorAll('[draggable="true"]').forEach(function (item) {
            var handle = item.querySelector(handleSelector);
            if (view === 'calendar') handle = item;
            handle?.addEventListener('pointerdown', function () { item.dataset.dragArmed = 'true'; });
            item.addEventListener('dragstart', function (event) {
                if (item.dataset.dragArmed !== 'true') { event.preventDefault(); return; }
                event.stopPropagation(); dragged = item; item.classList.add('is-dragging');
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', item.dataset.elementId || item.dataset.sectionId);
            });
            item.addEventListener('dragend', function () {
                delete item.dataset.dragArmed; item.classList.remove('is-dragging'); dragged = null;
                viewRoot.querySelectorAll('.drop-target').forEach(function (target) { target.classList.remove('drop-target'); });
            });
        });

        if (view === 'calendar') {
            viewRoot.querySelectorAll('.pf-calendar__day[data-date]').forEach(function (day) {
                day.addEventListener('dragover', function (event) {
                    if (!dragged?.dataset.elementId) return;
                    event.preventDefault(); day.classList.add('drop-target');
                });
                day.addEventListener('dragleave', function (event) {
                    if (!day.contains(event.relatedTarget)) day.classList.remove('drop-target');
                });
                day.addEventListener('drop', function (event) {
                    event.preventDefault(); day.classList.remove('drop-target');
                    if (!dragged?.dataset.elementId || dragged.dataset.date === day.dataset.date) return;
                    submit('/projects/' + projectId + (isDraft ? '/draft' : '/plan') + '/elements/'
                        + dragged.dataset.elementId + '/date', { targetDate: day.dataset.date });
                });
            });
            return;
        }

        viewRoot.querySelectorAll('.pf-plan-compact-list').forEach(function (list) {
            list.addEventListener('dragover', function (event) {
                if (!dragged?.classList.contains('pf-plan-compact-element')) return;
                event.preventDefault(); list.classList.add('drop-target');
            });
            list.addEventListener('drop', function (event) {
                event.preventDefault(); list.classList.remove('drop-target');
                if (!dragged?.classList.contains('pf-plan-compact-element')) return;
                var siblings = Array.from(list.querySelectorAll('.pf-plan-compact-element:not(.is-dragging)'));
                var before = siblings.find(function (item) { return event.clientY < item.getBoundingClientRect().top + item.offsetHeight / 2; });
                submit(dragged.dataset.moveUrl, {
                    targetSectionId: list.dataset.sectionId || '', targetDate: dragged.dataset.date || '',
                    targetPosition: before ? siblings.indexOf(before) : siblings.length
                });
            });
        });

        var phaseContainer = viewRoot.querySelector('.pf-phase-card-grid, .pf-board');
        phaseContainer?.addEventListener('dragover', function (event) {
            if (!dragged || !(dragged.classList.contains('pf-phase-card') || dragged.classList.contains('pf-board-column'))) return;
            event.preventDefault(); phaseContainer.classList.add('drop-target');
        });
        phaseContainer?.addEventListener('drop', function (event) {
            event.preventDefault(); phaseContainer.classList.remove('drop-target');
            if (!dragged?.dataset.moveUrl || dragged.dataset.elementId) return;
            var selector = view === 'board' ? '.pf-board-column:not(.is-dragging)' : '.pf-phase-card:not(.is-dragging)';
            var siblings = Array.from(phaseContainer.querySelectorAll(':scope > ' + selector));
            var before = siblings.find(function (item) {
                var box = item.getBoundingClientRect();
                return view === 'board' ? event.clientX < box.left + box.width / 2 : event.clientY < box.top + box.height / 2;
            });
            var offset = view === 'cards' ? cardPage * 6 : 0;
            submit(dragged.dataset.moveUrl, { targetPosition: offset + (before ? siblings.indexOf(before) : siblings.length) });
        });
    }

    function showView(view) {
        var isList = view === 'list';
        sections.hidden = !isList;
        if (unsectioned) unsectioned.hidden = !isList;
        alternativeView.hidden = isList;
        alternativeView.replaceChildren();
        switcher.querySelectorAll('[data-plan-view]').forEach(function (button) {
            var active = button.dataset.planView === view;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        });
        if (!isList) {
            var phases = collectPhases();
            alternativeView.appendChild(view === 'cards' ? renderCards(phases) : view === 'board' ? renderBoard(phases) : renderCalendar(phases));
            enableAlternativeDragAndDrop(alternativeView, view);
        }
        try { window.localStorage.setItem(storageKey, view); } catch (ignored) { }
    }

    switcher.addEventListener('click', function (event) {
        var button = event.target.closest('[data-plan-view]');
        if (button) showView(button.dataset.planView);
    });
    var savedView;
    try { savedView = window.localStorage.getItem(storageKey); } catch (ignored) { }
    showView(['list', 'cards', 'board', 'calendar'].includes(savedView) ? savedView : 'list');
}

function initializeScrollState() {
    var main = document.querySelector('main[data-project-id]');
    if (!main || main.dataset.scrollStateInitialized === 'true') return;
    main.dataset.scrollStateInitialized = 'true';
    var key = 'projectflow.pageScroll.' + main.dataset.projectId + '.' + window.location.pathname;
    var capture = function () {
        try {
            window.sessionStorage.setItem(key, JSON.stringify({ x: window.scrollX, y: window.scrollY }));
        } catch (ignored) { }
    };
    window.ProjectFlowScrollState = { capture: capture };
    main.addEventListener('submit', capture, true);
    main.addEventListener('click', function (event) {
        var link = event.target.closest('a[href]');
        if (!link || link.target === '_blank') return;
        try {
            var target = new URL(link.href, window.location.href);
            if (target.origin === window.location.origin && target.pathname !== window.location.pathname) capture();
        } catch (ignored) { }
    }, true);
    try {
        var saved = JSON.parse(window.sessionStorage.getItem(key));
        if (saved && Number.isFinite(saved.y)) {
            window.requestAnimationFrame(function () { window.scrollTo(saved.x || 0, saved.y); });
        }
        window.sessionStorage.removeItem(key);
    } catch (ignored) { }
}

function initialize() {
    initializePlanViews();
    initializeScrollState();
}

initialize();
document.addEventListener('projectflow:plan-updated', initialize);
})();
