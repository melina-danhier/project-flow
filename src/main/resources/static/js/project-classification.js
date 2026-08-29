(() => {
    const category = document.getElementById('category');
    const subcategory = document.getElementById('subcategory');
    const options = document.getElementById('subcategory-options');
    const otherDescription = document.getElementById('otherProjectTypeDescription');
    if (!category || !subcategory || !options || !otherDescription) return;

    const update = () => {
        const selected = subcategory.value;
        const isOther = category.value === 'OTHER';
        subcategory.replaceChildren(new Option('Keine Unterkategorie', ''));
        options.content.querySelectorAll('option').forEach(option => {
            if (option.dataset.category === category.value) {
                subcategory.append(option.cloneNode(true));
            }
        });
        // Restore only values that still belong to the selected category.
        subcategory.value = Array.from(subcategory.options).some(option => option.value === selected)
            ? selected : '';
        subcategory.disabled = isOther;
        document.getElementById('subcategory-fields').hidden = isOther;
        document.getElementById('other-project-type-fields').hidden = !isOther;
        otherDescription.disabled = !isOther;
        otherDescription.required = isOther;
    };
    category.addEventListener('change', update);
    update();
})();
