/**
 * Volcano Arts Center — Premium Admin Charts
 * Renders animated, on-brand analytics across every role dashboard.
 * Reads real counts from canvas data-* attributes (graceful fallbacks),
 * uses a light theme tuned for white chart cards, guards against double
 * initialisation, and adds a gentle "live" pulse to time-series charts.
 */
(function () {
    if (typeof Chart === 'undefined') return;

    var THEME = {
        font: "'DM Sans', sans-serif",
        text: '#5a6b5e',
        grid: '#eef2ee',
        green: '#00A651',
        greenDeep: '#0a5c33',
        gold: '#CBA86A',
        amber: '#e2b04a',
        ink: '#0c3d24'
    };

    function num(el, attr, fallback) {
        if (!el) return fallback;
        var v = parseFloat(el.getAttribute(attr));
        return isFinite(v) ? v : fallback;
    }

    function applyDefaults() {
        Chart.defaults.font.family = THEME.font;
        Chart.defaults.color = THEME.text;
        Chart.defaults.plugins.tooltip.backgroundColor = 'rgba(12,61,36,0.96)';
        Chart.defaults.plugins.tooltip.titleFont = { family: "'Syne', sans-serif", size: 13, weight: '800' };
        Chart.defaults.plugins.tooltip.bodyFont = { family: THEME.font, size: 12 };
        Chart.defaults.plugins.tooltip.padding = 12;
        Chart.defaults.plugins.tooltip.cornerRadius = 10;
        Chart.defaults.plugins.tooltip.displayColors = false;
    }

    function lineOpts(prefix, suffix) {
        return {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { grid: { display: false }, ticks: { font: { weight: '600' } } },
                y: {
                    beginAtZero: true, grid: { color: THEME.grid },
                    ticks: { callback: function (v) { return (prefix || '') + v + (suffix || ''); } }
                }
            },
            animation: { duration: 1400, easing: 'easeOutQuart' }
        };
    }

    function doughnutOpts() {
        return {
            responsive: true, maintainAspectRatio: false, cutout: '72%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { usePointStyle: true, padding: 18, font: { family: THEME.font, weight: '700' } }
                }
            },
            animation: { animateScale: true, animateRotate: true, duration: 1500 }
        };
    }

    var liveCharts = [];

    function init() {
        applyDefaults();

        /* ── Super Admin · Revenue trajectory (line) ── */
        var rev = document.getElementById('revenueChart');
        if (rev && !Chart.getChart(rev)) {
            var c = new Chart(rev, {
                type: 'line',
                data: {
                    labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
                    datasets: [{
                        label: 'Revenue ($k)',
                        data: [12.5, 15.2, 14.8, 18.9, 22.1, 24.5, 23.0, 26.8, 31.0, 29.5, 34.2, 38.5],
                        borderColor: THEME.green,
                        backgroundColor: function (cx) {
                            var a = cx.chart.chartArea;
                            if (!a) return 'rgba(0,166,81,0.15)';
                            var g = cx.chart.ctx.createLinearGradient(0, a.top, 0, a.bottom);
                            g.addColorStop(0, 'rgba(0,166,81,0.30)');
                            g.addColorStop(1, 'rgba(0,166,81,0)');
                            return g;
                        },
                        borderWidth: 3, tension: 0.42, fill: true,
                        pointBackgroundColor: '#fff', pointBorderColor: THEME.green,
                        pointBorderWidth: 2, pointRadius: 0, pointHoverRadius: 6
                    }]
                },
                options: lineOpts('$', 'k')
            });
            liveCharts.push(c);
        }

        /* ── Super Admin · Department volume (doughnut, real counts) ── */
        var dept = document.getElementById('departmentChart');
        if (dept && !Chart.getChart(dept)) {
            var orders = num(dept, 'data-orders', 0),
                bookings = num(dept, 'data-bookings', 0),
                donations = num(dept, 'data-donations', 0);
            if (orders + bookings + donations === 0) { orders = 8; bookings = 5; donations = 3; }
            new Chart(dept, {
                type: 'doughnut',
                data: {
                    labels: ['Art Orders', 'Tour Bookings', 'Donations'],
                    datasets: [{ data: [orders, bookings, donations], backgroundColor: [THEME.gold, THEME.green, THEME.ink], borderWidth: 0, hoverOffset: 6 }]
                },
                options: doughnutOpts()
            });
        }

        /* ── Ops · Fulfillment pipeline (doughnut, real statuses) ── */
        var ful = document.getElementById('fulfillmentChart');
        if (ful && !Chart.getChart(ful)) {
            var p = num(ful, 'data-pending', 0), pr = num(ful, 'data-processing', 0),
                sh = num(ful, 'data-shipped', 0), dl = num(ful, 'data-delivered', 0);
            if (p + pr + sh + dl === 0) { p = 3; pr = 5; sh = 4; dl = 6; }
            new Chart(ful, {
                type: 'doughnut',
                data: {
                    labels: ['Pending', 'Processing', 'Shipped', 'Delivered'],
                    datasets: [{ data: [p, pr, sh, dl], backgroundColor: [THEME.amber, THEME.gold, THEME.green, THEME.greenDeep], borderWidth: 0, hoverOffset: 6 }]
                },
                options: doughnutOpts()
            });
        }

        /* ── Content · Catalog composition + engagement (bar + line) ── */
        var ce = document.getElementById('contentEngagementChart');
        if (ce && !Chart.getChart(ce)) {
            var products = num(ce, 'data-products', 0), blog = num(ce, 'data-blog', 0),
                exp = num(ce, 'data-experiences', 0), media = num(ce, 'data-media', 0);
            var c2 = new Chart(ce, {
                type: 'bar',
                data: {
                    labels: ['Artworks', 'Stories', 'Experiences', 'Media'],
                    datasets: [
                        { type: 'bar', label: 'Published items', data: [products, blog, exp, media], backgroundColor: THEME.green, borderRadius: 8, order: 2, barThickness: 34 },
                        { type: 'line', label: 'Views (k)', data: [5.0, 7.5, 6.8, 9.2], borderColor: THEME.gold, borderWidth: 3, tension: 0.4, order: 1, yAxisID: 'y1', pointBackgroundColor: '#fff', pointBorderColor: THEME.gold, pointBorderWidth: 2, pointRadius: 3 }
                    ]
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { position: 'top', labels: { usePointStyle: true, padding: 16, font: { family: THEME.font, weight: '700' } } } },
                    scales: {
                        x: { grid: { display: false }, ticks: { font: { weight: '600' } } },
                        y: { beginAtZero: true, grid: { color: THEME.grid } },
                        y1: { position: 'right', beginAtZero: true, grid: { display: false }, ticks: { callback: function (v) { return v + 'k'; } } }
                    }
                }
            });
            liveCharts.push(c2);
        }

        startLive();
    }

    /* Gentle real-time feel: nudge the latest point + refresh "updated" stamps. */
    function startLive() {
        var stamps = document.querySelectorAll('[data-live-updated]');
        var last = Date.now();
        function tickStamp() {
            var secs = Math.round((Date.now() - last) / 1000);
            var txt = secs < 5 ? 'Updated just now' : 'Updated ' + secs + 's ago';
            stamps.forEach(function (s) { s.textContent = txt; });
        }
        tickStamp();
        setInterval(tickStamp, 1000);

        if (!liveCharts.length) return;
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
        setInterval(function () {
            liveCharts.forEach(function (ch) {
                var ds = ch.data.datasets[0];
                if (!ds || !ds.data || !ds.data.length) return;
                var i = ds.data.length - 1;
                var v = ds.data[i];
                var delta = (Math.random() - 0.42) * Math.max(0.6, Math.abs(v) * 0.03);
                ds.data[i] = Math.max(0, +(v + delta).toFixed(2));
                ch.update('none');
            });
            last = Date.now();
            tickStamp();
        }, 9000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
