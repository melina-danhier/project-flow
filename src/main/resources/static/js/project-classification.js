(() => {
    const category = document.getElementById('category');
    const subcategory = document.getElementById('subcategory');
    const options = document.getElementById('subcategory-options');
    if (!category || !subcategory || !options) return;
    const update = () => {
        const selected = subcategory.value;
        const placeholder = new Option('Bitte auswählen', '');
        placeholder.disabled = true;
        subcategory.replaceChildren(placeholder);
        options.content.querySelectorAll('option').forEach(option => {
            if (option.dataset.category === category.value) {
                subcategory.append(option.cloneNode(true));
            }
        });
        // Restore only values that still belong to the selected category.
        subcategory.value = Array.from(subcategory.options).some(option => option.value === selected)
            ? selected : '';
        const hasOptions = subcategory.options.length > 1;
        subcategory.disabled = !hasOptions;
        subcategory.required = hasOptions;
        document.getElementById('subcategory-fields').hidden = !hasOptions;
    };
    category.addEventListener('change', update);
    update();
})();
