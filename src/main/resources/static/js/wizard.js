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

    // 3. Duration Picker Sync (Number + Unit to durationDays)
    const durationValueInput = document.getElementById('durationValue');
    const durationUnitSelect = document.getElementById('durationUnit');
    const durationDaysHidden = document.getElementById('durationDays');

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
    }

})();
