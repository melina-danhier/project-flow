(() => {
    const category = document.getElementById('category');
    const subcategory = document.getElementById('subcategory');
    const options = document.getElementById('subcategory-options');
    if (!category || !subcategory || !options) return;
    const update = () => {
        const selected = subcategory.value;
        subcategory.replaceChildren();
        options.content.querySelectorAll('option').forEach(option => {
            if (option.dataset.category === category.value) {
                subcategory.append(option.cloneNode(true));
            }
        });
        const hasOptions = subcategory.options.length > 0;
        if (hasOptions) {
            const hasValidSelected = Array.from(subcategory.options).some(option => option.value === selected && selected !== '');
            if (hasValidSelected) {
                subcategory.value = selected;
            } else {
                const fallbackOption = Array.from(subcategory.options).find(opt => opt.value.startsWith('OTHER_'));
                subcategory.value = fallbackOption ? fallbackOption.value : subcategory.options[0].value;
            }
        } else {
            subcategory.value = '';
        }
        subcategory.disabled = !hasOptions;
        subcategory.required = false;
        const subcategoryFields = document.getElementById('subcategory-fields');
        if (subcategoryFields) {
            subcategoryFields.hidden = !hasOptions;
        }
    };
    category.addEventListener('change', update);
    update();
})();
