(function () {
    'use strict';

    var switcher = document.querySelector('.pf-plan-view-switcher');
    var alternativeView = document.getElementById('plan-alternative-view');
    var sections = document.getElementById('plan-sections');
    var unsectioned = document.querySelector('.plan-section.unsectioned');
    if (!switcher || !alternativeView || !sections) return;

    var monthCursor;
    var cardPage = 0;
    var projectId = document.querySelector('main[data-project-id]').dataset.projectId;
    var storageKey = 'projectflow.planView.' + projectId;

    function text(element, selector) {
        var match = element.querySelector(selector);
        return match ? match.textContent.trim() : '';
    }

    function collectPhases() {
        var phaseNodes = Array.from(sections.querySelectorAll(':scope > .plan-phase'));
        if (unsectioned) phaseNodes.push(unsectioned);
        return phaseNodes.map(function (phase) {
            var header = phase.querySelector('.pf-plan-section__header');
            var title = phase.classList.contains('unsectioned')
                ? 'Ohne Phase'
                : text(header, 'strong');
            var description = header ? text(header, 'p') : '';
            var elements = Array.from(phase.querySelectorAll(':scope > .plan-elements > .plan-element'))
                .map(function (element) {
                    var taskLink = element.querySelector('.pf-element-item__left a[href*="/tasks/"]');
                    var milestoneLink = element.querySelector('.pf-element-item__left a[href*="/milestones/"]');
                    var link = taskLink || milestoneLink;
                    return {
                        title: link ? link.textContent.trim() : '',
                        href: link ? link.getAttribute('href') : '#',
                        type: taskLink ? 'task' : 'milestone',
                        date: element.dataset.date || '',
                        dateLabel: text(element, '.pf-badge--outline'),
                        state: text(element, '.pf-badge--gray')
                    };
                });
            return { title: title, description: description, elements: elements };
        });
    }

    function elementCard(item) {
        var li = document.createElement('li');
        li.className = 'pf-plan-compact-element pf-plan-compact-element--' + item.type;
        var icon = document.createElement('span');
        icon.className = 'pf-plan-compact-element__icon';
        icon.textContent = item.type === 'task' ? '☐' : '◆';
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
        li.append(icon, content);
        return li;
    }

    function phaseCard(phase, board) {
        var article = document.createElement('article');
        article.className = board ? 'pf-board-column' : 'pf-phase-card';
        var header = document.createElement('header');
        var title = document.createElement('h3');
        title.textContent = phase.title;
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
            return phase.elements.filter(function (item) { return item.type === 'task' && item.date; });
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
            tasks.filter(function (task) { return task.date === iso; }).forEach(function (task) {
                var link = document.createElement('a'); link.className = 'pf-calendar__task'; link.href = task.href;
                link.textContent = task.title; link.title = task.title; cell.appendChild(link);
            });
            grid.appendChild(cell);
        }
        wrapper.append(toolbar, grid);
        if (!tasks.length) {
            var note = document.createElement('p'); note.className = 'pf-text-muted'; note.textContent = 'Aufgaben mit einem Datum erscheinen hier im Kalender.'; wrapper.appendChild(note);
        }
        return wrapper;
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
})();
