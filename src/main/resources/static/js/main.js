/* ═══════════════════════════════════════════════════════════════
   VOLCANO ARTS CENTER — Main JavaScript (V5 / CODAFRIQA)
   Modules:
     • Sticky scroll-aware nav
     • Mobile menu toggle
     • Smooth anchor scroll
     • Motion One reveal + stagger + hero entrance
     • Counter animation on stats
     • Notification bell (filters, mark-all, fetch unread)
     • Cart store (localStorage) + slide-in drawer + toast on add
     • Chat hub (floating panel, send, polling)
     • Toast helper exposed as window.vacToast(message, variant)
   ═══════════════════════════════════════════════════════════════ */

(function () {
    'use strict';

    const motion = window.Motion || null;
    const animate = motion ? motion.animate : null;
    const inView  = motion ? motion.inView  : null;
    const stagger = motion ? motion.stagger : null;

    /* ────────────────────────────────────────────────────────────
       UTILITIES
       ──────────────────────────────────────────────────────────── */
    function $(sel, root) { return (root || document).querySelector(sel); }
    function $$(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }

    function getCsrf() {
        const tok = $('meta[name="_csrf"]');
        const hdr = $('meta[name="_csrf_header"]');
        return tok && hdr ? { header: hdr.content, token: tok.content } : null;
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function timeAgo(date) {
        if (!date) return '';
        const d = (date instanceof Date) ? date : new Date(date);
        const sec = Math.floor((Date.now() - d.getTime()) / 1000);
        if (sec < 60)    return sec + 's ago';
        if (sec < 3600)  return Math.floor(sec / 60) + 'm ago';
        if (sec < 86400) return Math.floor(sec / 3600) + 'h ago';
        return Math.floor(sec / 86400) + 'd ago';
    }

    /* ────────────────────────────────────────────────────────────
       TOAST
       ──────────────────────────────────────────────────────────── */
    const toastStack = $('#toast-stack');
    function vacToast(message, variant) {
        if (!toastStack) return;
        const v = variant || 'success';
        const icon = v === 'error'
            ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>'
            : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><polyline points="20 6 9 17 4 12"/></svg>';
        const el = document.createElement('div');
        el.className = 'toast' + (v === 'error' ? ' toast--error' : v === 'warning' ? ' toast--warning' : '');
        el.innerHTML = icon + '<span>' + escapeHtml(message) + '</span>';
        toastStack.appendChild(el);
        setTimeout(() => {
            el.classList.add('is-leaving');
            setTimeout(() => el.remove(), 260);
        }, 2500);
    }
    window.vacToast = vacToast;

    document.addEventListener('DOMContentLoaded', () => {

        /* ────────────────────────────────────────────────────────
           STICKY NAV
           ──────────────────────────────────────────────────────── */
        const nav = $('#main-nav');
        const heroSection = $('.hero-section, .vac-stage, .hero-golden, .hero--premium');
        if (nav) {
            const onScroll = () => {
                const inHero = !!heroSection && window.scrollY < (heroSection.offsetHeight - 80);
                nav.classList.toggle('is-scrolled', window.scrollY > 24);
                nav.classList.toggle('scrolled', window.scrollY > 80);
                nav.classList.toggle('transparent', inHero);
                nav.classList.toggle('solid', !inHero);
            };
            window.addEventListener('scroll', onScroll, { passive: true });
            window.addEventListener('resize', onScroll);
            onScroll();
        }

        /* ────────────────────────────────────────────────────────
           MOBILE MENU
           ──────────────────────────────────────────────────────── */
        const navToggle = $('.nav__toggle');
        const navLinks  = $('.nav__links');
        if (navToggle && navLinks) {
            navToggle.addEventListener('click', () => {
                navLinks.classList.toggle('open');
                navToggle.classList.toggle('active');
            });
            $$('a', navLinks).forEach(link => link.addEventListener('click', () => {
                navLinks.classList.remove('open');
                navToggle.classList.remove('active');
            }));
        }

        /* ────────────────────────────────────────────────────────
           SMOOTH ANCHOR
           ──────────────────────────────────────────────────────── */
        $$('a[href^="#"]').forEach(a => a.addEventListener('click', (e) => {
            const id = a.getAttribute('href');
            if (id === '#' || id.length < 2) return;
            const target = $(id);
            if (!target) return;
            e.preventDefault();
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }));

        /* ────────────────────────────────────────────────────────
           COUNTERS
           ──────────────────────────────────────────────────────── */
        const counters = $$('[data-count]');
        if (counters.length) {
            const obs = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        animateCounter(entry.target);
                        obs.unobserve(entry.target);
                    }
                });
            }, { threshold: 0.4 });
            counters.forEach(el => obs.observe(el));
        }
        function animateCounter(el) {
            const target = parseInt(el.getAttribute('data-count'), 10);
            if (Number.isNaN(target)) return;
            const dur = 1400;
            const start = performance.now();
            function step(now) {
                const p = Math.min((now - start) / dur, 1);
                const eased = 1 - Math.pow(1 - p, 4);
                el.textContent = Math.round(eased * target).toLocaleString();
                if (p < 1) requestAnimationFrame(step);
            }
            requestAnimationFrame(step);
        }

        /* ────────────────────────────────────────────────────────
           MOTION ONE REVEAL
           ──────────────────────────────────────────────────────── */
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (animate && inView && !reduceMotion) {
            $$('.reveal').forEach(el => inView(el, () => {
                animate(el, { opacity: [0, 1], transform: ['translateY(28px)', 'translateY(0)'] },
                    { duration: 0.65, easing: [0.16, 1, 0.3, 1] });
            }, { amount: 0.2 }));
            $$('.reveal-stagger').forEach(group => inView(group, () => {
                const items = group.querySelectorAll(':scope > *');
                animate(items, { opacity: [0, 1], transform: ['translateY(20px)', 'translateY(0)'] },
                    { duration: 0.55, delay: stagger ? stagger(0.08) : 0, easing: [0.16, 1, 0.3, 1] });
            }, { amount: 0.15 }));
            $$('.reveal-fade').forEach(el => inView(el, () => {
                animate(el, { opacity: [0, 1] }, { duration: 0.9, easing: 'ease-out' });
            }, { amount: 0.3 }));
            const heroBits = $$('.hero--premium [data-hero]');
            if (heroBits.length) {
                animate(heroBits, { opacity: [0, 1], transform: ['translateY(28px)', 'translateY(0)'] },
                    { duration: 0.85, delay: stagger ? stagger(0.13) : 0, easing: [0.16, 1, 0.3, 1] });
            }
        } else {
            const fb = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        entry.target.style.opacity = 1;
                        entry.target.style.transform = 'translateY(0)';
                        fb.unobserve(entry.target);
                    }
                });
            }, { rootMargin: '0px 0px -60px 0px', threshold: 0.1 });
            $$('.reveal, .reveal-fade').forEach(el => fb.observe(el));
            $$('.reveal-stagger > *').forEach(el => fb.observe(el));
        }

        /* ────────────────────────────────────────────────────────
           ACTIVE NAV LINK
           ──────────────────────────────────────────────────────── */
        const path = window.location.pathname;
        $$('.nav__links a').forEach(link => {
            const href = link.getAttribute('href');
            if (!href) return;
            if (href === path || (href !== '/' && path.startsWith(href))) link.classList.add('active');
        });

        /* ────────────────────────────────────────────────────────
           PARALLAX
           ──────────────────────────────────────────────────────── */
        const parallax = $$('[data-parallax]');
        if (parallax.length) {
            const onP = () => {
                const y = window.scrollY || 0;
                parallax.forEach(el => {
                    const speed = Number(el.getAttribute('data-parallax')) || 0.12;
                    el.style.transform = `translate3d(0, ${Math.round(y * speed)}px, 0)`;
                });
            };
            window.addEventListener('scroll', onP, { passive: true });
            onP();
        }

        /* ────────────────────────────────────────────────────────
           PREMIUM HERO ROTATION
           ──────────────────────────────────────────────────────── */
        (function initPremiumHero() {
            const heroWord = $('#vac-hero-word');
            const heroImage = $('#vac-hero-image');
            if (!heroWord && !heroImage) return;

            const words = ['CULTURE', 'DISCOVER', 'COMMUNITY', 'CREATE', 'EXPLORE'];
            const images = [
                '/uploads/arts page 4.jpg',
                '/uploads/volcano.jpg',
                '/uploads/media and dance.jpg',
                '/uploads/conservation3.jpg'
            ];
            const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            let wordIndex = 0;
            let imageIndex = 0;

            if (heroWord) {
                heroWord.style.transition = 'opacity 600ms ease, transform 600ms ease';
                heroWord.textContent = words[wordIndex];
                if (!reduceMotion) {
                    setInterval(() => {
                        heroWord.style.opacity = '0';
                        heroWord.style.transform = 'translateY(-20px)';
                        setTimeout(() => {
                            wordIndex = (wordIndex + 1) % words.length;
                            heroWord.textContent = words[wordIndex];
                            heroWord.style.transform = 'translateY(20px)';
                            requestAnimationFrame(() => {
                                heroWord.style.opacity = '1';
                                heroWord.style.transform = 'translateY(0)';
                            });
                        }, 300);
                    }, 4500);
                }
            }

            if (heroImage) {
                heroImage.style.transition = 'opacity 700ms ease';
                heroImage.addEventListener('load', () => {
                    heroImage.style.opacity = '1';
                });
                if (!reduceMotion) {
                    setInterval(() => {
                        imageIndex = (imageIndex + 1) % images.length;
                        heroImage.style.opacity = '0.35';
                        setTimeout(() => {
                            heroImage.src = images[imageIndex];
                        }, 320);
                    }, 7000);
                }
            }
        })();

        /* ────────────────────────────────────────────────────────
           HOME HERO SLIDER
           ──────────────────────────────────────────────────────── */
        (function initHomeHeroSlider() {
            const slides = $$('.hero-slide');
            const dots = $$('.hero-dot');
            const titleEl = $('#heroTitle');
            const captionEl = $('#heroCaption');
            if (!slides.length || !dots.length) return;

            const heroTitles = ['VIRUNGA', 'VOLCANO', 'CULTURE', 'WILDLIFE'];
            const heroCaptions = [
                'Mountain gorilla · Volcanoes National Park, Rwanda',
                'Virunga volcanic peaks · Northern Rwanda',
                'Traditional Intore dance · Musanze, Rwanda',
                'Rwanda volcanic hillside · East Africa'
            ];

            let currentSlide = 0;
            let intervalId = null;

            const goToSlide = (index) => {
                const next = (index + slides.length) % slides.length;
                slides[currentSlide].classList.remove('active');
                dots[currentSlide].classList.remove('active');
                currentSlide = next;
                slides[currentSlide].classList.add('active');
                dots[currentSlide].classList.add('active');

                if (titleEl) {
                    titleEl.style.opacity = '0';
                    setTimeout(() => {
                        titleEl.textContent = heroTitles[currentSlide];
                        titleEl.style.opacity = '1';
                    }, 280);
                }
                if (captionEl) {
                    captionEl.textContent = heroCaptions[currentSlide];
                }
            };

            dots.forEach(dot => {
                dot.addEventListener('click', () => {
                    const idx = parseInt(dot.dataset.slide || '0', 10);
                    goToSlide(Number.isNaN(idx) ? 0 : idx);
                });
            });

            if (!reduceMotion) {
                intervalId = window.setInterval(() => {
                    goToSlide(currentSlide + 1);
                }, 5000);
                window.addEventListener('beforeunload', () => {
                    if (intervalId) window.clearInterval(intervalId);
                });
            }
        })();

        /* ────────────────────────────────────────────────────────
           NOTIFICATION BELL
           ──────────────────────────────────────────────────────── */
        (function initBell() {
            const bell     = $('#bell');
            const trigger  = $('#bell-trigger');
            const panel    = $('#bell-dropdown');
            const dot      = $('#bell-dot');
            const unreadEl = $('#bell-unread-count');
            const list     = $('#bell-list');
            const filters  = $('#bell-filters');
            const markAll  = $('#bell-mark-all');
            if (!trigger || !panel) return;

            const open = () => {
                panel.removeAttribute('hidden');
                trigger.setAttribute('aria-expanded', 'true');
            };
            const close = () => {
                panel.setAttribute('hidden', '');
                trigger.setAttribute('aria-expanded', 'false');
            };
            trigger.addEventListener('click', (e) => {
                e.stopPropagation();
                if (panel.hasAttribute('hidden')) open(); else close();
            });
            document.addEventListener('click', (e) => {
                if (bell && !bell.contains(e.target)) close();
            });
            document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });

            // Filter tabs
            if (filters) {
                filters.addEventListener('click', (e) => {
                    const btn = e.target.closest('.bell__filter');
                    if (!btn) return;
                    $$('.bell__filter', filters).forEach(b => b.classList.remove('is-active'));
                    btn.classList.add('is-active');
                    applyFilter(btn.dataset.filter);
                });
            }
            function applyFilter(filter) {
                if (!list) return;
                const map = {
                    orders:   ['order_shipped', 'order_delivered', 'payment_success', 'payment_failed', 'order'],
                    bookings: ['booking_confirmed', 'booking_rejected', 'booking'],
                    messages: ['message_received', 'message']
                };
                $$('.notif-item', list).forEach(item => {
                    if (filter === 'all') { item.style.display = ''; return; }
                    const t = (item.dataset.notifType || '').toLowerCase();
                    const wanted = map[filter] || [];
                    const match = wanted.some(k => t.includes(k));
                    item.style.display = match ? '' : 'none';
                });
            }

            // Mark all read
            if (markAll) {
                markAll.addEventListener('click', async () => {
                    const csrf = getCsrf();
                    const headers = { 'Accept': 'application/json' };
                    if (csrf) headers[csrf.header] = csrf.token;
                    try {
                        await fetch('/api/v1/client/notifications/read-all', {
                            method: 'POST', headers, credentials: 'same-origin'
                        });
                    } catch (_) {}
                    $$('.notif-item.is-unread', list).forEach(it => it.classList.remove('is-unread'));
                    if (dot) dot.hidden = true;
                    if (unreadEl) unreadEl.textContent = '';
                    vacToast('All notifications marked as read');
                });
            }

            // Poll unread count every 30s
            async function refreshUnread() {
                try {
                    const headers = { 'Accept': 'application/json' };
                    const csrf = getCsrf();
                    if (csrf) headers[csrf.header] = csrf.token;
                    const res = await fetch('/api/v1/client/notifications/unread-count', { headers, credentials: 'same-origin' });
                    if (!res.ok) return;
                    const json = await res.json();
                    const n = (json && json.data && typeof json.data.unread === 'number') ? json.data.unread : 0;
                    if (dot) {
                        if (n === 0) {
                            dot.hidden = true;
                            dot.textContent = '';
                        } else {
                            dot.hidden = false;
                            dot.textContent = n > 9 ? '9+' : String(n);
                        }
                    }
                    if (unreadEl) unreadEl.textContent = n > 0 ? '(' + n + ' new)' : '';
                } catch (_) { /* bell is non-critical */ }
            }

            async function refreshMessages() {
                const msgBadge = $('#messages-badge');
                if (!msgBadge) return;
                try {
                    const headers = { 'Accept': 'application/json' };
                    const csrf = getCsrf();
                    if (csrf) headers[csrf.header] = csrf.token;
                    const res = await fetch('/api/v1/client/messages/unread-count', { headers, credentials: 'same-origin' });
                    if (!res.ok) return;
                    const json = await res.json();
                    const n = (json && json.data && typeof json.data.unread === 'number') ? json.data.unread : 0;
                    if (n === 0) {
                        msgBadge.hidden = true;
                        msgBadge.textContent = '';
                    } else {
                        msgBadge.hidden = false;
                        msgBadge.textContent = n > 9 ? '9+' : String(n);
                    }
                } catch (_) { /* non-critical */ }
            }

            if (bell) {
                refreshUnread();
                refreshMessages();
                setInterval(refreshUnread, 30000);
                setInterval(refreshMessages, 30000);
            }
        })();

        /* ────────────────────────────────────────────────────────
           CART STORE + DRAWER
           ──────────────────────────────────────────────────────── */
        const Cart = (function () {
            const KEY = 'vac-cart-v1';
            let items = [];
            try {
                const raw = localStorage.getItem(KEY);
                if (raw) items = JSON.parse(raw) || [];
            } catch (_) { items = []; }

            function persist() {
                try { localStorage.setItem(KEY, JSON.stringify(items)); } catch (_) {}
            }
            function count() { return items.length; }
            function total() {
                return items.reduce((sum, it) => sum + (Number(it.price) || 0), 0);
            }
            function has(id) { return items.some(it => String(it.id) === String(id)); }
            function add(item) {
                if (!item || !item.id) return false;
                if (has(item.id)) return false;
                items.push({
                    id: item.id,
                    name: item.name,
                    price: Number(item.price) || 0,
                    image: item.image || '',
                    artist: item.artist || '',
                    unique: !!item.unique,
                    addedAt: Date.now()
                });
                persist(); render(); pulseDot();
                return true;
            }
            function remove(id) {
                items = items.filter(it => String(it.id) !== String(id));
                persist(); render();
            }
            function clear() { items = []; persist(); render(); }

            // ── DOM elements ──
            const drawer  = $('#cart-drawer');
            const overlay = $('#cart-overlay');
            const empty   = $('#cart-empty');
            const itemsEl = $('#cart-items');
            const foot    = $('#cart-foot');
            const totalEl = $('#cart-total');
            const countEl = $('#cart-count');
            const dot     = $('#cart-dot');
            const navCount = $('#cart-nav-count');

            function pulseDot() {
                if (!dot) return;
                dot.hidden = count() === 0;
                if (navCount) {
                    navCount.textContent = String(count());
                    navCount.style.display = count() > 0 ? 'inline-flex' : 'none';
                }
            }

            function open() {
                if (!drawer || !overlay) return;
                drawer.classList.add('is-open');
                overlay.classList.add('is-open');
                drawer.setAttribute('aria-hidden', 'false');
                document.body.style.overflow = 'hidden';
            }
            function close() {
                if (!drawer || !overlay) return;
                drawer.classList.remove('is-open');
                overlay.classList.remove('is-open');
                drawer.setAttribute('aria-hidden', 'true');
                document.body.style.overflow = '';
            }
            function toggle() {
                drawer && drawer.classList.contains('is-open') ? close() : open();
            }

            function render() {
                if (countEl) countEl.textContent = '(' + count() + ')';
                pulseDot();
                if (!itemsEl || !empty || !foot) return;
                if (count() === 0) {
                    empty.style.display = '';
                    itemsEl.hidden = true;
                    foot.hidden = true;
                    return;
                }
                empty.style.display = 'none';
                itemsEl.hidden = false;
                foot.hidden = false;
                itemsEl.innerHTML = items.map(it => `
                    <div class="cart-item" data-cart-item="${escapeHtml(String(it.id))}">
                        <div class="cart-item__thumb">
                            ${it.image ? `<img src="${escapeHtml(it.image)}" alt="${escapeHtml(it.name)}" />` : ''}
                        </div>
                        <div>
                            <h5 class="cart-item__title">${escapeHtml(it.name || 'Artwork')}</h5>
                            ${it.artist ? `<p class="cart-item__artist">by ${escapeHtml(it.artist)}</p>` : ''}
                            ${it.unique ? `<p class="cart-item__reservation">
                                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                                Reserved · 1 of 1</p>` : ''}
                        </div>
                        <div style="text-align:right;">
                            <p class="cart-item__price">$${(Number(it.price) || 0).toFixed(2)}</p>
                            <button type="button" class="cart-item__remove" data-cart-remove="${escapeHtml(String(it.id))}">Remove</button>
                        </div>
                    </div>
                `).join('');
                if (totalEl) totalEl.textContent = '$' + total().toFixed(2);
            }

            // Wire openers / closers
            $$('[data-cart-open]').forEach(el => el.addEventListener('click', (e) => { e.preventDefault(); open(); }));
            $$('[data-cart-close]').forEach(el => el.addEventListener('click', (e) => {
                close();
                if (el.tagName !== 'A' || !el.getAttribute('href') || el.getAttribute('href') === '#') {
                    e.preventDefault();
                }
            }));
            document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });

            // Wire remove buttons (delegated)
            if (itemsEl) {
                itemsEl.addEventListener('click', (e) => {
                    const btn = e.target.closest('[data-cart-remove]');
                    if (!btn) return;
                    remove(btn.getAttribute('data-cart-remove'));
                    vacToast('Removed from cart');
                });
            }

            // Initial render
            render();

            return { add, remove, clear, count, total, has, open, close, toggle };
        })();

        // Wire .cart-btn add buttons across the page
        const isAuthedForCart = document.body && document.body.dataset.authenticated === 'true';

        function redirectToLoginForCart() {
            const returnPath = window.location.pathname + window.location.search + window.location.hash;
            vacToast('Please sign in to add items to your cart.');
            window.setTimeout(function () {
                window.location.href = '/login?redirect=' + encodeURIComponent(returnPath || '/art-store');
            }, 700);
        }

        $$('.cart-btn').forEach(btn => {
            const id = btn.dataset.artworkId;
            if (!id) return;
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                if (!isAuthedForCart) {
                    redirectToLoginForCart();
                    return;
                }
                if (btn.classList.contains('is-adding') || btn.classList.contains('is-added') || btn.classList.contains('is-sold-out')) return;
                const item = {
                    id: btn.dataset.artworkId,
                    name: btn.dataset.artworkName,
                    price: btn.dataset.artworkPrice,
                    image: btn.dataset.artworkImage,
                    artist: btn.dataset.artworkArtist,
                    unique: btn.dataset.artworkUnique === 'true'
                };
                
                // Show adding state
                btn.classList.add('is-adding');
                if (btn.querySelector('.idle')) btn.querySelector('.idle').hidden = true;
                if (btn.querySelector('.adding')) btn.querySelector('.adding').hidden = false;
                
                setTimeout(() => {
                    const ok = Cart.add(item);
                    btn.classList.remove('is-adding');
                    if (ok) {
                        showAddedState(btn);
                        vacToast(item.name ? (item.name + ' added to cart') : 'Added to cart');
                    } else {
                        showAddedState(btn);
                    }
                }, 380);
            });
        });

        function showAddedState(btn) {
            btn.classList.add('is-added');
            if (btn.querySelector('.adding')) btn.querySelector('.adding').hidden = true;
            if (btn.querySelector('.added')) btn.querySelector('.added').hidden = false;
            
            if (btn._vacAddedTimer) {
                clearTimeout(btn._vacAddedTimer);
            }
            btn._vacAddedTimer = setTimeout(() => {
                resetCartButton(btn);
            }, 2500);
        }

        function resetCartButton(btn) {
            btn.classList.remove('is-added', 'is-adding');
            if (btn.querySelector('.added')) btn.querySelector('.added').hidden = true;
            if (btn.querySelector('.adding')) btn.querySelector('.adding').hidden = true;
            if (btn.querySelector('.idle')) btn.querySelector('.idle').hidden = false;
        }

        /* ────────────────────────────────────────────────────────
           CHAT HUB
           ──────────────────────────────────────────────────────── */
        (function initChat() {
            const fab     = $('#chat-fab');
            const panel   = $('#chat-panel');
            const close   = $('#chat-close');
            const body    = $('#chat-body');
            const form    = $('#chat-form');
            const input   = $('#chat-input');
            const send    = form ? form.querySelector('.chat-panel__send') : null;
            const prechat = $('#chat-prechat');
            const openChatLinks = $$('[data-open-chat]');
            if (!fab || !panel) return;

            const isAuthed = body && body.dataset.authenticated === 'true';
            let conversationId = sessionStorage.getItem('vac-chat-cid') || null;
            let started = isAuthed || !!conversationId;

            // Hide pre-chat for authed users
            if (prechat && started) prechat.style.display = 'none';

            const open  = () => panel.classList.add('is-open');
            const closeFn = () => panel.classList.remove('is-open');
            fab.addEventListener('click', open);
            openChatLinks.forEach(link => link.addEventListener('click', (e) => {
                e.preventDefault();
                open();
                if (input) input.focus();
            }));
            if (close) close.addEventListener('click', closeFn);
            document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeFn(); });
            if (window.location.hash === '#live-chat') {
                requestAnimationFrame(() => {
                    open();
                    if (input) input.focus();
                });
            }

            // Enable send when input has content
            if (input && send) {
                input.addEventListener('input', () => {
                    send.disabled = input.value.trim().length === 0;
                });
            }

            function appendMessage(text, dir, ts) {
                if (!body) return;
                const div = document.createElement('div');
                div.className = 'chat-msg ' + (dir === 'out' ? 'chat-msg--out' : 'chat-msg--in');
                const safe = escapeHtml(text);
                const time = ts ? new Date(ts) : new Date();
                const hh = String(time.getHours()).padStart(2, '0');
                const mm = String(time.getMinutes()).padStart(2, '0');
                div.innerHTML = safe + '<span class="chat-msg__time">' + hh + ':' + mm + '</span>';
                body.appendChild(div);
                body.scrollTop = body.scrollHeight;
            }

            function appendTyping() {
                const t = document.createElement('div');
                t.className = 'chat-typing';
                t.id = 'chat-typing';
                t.innerHTML = '<span class="chat-typing__dot"></span><span class="chat-typing__dot"></span><span class="chat-typing__dot"></span>';
                body.appendChild(t);
                body.scrollTop = body.scrollHeight;
            }
            function removeTyping() { const t = $('#chat-typing'); if (t) t.remove(); }

            // Pre-chat submit (unauthed)
            if (prechat) {
                prechat.addEventListener('submit', async () => {
                    const name    = $('#chat-name').value.trim();
                    const email   = $('#chat-email').value.trim();
                    const topic   = $('#chat-topic').value;
                    const message = $('#chat-msg').value.trim();
                    if (!name || !email || message.length < 10) {
                        vacToast('Please fill in all fields (message at least 10 chars)', 'error');
                        return;
                    }
                    try {
                        const csrf = getCsrf();
                        const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
                        if (csrf) headers[csrf.header] = csrf.token;
                        const res = await fetch('/api/v1/public/chat/start', {
                            method: 'POST', headers, credentials: 'same-origin',
                            body: JSON.stringify({ name, email, topic, message })
                        });
                        if (res.ok) {
                            const json = await res.json().catch(() => ({}));
                            if (json && json.data && json.data.conversationId) {
                                conversationId = json.data.conversationId;
                                sessionStorage.setItem('vac-chat-cid', conversationId);
                            }
                        }
                    } catch (_) { /* fall back to UX-only flow */ }
                    started = true;
                    prechat.style.display = 'none';
                    appendMessage(message, 'out');
                    appendTyping();
                    setTimeout(() => {
                        removeTyping();
                        appendMessage('Thanks ' + name + '! A team member will respond shortly. Our typical response time is under 2 hours.', 'in');
                    }, 1400);
                });
            }

            // Active conversation send
            if (form && input) {
                form.addEventListener('submit', async () => {
                    const text = input.value.trim();
                    if (!text) return;
                    appendMessage(text, 'out');
                    input.value = '';
                    if (send) send.disabled = true;

                    appendTyping();
                    try {
                        const csrf = getCsrf();
                        const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
                        if (csrf) headers[csrf.header] = csrf.token;
                        const url = isAuthed
                            ? '/api/v1/client/chat/messages'
                            : (conversationId ? '/api/v1/public/chat/' + conversationId + '/messages' : '/api/v1/public/chat/start');
                        await fetch(url, {
                            method: 'POST', headers, credentials: 'same-origin',
                            body: JSON.stringify({ content: text, type: 'TEXT' })
                        });
                    } catch (_) { /* swallow — UX continues */ }
                    setTimeout(() => {
                        removeTyping();
                        appendMessage('Got it — we\'ll get back to you shortly.', 'in');
                    }, 900);
                });
            }
        })();

        /* ────────────────────────────────────────────────────────
           MICRO INTERACTIONS
           ──────────────────────────────────────────────────────── */
        (function initMicroInteractions() {
            const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            if (reduce) return;
            const cards = $$('.vac-trip-card, .vac-story-card');
            cards.forEach((card) => {
                card.addEventListener('pointermove', (e) => {
                    const r = card.getBoundingClientRect();
                    const x = (e.clientX - r.left) / r.width - 0.5;
                    const y = (e.clientY - r.top) / r.height - 0.5;
                    card.style.transform = 'translateY(-3px) rotateX(' + (-y * 3.2).toFixed(2) + 'deg) rotateY(' + (x * 3.2).toFixed(2) + 'deg)';
                });
                card.addEventListener('pointerleave', () => {
                    card.style.transform = '';
                });
            });
        })();

    });
})();
