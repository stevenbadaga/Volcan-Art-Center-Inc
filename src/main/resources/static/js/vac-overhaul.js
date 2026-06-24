(function () {
    'use strict';

    function $$(selector, root) {
        return Array.from((root || document).querySelectorAll(selector));
    }

    function markRevealTargets() {
        var selectors = [
            '.vac-section',
            '.vac-section-head',
            '.vac-art-card',
            '.card',
            '.tour-card',
            '.vac-exp-card',
            '.vac-blog-card',
            '.vac-focus-card',
            '.vac-trip-card',
            '.dept-block',
            '.split-section__media',
            '.vac-cart-item',
            '.vac-cart-empty',
            '.vac-cart-summary',
            '.admin-card',
            '.admin-panel',
            '.admin-kpi',
            '.admin-chart-container',
            '.vac-polaroid',
            '.vac-trust-item',
            '.vac-command-hero',
            '.vac-command-kpi',
            '.vac-command-panel',
            '.vac-action-tile',
            '.vac-user-command',
            '.vac-message-row',
            '.vac-notification-row',
            '.vac-chat-bubble',
            '.vac-stage-card',
            '.vac-shop-filter',
            '.vac-premium-art-card',
            '.vac-premium-exp-card',
            '.vac-premium-story-card',
            '.vac-home-polaroid',
            '.vac-home-proof-grid span',
            '.vac-home-rating-card',
            '.vac-home-why__copy .btn'
        ];

        $$(selectors.join(',')).forEach(function (el) {
            if (el.closest('.vac-home-premium .vac-stage, .vac-home-premium .hero-golden, .vac-home-premium .stats-strip, .vac-home-premium .journey-section, .vac-home-premium .vac-intl-bar')) {
                return;
            }
            if (!el.hasAttribute('data-vac-reveal')) {
                el.setAttribute('data-vac-reveal', '');
            }
        });
    }

    function initLiveCharts() {
        $$('.vac-live-chart').forEach(function (chart) {
            var bars = $$('.vac-live-bar', chart);
            var values = bars.map(function (bar) {
                return Number(bar.getAttribute('data-value') || 0);
            });
            var max = Math.max(1, Math.max.apply(Math, values));
            bars.forEach(function (bar, index) {
                var value = values[index] || 0;
                var width = Math.max(value > 0 ? 8 : 0, Math.round((value / max) * 100));
                window.requestAnimationFrame(function () {
                    bar.style.setProperty('--bar-width', width + '%');
                });
            });
        });
    }

    function initCountUp() {
        var counters = $$('.vac-count-up[data-value]');
        if (!counters.length) return;
        var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        function format(value) {
            return Math.round(value).toLocaleString();
        }

        function animate(el) {
            var target = Number(String(el.getAttribute('data-value') || '0').replace(/[^0-9.\-]/g, ''));
            if (!isFinite(target)) return;
            if (reduce || target <= 0) {
                el.textContent = format(target);
                return;
            }
            var start = performance.now();
            var duration = 900 + Math.min(target, 120) * 4;
            function frame(now) {
                var progress = Math.min(1, (now - start) / duration);
                var eased = 1 - Math.pow(1 - progress, 3);
                el.textContent = format(target * eased);
                if (progress < 1) {
                    requestAnimationFrame(frame);
                }
            }
            requestAnimationFrame(frame);
        }

        if (!('IntersectionObserver' in window)) {
            counters.forEach(animate);
            return;
        }

        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting || entry.target.dataset.counted === 'true') return;
                entry.target.dataset.counted = 'true';
                animate(entry.target);
                observer.unobserve(entry.target);
            });
        }, { threshold: 0.35 });

        counters.forEach(function (el) { observer.observe(el); });
    }

    function initReveal() {
        var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        var targets = $$('.reveal, [data-vac-reveal], .vac-split-text');
        if (!targets.length) return;

        if (reduce || !('IntersectionObserver' in window)) {
            targets.forEach(function (el) { el.classList.add('is-visible'); });
            return;
        }

        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-visible');
                    // Once visible, we can stop observing
                    observer.unobserve(entry.target);
                }
            });
        }, { 
            rootMargin: '0px 0px -10% 0px', 
            threshold: 0.15 
        });

        targets.forEach(function (el) {
            var rect = el.getBoundingClientRect();
            if (rect.top < window.innerHeight * 0.92 && rect.bottom > 0) {
                el.classList.add('is-visible');
            }
            observer.observe(el);
        });
    }

    function initHeroParallax() {
        var hero = document.querySelector('.vac-commerce-hero, .vac-home-hero, .hero--premium, .page-header');
        var bg = document.querySelector('.vac-commerce-hero__bg img, .vac-home-hero__bg img, .hero__bg img');
        if (!hero || !bg) return;
        if (hero.querySelector('.vac-home-hero__slide')) return;
        var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduce) return;

        function tick() {
            var rect = hero.getBoundingClientRect();
            var progress = Math.max(-1, Math.min(1, rect.top / Math.max(1, window.innerHeight)));
            bg.style.transform = 'scale(1.06) translate3d(0,' + Math.round(progress * -26) + 'px,0)';
        }

        window.addEventListener('scroll', tick, { passive: true });
        window.addEventListener('resize', tick);
        tick();
    }

    function initClickableCards() {
        $$('.vac-clickable-card[data-card-url]').forEach(function (card) {
            card.setAttribute('tabindex', '0');
            card.setAttribute('role', 'link');

            function openCard(event) {
                var interactive = event.target.closest('a, button, input, select, textarea, form, details, summary');
                if (interactive) return;
                var url = card.getAttribute('data-card-url');
                if (url) window.location.href = url;
            }

            card.addEventListener('click', openCard);
            card.addEventListener('keydown', function (event) {
                if (event.key !== 'Enter' && event.key !== ' ') return;
                event.preventDefault();
                var url = card.getAttribute('data-card-url');
                if (url) window.location.href = url;
            });
        });
    }

    function initMagneticMedia() {
        var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduce) return;

        $$('.vac-art-card, .card--editorial, .tour-card, .vac-trip-card, .vac-blog-card, .vac-focus-card, .dept-block__media, .vac-home-hero__frame, .vac-command-kpi, .vac-action-tile, .vac-command-panel, .vac-stage-card, .vac-premium-art-card, .vac-premium-exp-card, .vac-premium-story-card').forEach(function (card) {
            card.addEventListener('pointermove', function (event) {
                var rect = card.getBoundingClientRect();
                var x = (event.clientX - rect.left) / rect.width - 0.5;
                var y = (event.clientY - rect.top) / rect.height - 0.5;
                card.style.setProperty('--tilt-x', (y * -4).toFixed(2) + 'deg');
                card.style.setProperty('--tilt-y', (x * 4).toFixed(2) + 'deg');
                card.style.setProperty('--glow-x', ((x + 0.5) * 100).toFixed(1) + '%');
                card.style.setProperty('--glow-y', ((y + 0.5) * 100).toFixed(1) + '%');
            });
            card.addEventListener('pointerleave', function () {
                card.style.setProperty('--tilt-x', '0deg');
                card.style.setProperty('--tilt-y', '0deg');
            });
        });
    }

    function initBackToTop() {
        if (document.querySelector('.back-to-top')) return;
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'back-to-top';
        btn.setAttribute('aria-label', 'Back to top');
        btn.innerHTML = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5"/><path d="M5 12l7-7 7 7"/></svg>';
        btn.style.position = 'fixed';
        btn.style.right = '24px';
        btn.style.bottom = '90px';
        btn.style.zIndex = '260';
        btn.style.opacity = '0';
        btn.style.pointerEvents = 'none';
        btn.addEventListener('click', function () {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
        document.body.appendChild(btn);

        function toggle() {
            var visible = window.scrollY > 600;
            btn.style.opacity = visible ? '1' : '0';
            btn.style.pointerEvents = visible ? 'auto' : 'none';
        }

        window.addEventListener('scroll', toggle, { passive: true });
        toggle();
    }

    function initCarousels() {
        $$('.vac-carousel-wrap').forEach(function (wrap) {
            var track = wrap.querySelector('.vac-carousel-track');
            var prevBtn = wrap.querySelector('[data-carousel-prev]');
            var nextBtn = wrap.querySelector('[data-carousel-next]');
            if (!track) return;

            var scrollAmount = 340;

            if (prevBtn) {
                prevBtn.addEventListener('click', function () {
                    track.scrollBy({ left: -scrollAmount, behavior: 'smooth' });
                });
            }
            if (nextBtn) {
                nextBtn.addEventListener('click', function () {
                    track.scrollBy({ left: scrollAmount, behavior: 'smooth' });
                });
            }

            // Auto-scroll right-to-left every 4 seconds
            var autoInterval = setInterval(function () {
                var maxScroll = track.scrollWidth - track.clientWidth;
                if (track.scrollLeft >= maxScroll - 10) {
                    track.scrollTo({ left: 0, behavior: 'smooth' });
                } else {
                    track.scrollBy({ left: scrollAmount, behavior: 'smooth' });
                }
            }, 4000);

            // Pause auto-scroll on hover
            track.addEventListener('mouseenter', function () { clearInterval(autoInterval); });
            track.addEventListener('mouseleave', function () {
                autoInterval = setInterval(function () {
                    var maxScroll = track.scrollWidth - track.clientWidth;
                    if (track.scrollLeft >= maxScroll - 10) {
                        track.scrollTo({ left: 0, behavior: 'smooth' });
                    } else {
                        track.scrollBy({ left: scrollAmount, behavior: 'smooth' });
                    }
                }, 4000);
            });
        });
    }

    function initHeroRotation() {
        var hero = document.querySelector('.hero-golden--home');
        if (!hero) return;
        var title = hero.querySelector('.hero-golden__title');
        if (!title) return;

        var slides = Array.from(hero.querySelectorAll('.hero-golden__bg .hero-slide'));
        var dots = Array.from(hero.querySelectorAll('.hero-golden__slide-dot'));
        var words = ['WILDLIFE', 'CULTURE', 'ART', 'COMMUNITY'];
        var index = 0;
        var interval = 6000;

        title.style.transition = 'opacity 0.45s ease';

        if (slides.length === 1) {
            slides[0].classList.add('active');
            slides[0].style.opacity = '1';
        }

        function goTo(next) {
            index = ((next % words.length) + words.length) % words.length;

            if (slides.length > 1) {
                var slideIndex = index % slides.length;
                slides.forEach(function (s, i) {
                    s.classList.toggle('active', i === slideIndex);
                });
                dots.forEach(function (d, i) {
                    d.classList.toggle('active', i === slideIndex);
                });
            }

            title.style.opacity = '0';
            setTimeout(function () {
                title.textContent = words[index];
                title.style.opacity = '1';
            }, 400);
        }

        if (slides.length > 1) {
            slides.forEach(function (s) { s.style.transition = 'opacity 1.6s ease'; });
        }

        setInterval(function () {
            goTo(index + 1);
        }, interval);
    }

    function initNavScroll() {
        var nav = document.getElementById('main-nav');
        if (!nav) return;

        function checkScroll() {
            if (window.scrollY > 50) {
                nav.classList.add('is-scrolled');
                nav.classList.add('scrolled');
            } else {
                nav.classList.remove('is-scrolled');
                nav.classList.remove('scrolled');
            }
        }

        window.addEventListener('scroll', checkScroll, { passive: true });
        checkScroll();
    }

    function initCinematicHeroSlides() {
        var heroes = document.querySelectorAll('.vac-cinematic-hero:not(.hero-golden--home):not(.hero-golden--editorial-bg)');
        heroes.forEach(function(hero) {
            var slides = Array.from(hero.querySelectorAll('.hero-slide'));
            if (slides.length < 2) return;
            var current = 0;
            setInterval(function() {
                slides[current].classList.remove('active');
                current = (current + 1) % slides.length;
                slides[current].classList.add('active');
            }, 7000);
        });
    }

    function initCinematicParallax() {
        var heroes = document.querySelectorAll('.vac-cinematic-hero .vac-cinematic-hero__bg, .vac-cinematic-hero .hero-golden__bg');
        if (!heroes.length) return;
        var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduceMotion) return;

        function onScroll() {
            var y = window.scrollY || 0;
            heroes.forEach(function(bg) {
                var hero = bg.closest('.vac-cinematic-hero, .hero-golden');
                if (!hero) return;
                var rect = hero.getBoundingClientRect();
                if (rect.bottom < 0 || rect.top > window.innerHeight) return;
                var offset = Math.min(Math.max(y * 0.12, 0), 80);
                bg.style.transform = 'translate3d(0,' + offset + 'px,0)';
            });
        }
        window.addEventListener('scroll', onScroll, { passive: true });
        onScroll();
    }

    function initNavMegaMenu() {
        var items = document.querySelectorAll('.nav-item--mega');
        if (!items.length) return;
        var closeTimers = new WeakMap();

        function closeAll(except) {
            items.forEach(function (el) {
                if (except && el === except) return;
                el.classList.remove('is-open');
                var toggle = el.querySelector('.nav-mega__mobile-toggle');
                if (toggle) {
                    toggle.setAttribute('aria-expanded', 'false');
                }
            });
        }

        items.forEach(function (item) {
            var open = function () {
                if (window.innerWidth <= 980) return;
                var pending = closeTimers.get(item);
                if (pending) window.clearTimeout(pending);
                closeAll(item);
                item.classList.add('is-open');
            };

            var closeSoon = function () {
                if (window.innerWidth <= 980) return;
                var timer = window.setTimeout(function () {
                    item.classList.remove('is-open');
                }, 200);
                closeTimers.set(item, timer);
            };

            // Mouse events for desktop
            item.addEventListener('mouseenter', open);
            item.addEventListener('mouseleave', closeSoon);

            // Focus events for keyboard accessibility
            item.addEventListener('focusin', open);
            item.addEventListener('focusout', function (event) {
                if (item.contains(event.relatedTarget)) return;
                item.classList.remove('is-open');
            });

            // Click event for mobile toggling (for backup/fallback consistency)
            var mobileToggle = item.querySelector('.nav-mega__mobile-toggle');
            if (mobileToggle) {
                mobileToggle.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var isOpen = item.classList.contains('is-open');
                    if (isOpen) {
                        item.classList.remove('is-open');
                        this.setAttribute('aria-expanded', 'false');
                    } else {
                        closeAll();
                        item.classList.add('is-open');
                        this.setAttribute('aria-expanded', 'true');
                    }
                });
            }
        });

        // Close on clicking outside the mega menus
        document.addEventListener('click', function (e) {
            var insideMega = false;
            items.forEach(function (el) {
                if (el.contains(e.target)) {
                    insideMega = true;
                }
            });
            if (!insideMega) {
                closeAll();
            }
        });

        // Close on Escape key press
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                closeAll();
            }
        });

        // Close on clicking dropdown links
        var dropdownLinks = document.querySelectorAll('.nav-mega a');
        dropdownLinks.forEach(function (link) {
            link.addEventListener('click', function () {
                closeAll();
            });
        });
    }

    function initKpiSparklines() {
        var cards = $$('.admin-kpi');
        if (!cards.length) return;

        function series(seed, count) {
            var pts = [];
            var v = 0.5;
            for (var i = 0; i < count; i++) {
                var r = Math.sin((seed + 1) * 9.13 + i * 2.7) * 0.5 + 0.5;
                v = v * 0.55 + r * 0.45;
                pts.push(v);
            }
            pts[count - 1] = Math.min(1, pts[count - 1] + 0.14);
            return pts;
        }

        function draw(canvas, data) {
            var w = canvas.clientWidth || canvas.parentNode.clientWidth || 240;
            var h = canvas.clientHeight || 42;
            if (!w) return;
            var ratio = window.devicePixelRatio || 1;
            canvas.width = w * ratio;
            canvas.height = h * ratio;
            var ctx = canvas.getContext('2d');
            if (!ctx) return;
            ctx.scale(ratio, ratio);
            var n = data.length, pad = 3;
            var max = Math.max.apply(null, data), min = Math.min.apply(null, data);
            var range = (max - min) || 1;
            function x(i) { return (i / (n - 1)) * w; }
            function y(val) { return h - pad - ((val - min) / range) * (h - pad * 2); }
            var grad = ctx.createLinearGradient(0, 0, 0, h);
            grad.addColorStop(0, 'rgba(0,166,81,0.22)');
            grad.addColorStop(1, 'rgba(0,166,81,0)');
            ctx.beginPath();
            ctx.moveTo(0, y(data[0]));
            for (var i = 1; i < n; i++) ctx.lineTo(x(i), y(data[i]));
            ctx.lineTo(w, h); ctx.lineTo(0, h); ctx.closePath();
            ctx.fillStyle = grad; ctx.fill();
            ctx.beginPath();
            ctx.moveTo(0, y(data[0]));
            for (var j = 1; j < n; j++) ctx.lineTo(x(j), y(data[j]));
            ctx.strokeStyle = 'rgba(0,166,81,0.85)';
            ctx.lineWidth = 1.6; ctx.lineJoin = 'round'; ctx.stroke();
            ctx.beginPath();
            ctx.arc(w - 2.5, y(data[n - 1]), 2.4, 0, Math.PI * 2);
            ctx.fillStyle = '#00A651'; ctx.fill();
        }

        var registry = [];
        cards.forEach(function (card) {
            if (card.querySelector('.admin-kpi__spark')) return;
            var valEl = card.querySelector('.admin-kpi__value');
            var seed = valEl ? (parseInt(String(valEl.textContent).replace(/[^0-9]/g, ''), 10) || card.textContent.length) : card.textContent.length;
            var canvas = document.createElement('canvas');
            canvas.className = 'admin-kpi__spark';
            canvas.setAttribute('aria-hidden', 'true');
            card.appendChild(canvas);
            var data = series(seed, 24);
            registry.push({ canvas: canvas, data: data });
            window.requestAnimationFrame(function () { draw(canvas, data); });
        });

        var resizeTimer;
        window.addEventListener('resize', function () {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function () {
                registry.forEach(function (r) { draw(r.canvas, r.data); });
            }, 200);
        });
    }

    function initThemeSystem() {
        var themeToggle = document.getElementById('theme-toggle');
        var themeMenu = document.getElementById('theme-menu');
        var themeOptions = $$('.theme-option');

        if (!themeToggle || !themeMenu) return;

        function applyTheme(value) {
            var resolvedTheme = value;
            if (value === 'system') {
                resolvedTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
            }
            
            document.documentElement.setAttribute('data-theme', resolvedTheme);
            document.documentElement.setAttribute('data-user-preference', value);
            localStorage.setItem('vac-theme', value);

            // Update active state in menu
            themeOptions.forEach(function (opt) {
                opt.classList.toggle('is-active', opt.getAttribute('data-theme-value') === value);
            });

            // Close menu
            themeMenu.classList.remove('is-open');
            themeToggle.setAttribute('aria-expanded', 'false');

            // Dispatch event for theme-aware elements (e.g. admin charts)
            window.dispatchEvent(new CustomEvent('vac:theme-changed', { detail: { theme: resolvedTheme } }));
        }

        themeToggle.addEventListener('click', function (e) {
            e.stopPropagation();
            var isOpen = themeMenu.classList.contains('is-open');
            themeMenu.classList.toggle('is-open', !isOpen);
            themeToggle.setAttribute('aria-expanded', !isOpen);
        });

        themeOptions.forEach(function (opt) {
            opt.addEventListener('click', function (e) {
                e.stopPropagation();
                var value = opt.getAttribute('data-theme-value');
                applyTheme(value);
            });
        });

        document.addEventListener('click', function () {
            themeMenu.classList.remove('is-open');
            themeToggle.setAttribute('aria-expanded', 'false');
        });

        // Sync initial state
        var savedTheme = localStorage.getItem('vac-theme') || 'system';
        themeOptions.forEach(function (opt) {
            opt.classList.toggle('is-active', opt.getAttribute('data-theme-value') === savedTheme);
        });

        // Listen for system theme changes if in system mode
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
            if (localStorage.getItem('vac-theme') === 'system' || !localStorage.getItem('vac-theme')) {
                var resolvedTheme = e.matches ? 'dark' : 'light';
                document.documentElement.setAttribute('data-theme', resolvedTheme);
                window.dispatchEvent(new CustomEvent('vac:theme-changed', { detail: { theme: resolvedTheme } }));
            }
        });
    }
