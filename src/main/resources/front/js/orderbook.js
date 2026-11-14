(function ($) {
    const API = {
        book: (depth) => `/api/book?depth=${depth}`,
        order: '/api/order',
        orderStatus: (id) => `/api/order/status?id=${id}`,
        cancel: '/api/cancel',
        replace: '/api/replace',
        seed: '/api/seed',
        trades: '/api/trades'
    };

    const STATUS_FA = {
        NEW: 'جدید',
        PARTIALLY_FILLED: 'پر شده جزئی',
        FILLED: 'کاملاً پر شده',
        CANCELLED: 'لغو شده'
    };

    const DEPTH = 12;
    let timer = null;

    function fmt(n) {
        if (n === null || n === undefined) return '—';
        return Number(n).toLocaleString('en-US');
    }

    function statusLabel(code) {
        return STATUS_FA[code] || code;
    }

    function statusBadgeClass(code) {
        return {
            NEW: 'ob-badge-new',
            PARTIALLY_FILLED: 'ob-badge-partial',
            FILLED: 'ob-badge-filled',
            CANCELLED: 'ob-badge-cancelled'
        }[code] || 'ob-badge-new';
    }

    function badgeHtml(code) {
        return `<span class="ob-badge ${statusBadgeClass(code)}">${statusLabel(code)}</span>`;
    }

    function showMsg(text, isError) {
        const $msg = $('#msg');
        $msg.text(text).toggleClass('text-danger', !!isError).toggleClass('text-info', !isError).show();
        setTimeout(() => $msg.fadeOut(), 4000);
    }

    function showLastResult(title, html) {
        $('#last-result-card').show();
        $('#last-result-body').html(`<strong>${title}</strong><br>${html}`);
    }

    function renderOrderDetail(o) {
        if (!o || !o.found) {
            $('#order-detail').html('<span class="text-danger">سفارشی با این شناسه یافت نشد.</span>');
            return;
        }
        const sideFa = o.side === 'BUY' ? 'خرید' : 'فروش';
        const openFa = o.open ? 'بله — روی book' : 'خیر';
        $('#order-detail').html(`
            <div class="d-flex justify-content-between align-items-center mb-2">
                <span>سفارش #${o.id}</span>
                ${badgeHtml(o.status)}
            </div>
            <dl class="ob-dl row mb-0">
                <dt class="col-5">طرف</dt><dd class="col-7" dir="ltr">${sideFa} (${o.side})</dd>
                <dt class="col-5">قیمت</dt><dd class="col-7" dir="ltr">${fmt(o.price)}</dd>
                <dt class="col-5">حجم کل</dt><dd class="col-7" dir="ltr">${fmt(o.quantity)}</dd>
                <dt class="col-5">پر شده</dt><dd class="col-7" dir="ltr">${fmt(o.filledQuantity)}</dd>
                <dt class="col-5">باقی‌مانده</dt><dd class="col-7" dir="ltr">${fmt(o.remainingQuantity)}</dd>
                <dt class="col-5">باز روی book</dt><dd class="col-7">${openFa}</dd>
            </dl>
        `);
    }

    function lookupOrder(id, silent) {
        return $.getJSON(API.orderStatus(id))
            .done((data) => {
                renderOrderDetail(data);
                if (!silent) showMsg('وضعیت سفارش ' + id + ' بارگذاری شد');
            })
            .fail(() => {
                renderOrderDetail({ found: false });
                if (!silent) showMsg('خطا در جستجو', true);
            });
    }

    function formatSubmitResult(res) {
        const lines = [
            `شناسه: ${res.id}`,
            `وضعیت: ${badgeHtml(res.status)}`,
            `پر شده: ${fmt(res.filled)} | باقی: ${fmt(res.remaining)}`,
            res.resting ? 'روی book است' : 'روی book نیست'
        ];
        if (res.trades && res.trades.length) {
            lines.push(`تعداد معامله: ${res.trades.length}`);
        }
        return lines.join('<br>');
    }

    function maxQty(levels) {
        let m = 1;
        levels.forEach((l) => { m = Math.max(m, l.quantity); });
        return m;
    }

    function renderLadder(data) {
        const bidMax = maxQty(data.bids);
        const askMax = maxQty(data.asks);
        const rows = Math.max(data.bids.length, data.asks.length, 1);
        const $ladder = $('#depth-ladder').empty();

        for (let i = 0; i < rows; i++) {
            const bid = data.bids[i];
            const ask = data.asks[i];
            const $row = $('<div class="ob-row"></div>');

            const $bidSide = $('<div class="ob-side bid"></div>');
            if (bid) {
                const w = (bid.quantity / bidMax) * 100;
                $bidSide.append($('<div class="ob-bar bid"></div>').css('width', w + '%'));
                $bidSide.append(
                    $('<span></span>').text(fmt(bid.quantity)),
                    $('<span class="ob-meta"></span>').text(`(${bid.orderCount})`)
                );
            }
            $row.append($bidSide);

            let priceLabel = '—';
            if (bid && ask) {
                priceLabel = fmt(bid.price) + ' │ ' + fmt(ask.price);
            } else if (bid) {
                priceLabel = fmt(bid.price);
            } else if (ask) {
                priceLabel = fmt(ask.price);
            }
            $row.append($('<div class="ob-price-col"></div>').text(priceLabel));

            const $askSide = $('<div class="ob-side ask"></div>');
            if (ask) {
                const w = (ask.quantity / askMax) * 100;
                $askSide.append($('<div class="ob-bar ask"></div>').css('width', w + '%'));
                $askSide.append(
                    $('<span></span>').text(fmt(ask.quantity)),
                    $('<span class="ob-meta"></span>').text(`(${ask.orderCount})`)
                );
            }
            $row.append($askSide);

            $ladder.append($row);
        }
    }

    function renderSummary(data) {
        $('#stat-bid').text(fmt(data.bestBid));
        $('#stat-ask').text(fmt(data.bestAsk));
        $('#stat-mid').text(data.mid != null ? fmt(data.mid) : '—');
        $('#stat-spread').text(fmt(data.spread));
        $('#stat-open').text(fmt(data.openOrders));
    }

    function renderTrades(trades) {
        const $list = $('#trade-list').empty();
        if (!trades || trades.length === 0) {
            $list.append('<li class="list-group-item text-secondary">معامله‌ای نیست</li>');
            return;
        }
        trades.slice().reverse().forEach((t) => {
            const line = `#${t.tradeId}  ${t.quantity} @ ${t.price}  (maker ${t.makerOrderId} ← taker ${t.takerOrderId})`;
            $list.append($('<li class="list-group-item"></li>').text(line));
        });
    }

    function loadTrades() {
        $.getJSON(API.trades).done(renderTrades);
    }

    function refreshBook() {
        return $.getJSON(API.book(DEPTH))
            .done((data) => {
                renderSummary(data);
                renderLadder(data);
                $('#status-text').text('متصل — ' + new Date().toLocaleTimeString('fa-IR'));
            })
            .fail(() => {
                $('#status-text').text('خطا در اتصال به API (سرور را اجرا کنید)');
            });
    }

    function scheduleAuto() {
        if (timer) clearInterval(timer);
        if ($('#auto-refresh').is(':checked')) {
            timer = setInterval(refreshBook, 1500);
        }
    }

    $('#btn-refresh').on('click', refreshBook);
    $('#auto-refresh').on('change', scheduleAuto);

    $('#btn-seed').on('click', function () {
        $.post(API.seed)
            .done(() => {
                refreshBook();
                loadTrades();
                $('#last-result-card').hide();
                showMsg('دادهٔ نمونه بارگذاری شد');
            })
            .fail(() => showMsg('خطا در seed', true));
    });

    $('#btn-trades').on('click', loadTrades);

    $('#lookup-form').on('submit', function (e) {
        e.preventDefault();
        lookupOrder(Number($('#lookup-id').val()));
    });

    $('#order-form').on('submit', function (e) {
        e.preventDefault();
        const payload = {
            side: $('#side').val(),
            price: Number($('#price').val()),
            quantity: Number($('#quantity').val())
        };
        const customId = $('#order-id').val();
        if (customId) payload.id = Number(customId);

        $.ajax({
            url: API.order,
            method: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            data: JSON.stringify(payload)
        })
            .done((res) => {
                showLastResult('ثبت سفارش', formatSubmitResult(res));
                showMsg('ثبت شد — ' + statusLabel(res.status));
                $('#order-id').val('');
                $('#lookup-id').val(res.id);
                lookupOrder(res.id, true);
                refreshBook();
                loadTrades();
            })
            .fail((xhr) => showMsg(xhr.responseText || 'خطا در ثبت', true));
    });

    $('#replace-form').on('submit', function (e) {
        e.preventDefault();
        const payload = {
            openOrderId: Number($('#replace-open-id').val()),
            price: Number($('#replace-price').val()),
            quantity: Number($('#replace-qty').val())
        };
        const newId = $('#replace-new-id').val();
        if (newId) payload.newOrderId = Number(newId);

        $.ajax({
            url: API.replace,
            method: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            data: JSON.stringify(payload)
        })
            .done((res) => {
                const html = [
                    `قدیمی #${res.openOrderId} → لغو`,
                    `جدید #${res.newOrderId}: ${badgeHtml(res.status)}`,
                    `پر شده: ${fmt(res.filled)}`
                ].join('<br>');
                showLastResult('Replace', html);
                $('#lookup-id').val(res.newOrderId);
                lookupOrder(res.openOrderId, true);
                lookupOrder(res.newOrderId, true);
                refreshBook();
                loadTrades();
                showMsg('جایگزینی انجام شد');
            })
            .fail((xhr) => showMsg(xhr.responseText || 'خطا در replace', true));
    });

    $('#cancel-form').on('submit', function (e) {
        e.preventDefault();
        const id = Number($('#cancel-id').val());
        $.ajax({
            url: API.cancel,
            method: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            data: JSON.stringify({ id })
        })
            .done((res) => {
                if (res.cancelled) {
                    showLastResult('لغو', `سفارش #${id}: ${badgeHtml(res.status)}`);
                    $('#lookup-id').val(id);
                    lookupOrder(id, true);
                }
                showMsg(res.cancelled ? 'لغو شد — ' + statusLabel(res.status) : 'سفارش باز یافت نشد', !res.cancelled);
                refreshBook();
            })
            .fail(() => showMsg('خطا در لغو', true));
    });

    refreshBook();
    loadTrades();
    scheduleAuto();
})(jQuery);
