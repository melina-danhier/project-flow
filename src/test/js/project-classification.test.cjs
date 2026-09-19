const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const { resolve } = require('node:path');

test('dependent dropdown requires concrete selections and hides categories without options', () => {
    class Option {
        constructor(label, value, category) {
            this.label = label;
            this.value = value;
            this.dataset = { category };
        }
        cloneNode() { return new Option(this.label, this.value, this.dataset.category); }
    }
    const category = { value: 'EDUCATION', addEventListener: (_, callback) => { category.change = callback; } };
    const subcategory = {
        value: 'THESIS', options: [],
        replaceChildren(...options) { this.options = options; },
        append(option) { this.options.push(option); }
    };
    const nodes = {
        category, subcategory,
        'subcategory-fields': {},
        'subcategory-options': { content: { querySelectorAll: () => [
            new Option('Abschlussarbeit', 'THESIS', 'EDUCATION'),
            new Option('Lernplan', 'LEARNING_PLAN', 'EDUCATION'),
            new Option('Umzug', 'MOVING', 'HOME')
        ] } }
    };
    runInNewContext(readFileSync(resolve(__dirname, '../../main/resources/static/js/project-classification.js'), 'utf8'), {
        document: { getElementById: id => nodes[id] }, Option
    });
    assert.equal(subcategory.value, 'THESIS');
    assert.deepEqual(subcategory.options.map(option => option.value), ['', 'THESIS', 'LEARNING_PLAN']);
    assert.equal(subcategory.options[0].label, 'Bitte auswählen');
    assert.equal(subcategory.options[0].disabled, true);
    assert.equal(subcategory.required, true);
    category.value = 'HOME';
    category.change();
    assert.equal(subcategory.value, '');
    assert.deepEqual(subcategory.options.map(option => option.value), ['', 'MOVING']);
    subcategory.value = 'MOVING';
    category.value = 'OTHER';
    category.change();
    assert.equal(subcategory.value, '');
    assert.equal(subcategory.disabled, true);
    assert.equal(subcategory.required, false);
    assert.equal(nodes['subcategory-fields'].hidden, true);
    category.value = 'EDUCATION';
    category.change();
    assert.equal(subcategory.value, '');
    assert.equal(subcategory.disabled, false);
});