function initShopMegaSlider() {
    var slider = document.getElementById('shop-mega-slider');
    if (!slider) return;

    var slides = Array.from(slider.querySelectorAll('.nav-mega__slide'));
    var dots = Array.from(slider.querySelectorAll('.nav-mega__slider-dots span'));
    if (slides.length <= 1) return;

    var currentIdx = 0;
    var interval;

    function showSlide(idx) {
        slides.forEach(function(s, i) {
            s.classList.toggle('active', i === idx);
        });
        dots.forEach(function(d, i) {
            d.classList.toggle('active', i === idx);
        });
        currentIdx = idx;
    }

    function startRotation() {
        stopRotation();
        interval = setInterval(function() {
            var next = (currentIdx + 1) % slides.length;
            showSlide(next);
        }, 5000);
    }

    function stopRotation() {
        if (interval) clearInterval(interval);
    }

    // Parent hover state in base.html logic handles visibility, 
    // but we start/stop timer based on mega menu hover for performance.
    slider.addEventListener('mouseenter', stopRotation);
    slider.addEventListener('mouseleave', startRotation);

    startRotation();
}

function initChatPanelBehavior() {
    var panel = document.getElementById('chat-panel');
    var fab = document.getElementById('chat-fab');
    var openChatLinks = document.querySelectorAll('[data-open-chat]');
    
    if (!panel) return;

    function closeChat() {
        panel.classList.remove('is-open');
    }

    // Close when clicking outside chat panel and not on trigger elements
    document.addEventListener('click', function (e) {
        if (panel.classList.contains('is-open')) {
            var clickedTrigger = false;
            if (fab && fab.contains(e.target)) clickedTrigger = true;
            openChatLinks.forEach(function (link) {
                if (link.contains(e.target)) clickedTrigger = true;
            });
            
            if (!panel.contains(e.target) && !clickedTrigger) {
                closeChat();
            }
        }
    });

    // Close on Escape key
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            closeChat();
        }
    });

    // Close when clicking other buttons/dropdowns in the navbar
    var navControls = document.querySelectorAll('.nav-link, .nav-icon-btn, .logo--brand, .dropdown-menu button');
    navControls.forEach(function (el) {
        var isOpenChatTrigger = el.hasAttribute('data-open-chat') || el.id === 'chat-fab';
        if (!isOpenChatTrigger) {
            el.addEventListener('click', function () {
                closeChat();
            });
        }
    });

    // Close cart drawer when chat panel triggers are clicked
    var chatTriggers = document.querySelectorAll('[data-open-chat], #chat-fab');
    chatTriggers.forEach(function (el) {
        el.addEventListener('click', function () {
            var cartDrawer = document.getElementById('cart-drawer');
            var cartOverlay = document.getElementById('cart-overlay');
            if (cartDrawer) {
                cartDrawer.classList.remove('is-open');
                cartDrawer.setAttribute('aria-hidden', 'true');
            }
            if (cartOverlay) {
                cartOverlay.classList.remove('is-open');
            }
            document.body.style.overflow = '';
        });
    });

    // Close chat when cart is opened
    var cartTriggers = document.querySelectorAll('[data-cart-open]');
    cartTriggers.forEach(function (el) {
        el.addEventListener('click', function () {
            closeChat();
        });
    });
}

