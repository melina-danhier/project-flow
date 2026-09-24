const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function loadOrdering(file, applied = false) {
    const callbacks = {};
    const events = {};
    const handleEvents = {};
    const media = { matches: true, addEventListener: (_, fn) => { callbacks.resize = fn; } };
    const handle = { addEventListener: (name, fn) => { handleEvents[name] = fn; } };
    const item = {
        dataset: { moveUrl: '/move', elementId: 'task-1' }, draggable: true,
        classList: { contains: () => false, add() {}, remove() {} },
        querySelectorAll: () => [handle],
        addEventListener: (name, fn) => { events[name] = fn; }
    };
    const main = {
        dataset: { editable: 'true', draftStatus: applied ? 'APPLIED' : 'DRAFT' },
        querySelectorAll: () => [item], isConnected: true
    };
    const document = {
        querySelector: selector => selector.startsWith('main') ? main : null,
        querySelectorAll: () => [], getElementById: () => null,
        addEventListener: (name, fn) => { callbacks[name] = fn; }
    };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js', file), 'utf8'), {
        document, window: { matchMedia: () => media }, console
    });
    callbacks.DOMContentLoaded?.();
    return { media, callbacks, item, events, handleEvents };
}

for (const file of ['plan-ordering.js', 'draft-review.js']) {
    test(`${file}: resizing from mobile enables a working drag handler`, () => {
        const state = loadOrdering(file);
        assert.equal(state.item.draggable, false);
        state.media.matches = false;
        state.callbacks.resize();
        assert.equal(state.item.draggable, true);
        state.handleEvents.pointerdown();
        let payload;
        state.events.dragstart({
            target: { closest: selector => selector === '.pf-drag-handle-visual' ? {} : null },
            preventDefault: () => assert.fail('Desktop drag was blocked'), stopPropagation() {},
            dataTransfer: { setData: (_, value) => { payload = value; } }
        });
        assert.equal(payload, 'task-1');
        state.media.matches = true;
        state.callbacks.resize();
        assert.equal(state.item.draggable, false);
        assert.equal(state.item.dataset.dragArmed, undefined);
        let blocked = false;
        state.events.dragstart({ preventDefault: () => { blocked = true; } });
        assert.equal(blocked, true);
    });
}

test('Applied drafts remain non-draggable after resizing', () => {
    const state = loadOrdering('draft-review.js', true);
    state.media.matches = false;
    state.callbacks.resize();
    assert.equal(state.item.draggable, false);
});
