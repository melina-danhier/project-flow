const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function setup() {
    const template = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/fragments/layout.html'), 'utf8');
    const start = template.indexOf('// A real button');
    assert.ok(start >= 0);
    const code = template.slice(start, template.indexOf('</script>', start));
    const inputs = [];
    const buttons = [];
    let refresh;
    function makeInput() {
        const input = { wrapped: false, calls: 0, disabled: false, readOnly: false,
            closest() { return this.wrapped; }, before() { this.wrapped = true; },
            showPicker() { this.calls++; },
            addEventListener(name, fn) { this[name] = fn; }
        };
        inputs.push(input);
        return input;
    }
    const input = makeInput();
    vm.runInNewContext(code, {
        document: { body: {}, querySelectorAll: () => inputs, createElement: tag => {
            const element = { append() {}, setAttribute() {}, addEventListener(name, fn) { this[name] = fn; } };
            if (tag === 'button') buttons.push(element);
            return element;
        } },
        MutationObserver: class { constructor(fn) { refresh = fn; } observe() {} }
    });
    return { input, buttons, refresh, makeInput };
}

test('Only the calendar button opens the picker', () => {
    const { input, buttons } = setup();
    let cancelled = false;
    input.click({ preventDefault: () => { cancelled = true; } });
    assert.equal(cancelled, true);
    assert.equal(input.calls, 0);
    buttons[0].click();
    assert.equal(input.calls, 1);
});

test('Disabled and readonly dates do not open a picker', () => {
    const { input, buttons } = setup();
    input.disabled = true;
    buttons[0].click();
    input.disabled = false;
    input.readOnly = true;
    buttons[0].click();
    assert.equal(input.calls, 0);
});

test('DOM updates add one button per date field without duplicates', () => {
    const { buttons, refresh, makeInput } = setup();
    refresh();
    assert.equal(buttons.length, 1);
    makeInput();
    refresh();
    refresh();
    assert.equal(buttons.length, 2);
});