function initAdminSearch() {
    var searchInput = document.getElementById('admin-search');
    if (!searchInput) return;

    searchInput.addEventListener('input', function () {
        var query = searchInput.value.trim().toLowerCase();
        
        var selectors = [
            { container: '.vac-admin-table', items: '.vac-admin-table__row' },
            { container: '.vac-admin-campaign-grid', items: '.vac-admin-campaign-teaser' },
            { container: '.vac-admin-feed', items: '.vac-admin-feed__item' },
            { container: '.vac-admin-queue-list', items: '.vac-admin-queue' },
            { container: '.admin-table tbody', items: 'tr' }
        ];

        selectors.forEach(function (sel) {
            var containers = Array.from(document.querySelectorAll(sel.container));
            containers.forEach(function (container) {
                var items = Array.from(container.querySelectorAll(sel.items));
                
                items = items.filter(function (item) {
                    return !item.classList.contains('vac-search-empty-state');
                });

                if (items.length === 0) return;

                var visibleCount = 0;
                items.forEach(function (item) {
                    var text = item.textContent.toLowerCase();
                    if (query === '' || text.indexOf(query) !== -1) {
                        item.style.display = '';
                        visibleCount++;
                    } else {
                        item.style.display = 'none';
                    }
                });

                var emptyState = container.querySelector('.vac-search-empty-state');
                if (visibleCount === 0) {
                    if (!emptyState) {
                        if (container.tagName === 'TBODY') {
                            var colSpan = 8;
                            var table = container.closest('table');
                            if (table) {
                                var ths = table.querySelectorAll('thead th');
                                if (ths.length > 0) colSpan = ths.length;
                            }
                            emptyState = document.createElement('tr');
                            emptyState.className = 'vac-search-empty-state';
                            var td = document.createElement('td');
                            td.colSpan = colSpan;
                            td.style.textAlign = 'center';
                            td.style.padding = '2rem';
                            td.style.color = 'var(--vac-muted)';
                            td.style.fontSize = '0.9rem';
                            td.textContent = 'No results found';
                            emptyState.appendChild(td);
                            container.appendChild(emptyState);
                        } else {
                            emptyState = document.createElement('div');
                            emptyState.className = 'vac-search-empty-state vac-admin-empty-state';
                            emptyState.style.padding = '2rem';
                            emptyState.style.textAlign = 'center';
                            emptyState.style.color = 'var(--vac-muted)';
                            emptyState.style.border = '1px dashed var(--vac-line)';
                            emptyState.style.borderRadius = 'var(--vac-radius-sm)';
                            emptyState.style.fontSize = '0.9rem';
                            emptyState.textContent = 'No results found';
                            container.appendChild(emptyState);
                        }
                    }
                    emptyState.style.display = '';
                } else {
                    if (emptyState) {
                        emptyState.style.display = 'none';
                    }
                }
            });
        });
    });

    searchInput.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            searchInput.value = '';
            searchInput.dispatchEvent(new Event('input'));
            searchInput.blur();
        }
    });
}

document.addEventListener('DOMContentLoaded', function () {
    initNavScroll();
    initNavMegaMenu();
    initShopMegaSlider();
    initThemeSystem();
    initChatPanelBehavior();
    markRevealTargets();
    initReveal();
    initHeroParallax();
    initBackToTop();
    initClickableCards();
    initMagneticMedia();
    initLiveCharts();
    initCountUp();
    initCarousels();
    initHeroRotation();
    initCinematicHeroSlides();
    initCinematicParallax();
    initKpiSparklines();
    initAdminSearch();
});
})();
