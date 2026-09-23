(() => {
    window.addEventListener('pageshow', () => {
        document.querySelectorAll('form[data-loading-screen-target]').forEach(form => {
            const loadingScreen = document.getElementById(form.dataset.loadingScreenTarget);
            const content = document.getElementById(form.dataset.loadingContentTarget);
            if (loadingScreen) {
                loadingScreen.hidden = true;
                loadingScreen.setAttribute('aria-hidden', 'true');
            }
            if (content) content.hidden = false;
        });
    });

    document.addEventListener('submit', event => {
        const form = event.target.closest('form[data-loading-screen-target]');
        if (!form || event.defaultPrevented || !form.checkValidity()) return;

        const loadingScreen = document.getElementById(form.dataset.loadingScreenTarget);
        if (!loadingScreen) return;

        const content = document.getElementById(form.dataset.loadingContentTarget);
        if (content) content.hidden = true;
        loadingScreen.hidden = false;
        loadingScreen.removeAttribute('aria-hidden');
        window.scrollTo({ top: 0, behavior: 'instant' });
    });
})();
