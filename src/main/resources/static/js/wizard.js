(function () {
    'use strict';

    // 1. "Heute" Checkbox for Start Date
    const todayCheckbox = document.getElementById('start-date-today-checkbox');
    const startDateInput = document.getElementById('startDate');

    if (todayCheckbox && startDateInput) {
        const formatToday = () => {
            const d = new Date();
            const year = d.getFullYear();
            const month = String(d.getMonth() + 1).padStart(2, '0');
            const day = String(d.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        };

        const updateTodayState = () => {
            if (todayCheckbox.checked) {
                startDateInput.dataset.previousValue = startDateInput.value;
                startDateInput.value = formatToday();
                startDateInput.readOnly = true;
                startDateInput.classList.add('is-readonly-today');
            } else {
                startDateInput.readOnly = false;
                startDateInput.classList.remove('is-readonly-today');
                if (startDateInput.dataset.previousValue !== undefined) {
                    startDateInput.value = startDateInput.dataset.previousValue;
                }
            }
        };

        todayCheckbox.addEventListener('change', updateTodayState);

        // Pre-check if input already equals today
        if (startDateInput.value && startDateInput.value === formatToday()) {
            todayCheckbox.checked = true;
            startDateInput.readOnly = true;
            startDateInput.classList.add('is-readonly-today');
        }

        const form = startDateInput.closest('form');
        form?.addEventListener('submit', () => {
            if (todayCheckbox.checked) {
                startDateInput.value = formatToday();
            }
        });
    }

    // 2. Creation Method Cards Selection on Page 2
    const methodCards = document.querySelectorAll('.pf-method-card');
    const methodSubmitBtn = document.getElementById('method-submit');

    if (methodCards.length) {
        const labels = {
            EMPTY: 'Projekt erstellen',
            TEMPLATE: 'Vorlage auswählen',
            AI: 'Weiter zu den KI-Angaben'
        };

        const updateSelectedMethod = () => {
            const checkedRadio = document.querySelector('input[name="creationType"]:checked');
            methodCards.forEach(card => {
                const radio = card.querySelector('input[name="creationType"]');
                card.classList.toggle('is-selected', radio && radio.checked);
            });
            if (methodSubmitBtn && checkedRadio) {
                methodSubmitBtn.textContent = labels[checkedRadio.value] || 'Weiter';
            }
        };

        methodCards.forEach(card => {
            card.addEventListener('click', (event) => {
                const radio = card.querySelector('input[name="creationType"]');
                if (radio && event.target !== radio) {
                    radio.checked = true;
                    radio.dispatchEvent(new Event('change', { bubbles: true }));
                }
            });
        });

        document.querySelectorAll('input[name="creationType"]').forEach(radio => {
            radio.addEventListener('change', updateSelectedMethod);
        });

        updateSelectedMethod();
    }

    // 3. Duration Picker Sync & explicit time-frame suggestions
    const durationValueInput = document.getElementById('durationValue');
    const durationUnitSelect = document.getElementById('durationUnit');
    const durationDaysHidden = document.getElementById('durationDays');
    const endDateInput = document.getElementById('endDate');
    const suggestionBox = document.getElementById('time-frame-suggestion');
    const suggestionText = document.getElementById('time-frame-suggestion-text');
    const applySuggestionButton = document.getElementById('apply-time-frame-suggestion');

    if (durationValueInput && durationUnitSelect && durationDaysHidden) {
        const syncDuration = () => {
            const val = parseInt(durationValueInput.value, 10);
            if (isNaN(val) || val <= 0) {
                durationDaysHidden.value = '';
                return;
            }
            const unit = durationUnitSelect.value;
            let days = val;
            if (unit === 'WEEKS') {
                days = val * 7;
            } else if (unit === 'MONTHS') {
                days = val * 30;
            }
            durationDaysHidden.value = days;
        };

        durationValueInput.addEventListener('input', syncDuration);
        durationUnitSelect.addEventListener('change', syncDuration);
        syncDuration();

        // A calculated date remains a suggestion until the user explicitly accepts it.
        if (startDateInput && endDateInput) {
            let suggestion = null;

            const parseDate = (val) => {
                if (!val) return null;
                const parts = val.split('-');
                if (parts.length !== 3) return null;
                const year = parseInt(parts[0], 10);
                const month = parseInt(parts[1], 10) - 1;
                const day = parseInt(parts[2], 10);
                const d = new Date(year, month, day);
                return isNaN(d.getTime()) ? null : d;
            };

            const formatDate = (d) => {
                const year = d.getFullYear();
                const month = String(d.getMonth() + 1).padStart(2, '0');
                const day = String(d.getDate()).padStart(2, '0');
                return `${year}-${month}-${day}`;
            };

            const addDays = (d, days) => {
                const res = new Date(d.getTime());
                res.setDate(res.getDate() + days);
                return res;
            };

            const hideSuggestion = () => {
                suggestion = null;
                if (suggestionBox) suggestionBox.style.display = 'none';
            };

            const updateSuggestion = () => {
                hideSuggestion();
                syncDuration();
                const start = parseDate(startDateInput.value);
                const end = parseDate(endDateInput.value);
                const days = parseInt(durationDaysHidden.value, 10);
                if (isNaN(days) || days <= 0 || (start && end)) return;

                if (start && !end) {
                    const value = formatDate(addDays(start, days - 1));
                    suggestion = { field: endDateInput, value };
                    if (suggestionText) suggestionText.textContent = `Vorgeschlagener Endtermin: ${value.split('-').reverse().join('.')}`;
                } else if (end && !start) {
                    const value = formatDate(addDays(end, -(days - 1)));
                    suggestion = { field: startDateInput, value };
                    if (suggestionText) suggestionText.textContent = `Vorgeschlagenes Startdatum: ${value.split('-').reverse().join('.')}`;
                }

                if (suggestion && suggestionBox) suggestionBox.style.display = 'flex';
            };

            startDateInput.addEventListener('change', updateSuggestion);
            endDateInput.addEventListener('change', updateSuggestion);
            durationValueInput.addEventListener('input', updateSuggestion);
            durationUnitSelect.addEventListener('change', updateSuggestion);
            applySuggestionButton?.addEventListener('click', () => {
                if (!suggestion || suggestion.field.value) return;
                suggestion.field.value = suggestion.value;
                suggestion.field.dispatchEvent(new Event('change', { bubbles: true }));
                hideSuggestion();
            });
            updateSuggestion();
        }
    }

})();
