(() => {
    const explanation = 'Diese Funktion ist während der Nutzerstudie deaktiviert.';
    const lifecycleAction = /^\/projects\/[^/]+\/(archive|trash|reactivate|delete|pin|unpin)$/;

    function isBlocked(rawUrl) {
        const url = new URL(rawUrl, window.location.origin);
        const path = url.pathname;
        if (['/login', '/register', '/logout', '/study/start'].includes(path)) return true;
        if (path.startsWith('/projects/') && path.includes('/members')) return true;
        if (lifecycleAction.test(path) || path.startsWith('/projects/bulk/')) return true;
        if (path.startsWith('/projects/new/template')) return true;
        if (path === '/projects/new' && url.searchParams.has('templateId')) return true;
        return ['/projects', '/projects/search'].includes(path)
            && ['ARCHIVE', 'TRASH'].includes(url.searchParams.get('location'));
    }

    document.querySelectorAll('a[href]').forEach(link => {
        if (!isBlocked(link.href)) return;
        link.setAttribute('aria-disabled', 'true');
        link.classList.add('pf-study-disabled');
        link.title = explanation;
        link.addEventListener('click', event => event.preventDefault());
    });

    document.querySelectorAll('form[action]').forEach(form => {
        if (!isBlocked(form.action)) return;
        form.querySelectorAll('button, input[type="submit"]').forEach(control => {
            control.disabled = true;
            control.title = explanation;
            control.classList.add('pf-study-disabled');
        });
    });

    document.querySelectorAll('input[name="creationType"][value="TEMPLATE"]').forEach(input => {
        input.disabled = true;
        const label = input.closest('label');
        if (label) {
            label.classList.add('pf-study-disabled');
            label.title = explanation;
        }
    });
})();
