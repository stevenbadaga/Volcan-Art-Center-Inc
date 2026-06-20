/**
 * Volcano Arts Center - Premium Admin Charts
 * Theme-aware analytics renderer for admin dashboards.
 */
(function () {
    'use strict';

    if (typeof Chart === 'undefined') return;

    var chartRefs = [];
    var liveCharts = [];
    var liveStampInterval = null;
    var liveDataInterval = null;
    var lastUpdatedAt = Date.now();

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
    }

    function palette() {
        // Since charts are always inside dark card panels (#101412 / #111613),
        // we must use high-contrast light colors for labels and grids in all modes.
        if (currentTheme() === 'light') {
            return {
                font: "'DM Sans', sans-serif",
                text: '#a4bfae', // Readable light green-grey
                grid: 'rgba(255, 255, 255, 0.08)',
                accent: '#00A651', // Strong green brand accent
                accentSoft: '#32CF7A',
                accentDeep: '#0A5C33',
                lime: '#9DDB3D',
                gold: '#cba86a', // Gold accent
                slate: '#101412',
                tooltipBg: 'rgba(12, 30, 22, 0.96)',
                tooltipText: '#ffffff'
            };
        }

        return {
            font: "'DM Sans', sans-serif",
            text: '#D6E6DB', // Very light green-grey
            grid: 'rgba(255, 255, 255, 0.08)',
            accent: '#32CF7A', // Brighter green for dark mode
            accentSoft: '#B8FF57',
            accentDeep: '#0EA85B',
            lime: '#E7FFAE',
            gold: '#E3C37D',
            slate: '#0d120f',
            tooltipBg: 'rgba(7, 12, 9, 0.96)',
            tooltipText: '#F4FBF6'
        };
    }

    function num(el, attr, fallback) {
        if (!el) return fallback;
        var v = parseFloat(el.getAttribute(attr));
        return isFinite(v) ? v : fallback;
    }

    function setChartDefaults(theme) {
        Chart.defaults.font.family = theme.font;
        Chart.defaults.color = theme.text;
        Chart.defaults.borderColor = theme.grid;
        Chart.defaults.plugins.tooltip.backgroundColor = theme.tooltipBg;
        Chart.defaults.plugins.tooltip.titleColor = theme.tooltipText;
        Chart.defaults.plugins.tooltip.bodyColor = theme.tooltipText;
        Chart.defaults.plugins.tooltip.padding = 12;
        Chart.defaults.plugins.tooltip.cornerRadius = 12;
        Chart.defaults.plugins.tooltip.displayColors = false;
        Chart.defaults.plugins.tooltip.titleFont = { family: "'Syne', sans-serif", size: 13, weight: '800' };
        Chart.defaults.plugins.tooltip.bodyFont = { family: theme.font, size: 12 };
    }

    function lineOptions(theme, prefix, suffix) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { color: theme.text, font: { weight: '600' } }
                },
                y: {
                    beginAtZero: true,
                    grid: { color: theme.grid },
                    ticks: {
                        color: theme.text,
                        callback: function (value) {
                            return (prefix || '') + value + (suffix || '');
                        }
                    }
                }
            },
            animation: { duration: 1400, easing: 'easeOutQuart' }
        };
    }

    function doughnutOptions(theme) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '72%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        color: theme.text,
                        usePointStyle: true,
                        padding: 18,
                        font: { family: theme.font, weight: '700' }
                    }
                }
            },
            animation: { animateScale: true, animateRotate: true, duration: 1500 }
        };
    }

    function destroyCharts() {
        chartRefs.forEach(function (chart) {
            try {
                chart.destroy();
            } catch (_) { /* ignore */ }
        });
        chartRefs = [];
        liveCharts = [];

        if (liveStampInterval) {
            clearInterval(liveStampInterval);
            liveStampInterval = null;
        }
        if (liveDataInterval) {
            clearInterval(liveDataInterval);
            liveDataInterval = null;
        }
    }

    function createRevenueChart(theme) {
        var canvas = document.getElementById('revenueChart');
        if (!canvas) return;

        var chart = new Chart(canvas, {
            type: 'line',
            data: {
                labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
                datasets: [{
                    label: 'Revenue ($k)',
                    data: [12.5, 15.2, 14.8, 18.9, 22.1, 24.5, 23.0, 26.8, 31.0, 29.5, 34.2, 38.5],
                    borderColor: theme.accent,
                    backgroundColor: function (ctx) {
                        var area = ctx.chart.chartArea;
                        if (!area) return 'rgba(0, 166, 81, 0.18)';
                        var gradient = ctx.chart.ctx.createLinearGradient(0, area.top, 0, area.bottom);
                        gradient.addColorStop(0, currentTheme() === 'light' ? 'rgba(0, 166, 81, 0.28)' : 'rgba(184, 255, 87, 0.36)');
                        gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
                        return gradient;
                    },
                    borderWidth: 3,
                    tension: 0.42,
                    fill: true,
                    pointBackgroundColor: currentTheme() === 'light' ? '#ffffff' : '#07100b',
                    pointBorderColor: theme.accent,
                    pointBorderWidth: 2,
                    pointRadius: 0,
                    pointHoverRadius: 6
                }]
            },
            options: lineOptions(theme, '$', 'k')
        });

        chartRefs.push(chart);
        liveCharts.push(chart);
    }

    function createDepartmentChart(theme) {
        var canvas = document.getElementById('departmentChart');
        if (!canvas) return;

        var orders = num(canvas, 'data-orders', 0);
        var bookings = num(canvas, 'data-bookings', 0);
        var donations = num(canvas, 'data-donations', 0);
        if (orders + bookings + donations === 0) {
            orders = 8;
            bookings = 5;
            donations = 3;
        }

        var chart = new Chart(canvas, {
            type: 'doughnut',
            data: {
                labels: ['Art Orders', 'Tour Bookings', 'Donations'],
                datasets: [{
                    data: [orders, bookings, donations],
                    backgroundColor: [theme.gold, theme.accent, theme.accentDeep],
                    borderWidth: 0,
                    hoverOffset: 8
                }]
            },
            options: doughnutOptions(theme)
        });

        chartRefs.push(chart);
    }

    function createFulfillmentChart(theme) {
        var canvas = document.getElementById('fulfillmentChart');
        if (!canvas) return;

        var pending = num(canvas, 'data-pending', 0);
        var processing = num(canvas, 'data-processing', 0);
        var shipped = num(canvas, 'data-shipped', 0);
        var delivered = num(canvas, 'data-delivered', 0);
        if (pending + processing + shipped + delivered === 0) {
            pending = 3;
            processing = 5;
            shipped = 4;
            delivered = 6;
        }

        var chart = new Chart(canvas, {
            type: 'doughnut',
            data: {
                labels: ['Pending', 'Processing', 'Shipped', 'Delivered'],
                datasets: [{
                    data: [pending, processing, shipped, delivered],
                    backgroundColor: [theme.gold, theme.accentSoft, theme.accent, theme.accentDeep],
                    borderWidth: 0,
                    hoverOffset: 8
                }]
            },
            options: doughnutOptions(theme)
        });

        chartRefs.push(chart);
    }

    function createContentEngagementChart(theme) {
        var canvas = document.getElementById('contentEngagementChart');
        if (!canvas) return;

        var products = num(canvas, 'data-products', 0);
        var blog = num(canvas, 'data-blog', 0);
        var experiences = num(canvas, 'data-experiences', 0);
        var media = num(canvas, 'data-media', 0);

        var chart = new Chart(canvas, {
            type: 'bar',
            data: {
                labels: ['Artworks', 'Stories', 'Experiences', 'Media'],
                datasets: [
                    {
                        type: 'bar',
                        label: 'Published items',
                        data: [products, blog, experiences, media],
                        backgroundColor: currentTheme() === 'light' ? theme.accent : theme.accentSoft,
                        borderRadius: 10,
                        order: 2,
                        barThickness: 34
                    },
                    {
                        type: 'line',
                        label: 'Views (k)',
                        data: [5.0, 7.5, 6.8, 9.2],
                        borderColor: theme.gold,
                        borderWidth: 3,
                        tension: 0.4,
                        order: 1,
                        yAxisID: 'y1',
                        pointBackgroundColor: currentTheme() === 'light' ? '#ffffff' : '#07100b',
                        pointBorderColor: theme.gold,
                        pointBorderWidth: 2,
                        pointRadius: 3
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'top',
                        labels: {
                            color: theme.text,
                            usePointStyle: true,
                            padding: 16,
                            font: { family: theme.font, weight: '700' }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { color: theme.text, font: { weight: '600' } }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: theme.grid },
                        ticks: { color: theme.text }
                    },
                    y1: {
                        position: 'right',
                        beginAtZero: true,
                        grid: { display: false },
                        ticks: {
                            color: theme.text,
                            callback: function (value) { return value + 'k'; }
                        }
                    }
                }
            }
        });

        chartRefs.push(chart);
        liveCharts.push(chart);
    }

    function updateLiveStamps() {
        var stamps = document.querySelectorAll('[data-live-updated]');
        var seconds = Math.round((Date.now() - lastUpdatedAt) / 1000);
        var label = seconds < 5 ? 'Updated just now' : 'Updated ' + seconds + 's ago';
        stamps.forEach(function (stamp) {
            stamp.textContent = label;
        });
    }

    function startLiveMode() {
        updateLiveStamps();
        liveStampInterval = setInterval(updateLiveStamps, 1000);

        if (!liveCharts.length) return;
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

        liveDataInterval = setInterval(function () {
            liveCharts.forEach(function (chart) {
                var dataset = chart.data.datasets[0];
                if (!dataset || !dataset.data || !dataset.data.length) return;
                var index = dataset.data.length - 1;
                var value = dataset.data[index];
                var delta = (Math.random() - 0.42) * Math.max(0.6, Math.abs(value) * 0.03);
                dataset.data[index] = Math.max(0, +(value + delta).toFixed(2));
                chart.update('none');
            });

            lastUpdatedAt = Date.now();
            updateLiveStamps();
        }, 9000);
    }

    function init() {
        destroyCharts();
        lastUpdatedAt = Date.now();

        var theme = palette();
        setChartDefaults(theme);

        createRevenueChart(theme);
        createDepartmentChart(theme);
        createFulfillmentChart(theme);
        createContentEngagementChart(theme);
        startLiveMode();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    window.addEventListener('vac:theme-changed', init);
})();
