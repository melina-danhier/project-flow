const form = document.querySelector('#assumption-review-form');
const cards = [...form.querySelectorAll('.assumption-card')];
const submit = form.querySelector('#assumption-submit');

const update = () => {
    let complete = true;
    let rejected = false;
    cards.forEach(card => {
        const choice = card.querySelector('input[type="radio"]:checked');
        const correction = card.querySelector('textarea');
        const requiresCorrection = card.dataset.correctionRequired === 'true';
        const correctionVisible = choice?.value === 'REJECTED' && requiresCorrection;
        if (correction) {
            correction.closest('.assumption-correction').hidden = !correctionVisible;
            correction.required = correctionVisible;
            if (!correctionVisible) correction.value = '';
        }
        if (!choice || (correctionVisible && !correction.value.trim())) complete = false;
        if (choice?.value === 'REJECTED') rejected = true;
    });
    submit.disabled = !complete;
    submit.textContent = rejected ? 'Mit Korrekturen neu generieren' : 'Entwurf anzeigen';
};

form.addEventListener('input', update);
update();
