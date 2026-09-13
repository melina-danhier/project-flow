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

    // 3. Duration Picker Sync & Reciprocal 2-out-of-3 Time Calculation
    const durationValueInput = document.getElementById('durationValue');
    const durationUnitSelect = document.getElementById('durationUnit');
    const durationDaysHidden = document.getElementById('durationDays');
    const endDateInput = document.getElementById('endDate');

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

        // Reciprocal calculation: Any 2 of 3 values calculate the 3rd
        if (startDateInput && endDateInput) {
            let isCalculating = false;

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

            const calculateDurationFromDates = () => {
                const s = parseDate(startDateInput.value);
                const e = parseDate(endDateInput.value);
                if (!s || !e || e < s) return;

                const diffTime = e.getTime() - s.getTime();
                const days = Math.round(diffTime / (1000 * 60 * 60 * 24)) + 1;
                if (days <= 0) return;

                durationDaysHidden.value = days;
                if (days >= 30 && days % 30 === 0) {
                    durationValueInput.value = days / 30;
                    durationUnitSelect.value = 'MONTHS';
                } else if (days >= 7 && days % 7 === 0) {
                    durationValueInput.value = days / 7;
                    durationUnitSelect.value = 'WEEKS';
                } else {
                    durationValueInput.value = days;
                    durationUnitSelect.value = 'DAYS';
                }
            };

            const calculateEndFromStartAndDuration = () => {
                const s = parseDate(startDateInput.value);
                const days = parseInt(durationDaysHidden.value, 10);
                if (!s || isNaN(days) || days <= 0) return;

                const e = addDays(s, days - 1);
                endDateInput.value = formatDate(e);
            };

            const calculateStartFromEndAndDuration = () => {
                const e = parseDate(endDateInput.value);
                const days = parseInt(durationDaysHidden.value, 10);
                if (!e || isNaN(days) || days <= 0) return;

                const s = addDays(e, -(days - 1));
                startDateInput.value = formatDate(s);
            };

            startDateInput.addEventListener('change', () => {
                if (isCalculating) return;
                isCalculating = true;
                try {
                    if (endDateInput.value) {
                        calculateDurationFromDates();
                    } else if (durationValueInput.value) {
                        calculateEndFromStartAndDuration();
                    }
                } finally {
                    isCalculating = false;
                }
            });

            endDateInput.addEventListener('change', () => {
                if (isCalculating) return;
                isCalculating = true;
                try {
                    if (startDateInput.value) {
                        calculateDurationFromDates();
                    } else if (durationValueInput.value) {
                        calculateStartFromEndAndDuration();
                    }
                } finally {
                    isCalculating = false;
                }
            });

            const onDurationChanged = () => {
                if (isCalculating) return;
                isCalculating = true;
                try {
                    syncDuration();
                    if (startDateInput.value) {
                        calculateEndFromStartAndDuration();
                    } else if (endDateInput.value) {
                        calculateStartFromEndAndDuration();
                    }
                } finally {
                    isCalculating = false;
                }
            };

            durationValueInput.addEventListener('input', onDurationChanged);
            durationUnitSelect.addEventListener('change', onDurationChanged);
        }
    }

})();
