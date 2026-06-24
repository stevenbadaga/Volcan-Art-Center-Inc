/* ═══════════════════════════════════════════════════════════════
   VOLCANO ARTS CENTER — Admin Panel JavaScript
   Sidebar, Modals, Toasts, Table Interactions
   ═══════════════════════════════════════════════════════════════ */

(function () {
    'use strict';

    /* ── Sidebar Toggle ── */
    const sidebar = document.getElementById('admin-sidebar');
    const main = document.getElementById('admin-main');
    const sidebarToggle = document.getElementById('sidebar-toggle');
    const sidebarClose = document.getElementById('sidebar-close');
    const mobileBreakpoint = 768;

    function syncMobileSidebarState() {
        if (!document.body) return;
        var isMobileOpen = !!(sidebar && sidebar.classList.contains('mobile-open') && window.innerWidth <= mobileBreakpoint);
        document.body.classList.toggle('admin-mobile-nav-open', isMobileOpen);
        if (sidebarToggle) {
            var isDesktopCollapsed = !!(sidebar && sidebar.classList.contains('collapsed') && window.innerWidth > mobileBreakpoint);
            var isDrawerOpen = !!(sidebar && sidebar.classList.contains('mobile-open') && window.innerWidth <= mobileBreakpoint);
            var expanded = window.innerWidth > mobileBreakpoint ? !isDesktopCollapsed : isDrawerOpen;
            var label = window.innerWidth > mobileBreakpoint
                ? (isDesktopCollapsed ? 'Expand sidebar' : 'Collapse sidebar')
                : (isDrawerOpen ? 'Close sidebar' : 'Open sidebar');
            sidebarToggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
            sidebarToggle.setAttribute('aria-label', label);
            sidebarToggle.setAttribute('title', label);
        }
    }

    function syncCollapsedSidebarTooltips() {
        if (!sidebar) return;
        var isCollapsedDesktop = !!(sidebar.classList.contains('collapsed') && window.innerWidth > mobileBreakpoint);
        var items = sidebar.querySelectorAll('.admin-sidebar__link, .admin-sidebar__footer-link, .admin-sidebar__link--logout');

        items.forEach(function (item) {
            if (!item.dataset.originalTitle) {
                item.dataset.originalTitle = (item.textContent || '').replace(/\s+/g, ' ').trim();
            }
            if (isCollapsedDesktop) {
                var label = item.dataset.originalTitle || '';
                if (label) {
                    item.setAttribute('title', label);
                    item.setAttribute('aria-label', label);
                }
            } else {
                item.removeAttribute('title');
                if (item.dataset.originalTitle) {
                    item.setAttribute('aria-label', item.dataset.originalTitle);
                }
            }
        });
    }

    function toggleSidebar() {
        if (sidebar) sidebar.classList.toggle('collapsed');
        if (main) main.classList.toggle('sidebar-collapsed');
        syncCollapsedSidebarTooltips();
        try {
            localStorage.setItem('vac-sidebar', sidebar.classList.contains('collapsed') ? 'collapsed' : 'expanded');
        } catch (e) { /* ignore */ }
    }

    function closeSidebarMobile() {
        if (sidebar) sidebar.classList.remove('mobile-open');
        syncMobileSidebarState();
    }

    if (sidebarToggle) {
        sidebarToggle.addEventListener('click', function () {
            if (window.innerWidth <= mobileBreakpoint) {
                sidebar.classList.toggle('mobile-open');
                syncMobileSidebarState();
            } else {
                toggleSidebar();
                syncMobileSidebarState();
            }
        });
    }

    if (sidebarClose) {
        sidebarClose.addEventListener('click', function () {
            if (window.innerWidth <= mobileBreakpoint) {
                closeSidebarMobile();
            } else {
                toggleSidebar();
                syncMobileSidebarState();
            }
        });
    }

    // Restore sidebar state
    try {
        var sidebarState = localStorage.getItem('vac-sidebar');
        if (sidebarState === 'collapsed' && sidebar && window.innerWidth > mobileBreakpoint) {
            sidebar.classList.add('collapsed');
            if (main) main.classList.add('sidebar-collapsed');
        }
    } catch (e) { /* ignore */ }

    syncCollapsedSidebarTooltips();

    window.addEventListener('resize', function () {
        if (!sidebar) return;
        if (window.innerWidth > mobileBreakpoint) {
            sidebar.classList.remove('mobile-open');
        }
        syncMobileSidebarState();
        syncCollapsedSidebarTooltips();
    });

    if (sidebar) {
        sidebar.querySelectorAll('.admin-sidebar__link, .admin-sidebar__footer-link, .admin-sidebar__link--logout').forEach(function (link) {
            link.addEventListener('click', function () {
                if (window.innerWidth <= mobileBreakpoint) {
                    closeSidebarMobile();
                }
            });
        });
    }

    document.addEventListener('click', function (event) {
        if (window.innerWidth > mobileBreakpoint || !sidebar || !sidebar.classList.contains('mobile-open')) return;
        if (sidebar.contains(event.target) || (sidebarToggle && sidebarToggle.contains(event.target))) return;
        closeSidebarMobile();
    });

    syncMobileSidebarState();
    syncCollapsedSidebarTooltips();

    /* ── Admin Theme Toggle ── */
    const themeToggle = document.getElementById('admin-theme-toggle');
    const themeIcon = document.getElementById('admin-theme-icon');
    const themeStorageKey = 'vac-admin-theme';

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
    }

    function themeIconSvg(theme) {
        if (theme === 'dark') {
            return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4.5"></circle><path d="M12 2.5v2.5"></path><path d="M12 19v2.5"></path><path d="m4.93 4.93 1.77 1.77"></path><path d="m17.3 17.3 1.77 1.77"></path><path d="M2.5 12H5"></path><path d="M19 12h2.5"></path><path d="m4.93 19.07 1.77-1.77"></path><path d="m17.3 6.7 1.77-1.77"></path></svg>';
        }

        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A8.9 8.9 0 1 1 11.2 3a7.1 7.1 0 0 0 9.8 9.8Z"></path></svg>';
    }

    function syncThemeToggle(theme) {
        if (themeIcon) {
            themeIcon.innerHTML = themeIconSvg(theme);
        }

        if (themeToggle) {
            var nextTheme = theme === 'dark' ? 'light' : 'dark';
            var label = nextTheme === 'dark' ? 'Switch to dark theme' : 'Switch to light theme';
            themeToggle.setAttribute('aria-label', label);
            themeToggle.setAttribute('title', label);
        }
    }

    function applyAdminTheme(theme) {
        var resolved = theme === 'light' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', resolved);
        document.documentElement.setAttribute('data-user-preference', resolved);
        syncThemeToggle(resolved);
        try {
            localStorage.setItem(themeStorageKey, resolved);
        } catch (e) { /* ignore */ }
        window.dispatchEvent(new CustomEvent('vac:theme-changed', { detail: { theme: resolved } }));
    }

    syncThemeToggle(currentTheme());

    if (themeToggle) {
        themeToggle.addEventListener('click', function () {
            applyAdminTheme(currentTheme() === 'dark' ? 'light' : 'dark');
        });
    }

    /* ── Modal System ── */
    window.openModal = function (title, bodyHtml) {
        var overlay = document.getElementById('admin-modal-overlay');
        var titleEl = document.getElementById('admin-modal-title');
        var bodyEl = document.getElementById('admin-modal-body');
        if (!overlay || !titleEl || !bodyEl) return;
        titleEl.textContent = title;
        bodyEl.innerHTML = bodyHtml;
        overlay.style.display = 'flex';
        document.body.style.overflow = 'hidden';
        setTimeout(function () { overlay.classList.add('visible'); }, 20);
    };

    window.closeModal = function () {
        var overlay = document.getElementById('admin-modal-overlay');
        if (!overlay) return;
        overlay.classList.remove('visible');
        document.body.style.overflow = '';
        setTimeout(function () { overlay.style.display = 'none'; }, 300);
    };

    // Close modal on Escape
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') closeSidebarMobile();
        if (e.key === 'Escape') closeModal();
    });

    /* ── Toast Notifications ── */
    window.showToast = function (message, type) {
        type = type || 'info';
        var container = document.getElementById('admin-toast-container');
        if (!container) return;

        var toast = document.createElement('div');
        toast.className = 'admin-toast admin-toast--' + type;

        var icons = {
            success: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
            error: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>',
            warning: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
            info: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>'
        };

        toast.innerHTML =
            '<span class="admin-toast__icon">' + (icons[type] || icons.info) + '</span>' +
            '<span class="admin-toast__message">' + message + '</span>' +
            '<button class="admin-toast__close" onclick="this.parentElement.remove()">&times;</button>';

        container.appendChild(toast);
        setTimeout(function () { toast.classList.add('visible'); }, 20);
        setTimeout(function () {
            toast.classList.remove('visible');
            setTimeout(function () { toast.remove(); }, 400);
        }, 5000);
    };

    /* ── Confirm Delete ── */
    window.confirmAction = function (message, formId) {
        if (confirm(message || 'Are you sure? This action cannot be undone.')) {
            var form = document.getElementById(formId);
            if (form) form.submit();
        }
    };

    /* ── Admin Tab Switcher ── */
    document.querySelectorAll('[data-admin-tab]').forEach(function (tab) {
        tab.addEventListener('click', function (e) {
            e.preventDefault();
            var group = tab.closest('.admin-tabs');
            var targetId = tab.getAttribute('data-admin-tab');

            // Update tab active state
            group.querySelectorAll('[data-admin-tab]').forEach(function (t) { t.classList.remove('active'); });
            tab.classList.add('active');

            // Show/hide panels
            var container = group.nextElementSibling || group.parentElement;
            container.querySelectorAll('[data-admin-tab-panel]').forEach(function (panel) {
                panel.style.display = panel.getAttribute('data-admin-tab-panel') === targetId ? 'block' : 'none';
            });
        });
    });

    /* ── Status chip colors ── */
    document.querySelectorAll('[data-status]').forEach(function (el) {
        var status = (el.getAttribute('data-status') || '').toUpperCase();
        var map = {
            'PENDING': 'chip--warning', 'CONFIRMED': 'chip--success', 'COMPLETED': 'chip--success',
            'CANCELLED': 'chip--danger', 'REFUNDED': 'chip--danger', 'PAID': 'chip--success',
            'UNPAID': 'chip--warning', 'PARTIAL': 'chip--warning', 'ENABLED': 'chip--success',
            'DISABLED': 'chip--danger', 'APPROVED': 'chip--success', 'REJECTED': 'chip--danger',
            'PUBLISHED': 'chip--success', 'DRAFT': 'chip--warning', 'NEW': 'chip--warning',
            'IN_REVIEW': 'chip--warning', 'ACTIVE': 'chip--success', 'SHIPPED': 'chip--success',
            'DELIVERED': 'chip--success', 'PROCESSING': 'chip--warning', 'RESPONDED': 'chip--success',
            'CLOSED': 'chip--danger', 'WAITLISTED': 'chip--warning', 'ENROLLED': 'chip--success'
        };
        if (map[status]) el.classList.add(map[status]);
    });

    /* ── KPI counter animation for admin pages ── */
    document.querySelectorAll('.admin-kpi__value[data-count]').forEach(function (el) {
        // The numeric target may live in the data-count attribute OR in the
        // server-rendered text content (data-count is often an empty flag).
        var attr = el.getAttribute('data-count');
        var raw = (attr && attr.trim() !== '') ? attr : el.textContent;
        var target = parseInt(String(raw).replace(/[^0-9.\-]/g, ''), 10);
        // Never overwrite with NaN — leave the server value as-is if unparseable.
        if (!isFinite(target)) return;
        var suffix = el.getAttribute('data-suffix') || '';
        var prefix = el.getAttribute('data-prefix') || '';
        var duration = 1200;
        var start = performance.now();

        function update(now) {
            var elapsed = now - start;
            var progress = Math.min(elapsed / duration, 1);
            var eased = 1 - Math.pow(1 - progress, 3);
            var current = Math.round(eased * target);
            el.textContent = prefix + current.toLocaleString() + suffix;
            if (progress < 1) requestAnimationFrame(update);
        }
        requestAnimationFrame(update);
    });

    /* KPI sparklines are rendered by vac-overhaul.js (initKpiSparklines),
       which loads on both admin and public dashboards. */

    /* ── Admin notification bell polling ── */
    (function initAdminBellPolling() {
        var badge = document.getElementById('admin-bell-badge');
        if (!badge) return;

        function getCsrf() {
            var tokenMeta = document.querySelector('meta[name="_csrf"]');
            var headerMeta = document.querySelector('meta[name="_csrf_header"]');
            if (!tokenMeta || !headerMeta) return null;
            return { token: tokenMeta.getAttribute('content'), header: headerMeta.getAttribute('content') };
        }

        async function refreshAdminUnread() {
            try {
                var headers = { 'Accept': 'application/json' };
                var csrf = getCsrf();
                if (csrf) headers[csrf.header] = csrf.token;
                var res = await fetch('/api/v1/client/notifications/unread-count', { headers: headers, credentials: 'same-origin' });
                if (!res.ok) return;
                var json = await res.json();
                var n = (json && json.data && typeof json.data.unread === 'number') ? json.data.unread : 0;
                if (n === 0) {
                    badge.hidden = true;
                    badge.textContent = '';
                } else {
                    badge.hidden = false;
                    badge.textContent = n > 99 ? '99+' : String(n);
                }
            } catch (_) { /* non-critical */ }
        }

        refreshAdminUnread();
        setInterval(refreshAdminUnread, 30000);
    })();

})();
