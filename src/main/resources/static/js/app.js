// InhaWorks 공통 스크립트 — app.js
//  1) 확인 모달 (window.confirm 대신) : <button data-confirm="제목" data-confirm-body="설명" data-confirm-ok="삭제"
//                                       data-danger data-reason="reason" data-reason-label="사유">
//     - 버튼 또는 <form> 에 붙인다. data-reason 이 있으면 사유 입력칸이 나오고, 채워야 확인 버튼이 켜진다
//       (입력한 사유는 form 의 input[name=reason] 에 넣어서 보낸다)
//  2) 토스트 : 서버 플래시 메시지(<template data-toast>)와 data-async 폼 결과를 오른쪽 아래에
//  3) ⋯ 메뉴 : [data-menu-trigger] → 다음 형제 .menu 열기 (트리거 쪽에서 커지며 나타남)
//  4) 서명 패드, 사진 미리보기, 반복 주기 칸, 출퇴근 시계, 두 번 누르기 방지(data-once)
//  5) 폼 빌더 : [data-builder] 안의 '+ 항목 추가' → 종류 메뉴 → 항목 카드 추가 / 지우기 / 순서 번호
//  6) 목록-상세 (결재함, 오늘 할 일) : [data-md] 안의 [data-md-item] 을 누르면 상세 화면의 [data-md-content] 를 옆에 불러온다
//  7) 할 일 목록 세그먼트 필터 : [data-filter-tabs] + 행의 data-state
//  8) 긴 목록 표 : 좁은 화면(또는 표가 다 안 들어갈 때) 카드로 바꾸기(.cards + data-label), 표 안 ⋯ 메뉴는 화면 기준(fixed)으로 띄우기
(function () {
    'use strict';

    var $ = function (sel, root) { return (root || document).querySelector(sel); };
    var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

    // ---------- 2. 토스트 ----------
    var toastBox;
    // undoUrl 이 있으면 '실행 취소' 버튼 (레이아웃의 #undo-form 으로 POST, CSRF 토큰 포함)
    function toast(message, kind, undoUrl) {
        if (!message) return;
        if (!toastBox) {
            toastBox = document.createElement('div');
            toastBox.className = 'toasts';
            toastBox.setAttribute('role', 'status');
            toastBox.setAttribute('aria-live', 'polite');
            document.body.appendChild(toastBox);
        }
        var t = document.createElement('div');
        t.className = 'toast' + (kind === 'error' ? ' error' : '');
        var span = document.createElement('span');
        span.textContent = message;
        t.appendChild(span);
        var undoForm = document.getElementById('undo-form');
        if (undoUrl && undoForm) {
            var b = document.createElement('button');
            b.type = 'button';
            b.textContent = '실행 취소';
            b.addEventListener('click', function () {
                b.disabled = true;
                undoForm.action = undoUrl;
                undoForm.submit();
            });
            t.appendChild(b);
        }
        toastBox.appendChild(t);
        requestAnimationFrame(function () { t.classList.add('show'); });
        setTimeout(function () {
            t.classList.remove('show');
            setTimeout(function () { t.remove(); }, 250);
        }, kind === 'error' ? 6000 : (undoUrl ? 9000 : 3800));
    }
    window.iwToast = toast;

    function flushToasts() {
        $$('template[data-toast]').forEach(function (tpl) {
            toast(tpl.content.textContent.trim(), tpl.dataset.toast, tpl.dataset.undo);
            tpl.remove();
        });
        try {
            var saved = sessionStorage.getItem('iw-toast');
            if (saved) {
                sessionStorage.removeItem('iw-toast');
                var s = JSON.parse(saved);
                toast(s.message, null, s.undo);
            }
        } catch (e) { /* 저장소를 못 쓰면 무시 */ }
    }

    // ---------- 1. 확인 모달 ----------
    var modal;
    function confirmModal(opts) {
        return new Promise(function (resolve) {
            if (modal) modal.remove();
            modal = document.createElement('div');
            modal.className = 'scrim';
            modal.innerHTML =
                '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="iw-modal-title">' +
                '<h3 id="iw-modal-title"></h3><p></p>' +
                '<label class="sr-only" for="iw-modal-reason"></label>' +
                '<textarea id="iw-modal-reason" class="textarea" maxlength="500" hidden></textarea>' +
                '<div class="modal-actions"><button type="button" class="btn" data-cancel>취소</button>' +
                '<button type="button" class="btn" data-ok></button></div></div>';
            $('h3', modal).textContent = opts.title || '계속할까요?';
            var p = $('p', modal);
            if (opts.body) p.textContent = opts.body; else p.remove();
            var reason = $('textarea', modal);
            var ok = $('[data-ok]', modal);
            ok.textContent = opts.ok || '확인';
            ok.classList.add(opts.danger ? 'danger' : 'primary');
            if (opts.danger) { ok.classList.remove('primary'); ok.classList.add('danger'); }
            if (opts.reason) {
                reason.hidden = false;
                reason.placeholder = opts.reasonLabel || '사유를 입력해 주세요 (필수)';
                $('label', modal).textContent = opts.reasonLabel || '사유';
                ok.disabled = true;
                reason.addEventListener('input', function () { ok.disabled = reason.value.trim() === ''; });
            }
            var last = document.activeElement;
            function close(value) {
                modal.classList.remove('open');
                document.removeEventListener('keydown', onKey);
                var m = modal;
                setTimeout(function () { m.remove(); }, 200);
                if (last && last.focus) last.focus();
                resolve(value);
            }
            function onKey(e) {
                if (e.key === 'Escape') close(null);
                if (e.key === 'Tab') {   // 모달 안에서만 포커스 이동
                    var f = $$('button:not([disabled]), textarea:not([hidden])', modal);
                    if (!f.length) return;
                    if (e.shiftKey && document.activeElement === f[0]) { e.preventDefault(); f[f.length - 1].focus(); }
                    else if (!e.shiftKey && document.activeElement === f[f.length - 1]) { e.preventDefault(); f[0].focus(); }
                }
            }
            $('[data-cancel]', modal).addEventListener('click', function () { close(null); });
            modal.addEventListener('click', function (e) { if (e.target === modal) close(null); });
            ok.addEventListener('click', function () { close({reason: reason.value.trim()}); });
            document.addEventListener('keydown', onKey);
            document.body.appendChild(modal);
            requestAnimationFrame(function () { modal.classList.add('open'); });
            (opts.reason ? reason : ok).focus();
        });
    }
    window.iwConfirm = confirmModal;

    document.addEventListener('submit', function (e) {
        var form = e.target;
        var submitter = e.submitter;
        var holder = (submitter && submitter.dataset.confirm !== undefined) ? submitter
            : (form.dataset.confirm !== undefined ? form : null);
        if (holder && !form._iwConfirmed) {
            e.preventDefault();
            confirmModal({
                title: holder.dataset.confirm,
                body: holder.dataset.confirmBody,
                ok: holder.dataset.confirmOk,
                danger: holder.dataset.danger !== undefined,
                reason: holder.dataset.reason,
                reasonLabel: holder.dataset.reasonLabel
            }).then(function (result) {
                if (!result) return;
                if (holder.dataset.reason) {
                    var input = form.querySelector('[name="' + holder.dataset.reason + '"]');
                    if (!input) {
                        input = document.createElement('input');
                        input.type = 'hidden';
                        input.name = holder.dataset.reason;
                        form.appendChild(input);
                    }
                    input.value = result.reason;
                }
                form._iwConfirmed = true;
                if (form.requestSubmit) form.requestSubmit(submitter || undefined); else form.submit();
                form._iwConfirmed = false;
            });
            return;
        }
        // 비동기 폼 : 제자리에서 처리하고 토스트 → 새로고침 (예: 대시보드의 검수 통과)
        if (form.dataset.async !== undefined && window.fetch) {
            e.preventDefault();
            var data = new FormData(form);
            if (submitter && submitter.name) data.append(submitter.name, submitter.value);
            $$('button[type="submit"]', form).forEach(function (b) { b.disabled = true; });
            fetch(form.action, {method: 'POST', body: data, credentials: 'same-origin'})
                .then(function (res) {
                    if (!res.ok) throw new Error('fail');
                    try {
                        sessionStorage.setItem('iw-toast', JSON.stringify({
                            message: form.dataset.async || '처리했어요.', undo: form.dataset.undo || null }));
                    } catch (err) { /* 무시 */ }
                    location.reload();
                })
                .catch(function () {
                    $$('button[type="submit"]', form).forEach(function (b) { b.disabled = false; });
                    toast('처리하지 못했어요. 다시 시도해 주세요.', 'error');
                });
            return;
        }
        // 두 번 누르기 방지
        if (form.dataset.once !== undefined) {
            setTimeout(function () { $$('button[type="submit"]', form).forEach(function (b) { b.disabled = true; }); }, 0);
        }
    });

    // ---------- 3. ⋯ 메뉴 ----------
    // 표 안의 메뉴는 CSS 에서 position: fixed (상자에 잘리거나 스크롤을 만들지 않게). 열 때 버튼 위치로 자리를 잡고,
    // 아래 공간이 모자라면 버튼 위쪽으로 연다
    function placeFixedMenu(menu, trigger) {
        var r = trigger.getBoundingClientRect();
        var h = menu.offsetHeight, gap = 6;
        var below = window.innerHeight - r.bottom - gap - 8;
        var up = h > below && r.top - gap - 8 > below;
        menu.style.position = 'fixed';
        menu.style.top = (up ? Math.max(8, r.top - gap - h) : r.bottom + gap) + 'px';
        menu.style.right = Math.max(8, window.innerWidth - r.right) + 'px';
        menu.style.transformOrigin = up ? 'bottom right' : 'top right';
    }
    function closeMenus(except) {
        $$('.menu.open').forEach(function (m) {
            if (m !== except) {
                m.classList.remove('open');
                var t = m.parentElement && m.parentElement.querySelector('[data-menu-trigger]');
                if (t) t.setAttribute('aria-expanded', 'false');
            }
        });
    }
    document.addEventListener('click', function (e) {
        var trigger = e.target.closest('[data-menu-trigger]');
        if (trigger) {
            e.preventDefault();
            var menu = trigger.parentElement.querySelector('.menu');
            var open = !menu.classList.contains('open');
            closeMenus(menu);
            if (open && trigger.closest('.table-box')) placeFixedMenu(menu, trigger);
            menu.classList.toggle('open', open);
            trigger.setAttribute('aria-expanded', String(open));
            if (open) { var first = menu.querySelector('a, button:not([aria-disabled="true"])'); if (first) first.focus(); }
            return;
        }
        var disabled = e.target.closest('.menu [aria-disabled="true"]');
        if (disabled) { e.preventDefault(); toast(disabled.dataset.why || '아직 지원하지 않는 기능이에요.'); return; }
        if (!e.target.closest('.menu')) closeMenus();
    });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeMenus(); });
    // 화면 기준으로 띄운 메뉴(표 안의 ⋯)만 스크롤하면 닫는다 (자리가 어긋나므로). 항목 추가 메뉴 등은 그대로
    window.addEventListener('scroll', function () {
        $$('.menu.open').forEach(function (m) {
            if (m.style.position === 'fixed') {
                m.classList.remove('open');
                var t = m.parentElement && m.parentElement.querySelector('[data-menu-trigger]');
                if (t) t.setAttribute('aria-expanded', 'false');
            }
        });
    }, true);
    window.addEventListener('resize', function () { closeMenus(); });

    // ---------- 4. 서명 패드 / 사진 미리보기 / 시계 / 반복 주기 ----------
    function initSignature(root) {
        $$('canvas.sig-pad', root).forEach(function (canvas) {
            if (canvas._iw) return;
            canvas._iw = true;
            var ctx = canvas.getContext('2d');
            var drawing = false, drawn = false;
            function resize() {
                var ratio = window.devicePixelRatio || 1;
                canvas.width = canvas.offsetWidth * ratio;
                canvas.height = canvas.offsetHeight * ratio;
                ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
                ctx.lineWidth = 2.2; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = '#0B0F19';
            }
            resize();
            function pos(e) { var r = canvas.getBoundingClientRect(); return {x: e.clientX - r.left, y: e.clientY - r.top}; }
            canvas.addEventListener('pointerdown', function (e) {
                drawing = true; canvas.setPointerCapture(e.pointerId);
                var p = pos(e); ctx.beginPath(); ctx.moveTo(p.x, p.y);
            });
            canvas.addEventListener('pointermove', function (e) {
                if (!drawing) return;
                var p = pos(e); ctx.lineTo(p.x, p.y); ctx.stroke(); drawn = true;
            });
            ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (t) {
                canvas.addEventListener(t, function () { drawing = false; });
            });
            var wrap = canvas.closest('.sig-wrap');
            var clear = wrap && wrap.querySelector('[data-sig-clear]');
            if (clear) clear.addEventListener('click', function () { ctx.clearRect(0, 0, canvas.width, canvas.height); drawn = false; });
            var form = canvas.closest('form');
            if (form) form.addEventListener('submit', function () {
                var input = form.querySelector('input[name="' + canvas.dataset.target + '"]');
                if (input) input.value = drawn ? canvas.toDataURL('image/png') : '';
            });
        });
    }

    function initPhotoPreview(root) {
        $$('.photo-pick input[type=file]', root).forEach(function (input) {
            if (input._iw) return;
            input._iw = true;
            input.addEventListener('change', function () {
                var drop = input.closest('.photo-pick').querySelector('.drop');
                var file = input.files && input.files[0];
                if (!drop || !file) return;
                var img = drop.querySelector('img') || document.createElement('img');
                img.alt = '';
                img.src = URL.createObjectURL(file);
                drop.appendChild(img);
            });
        });
    }

    function initClock(root) {
        var clock = $('[data-clock]', root);
        if (!clock || clock._iw) return;
        clock._iw = true;
        var tick = function () {
            var d = new Date();
            clock.textContent = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
            var s = $('[data-clock-sec]', root);
            if (s) s.textContent = ':' + String(d.getSeconds()).padStart(2, '0');
        };
        tick();
        setInterval(tick, 1000);
    }

    function initCycle(root) {
        var cycle = $('[data-cycle-select]', root);
        if (!cycle) return;
        var sync = function () {
            $$('[data-cycle-show]', root).forEach(function (el) { el.hidden = el.dataset.cycleShow !== cycle.value; });
        };
        cycle.addEventListener('change', sync);
        sync();
    }

    // ---------- 5. 폼 빌더 ----------
    function initBuilder(root) {
        $$('[data-builder]', root).forEach(function (builder) {
            var list = $('[data-builder-list]', builder);
            var tpl = $('template[data-builder-tpl]', builder);
            var labels = JSON.parse(builder.dataset.labels || '{}');
            function renumber() {
                $$('.f-card', list).forEach(function (card, i) {
                    var n = card.querySelector('[data-n]');
                    if (n) n.textContent = String(i + 1).padStart(2, '0');
                    var t = card.querySelector('[data-type-input]');
                    var opt = card.querySelector('[data-options]');
                    if (t && opt) opt.hidden = t.value !== 'CHOICE';
                });
                var empty = $('[data-builder-empty]', builder);
                if (empty) empty.hidden = $$('.f-card', list).length > 0;
            }
            builder.addEventListener('click', function (e) {
                var pick = e.target.closest('[data-add-type]');
                if (pick) {
                    var type = pick.dataset.addType;
                    var node = tpl.content.cloneNode(true);
                    var card = node.querySelector('.f-card');
                    card.querySelector('[data-type-input]').value = type;
                    card.querySelector('[data-type-chip]').textContent = labels[type] || pick.textContent.trim();
                    list.appendChild(node);
                    renumber();
                    closeMenus();
                    var input = list.lastElementChild.querySelector('input[type=text]');
                    if (input) input.focus();
                    return;
                }
                var rm = e.target.closest('[data-remove-card]');
                if (rm) {
                    rm.closest('.f-card').remove();
                    renumber();
                }
            });
            renumber();
        });
    }

    // ---------- 6. 목록-상세 ----------
    function initMasterDetail(root) {
        $$('[data-md]', root).forEach(function (md) {
            var pane = $('[data-md-pane]', md);
            if (!pane) return;
            function wide() { return window.matchMedia('(min-width: 1101px)').matches; }
            function load(item, push) {
                $$('[data-md-item]', md).forEach(function (i) { i.classList.toggle('selected', i === item); i.removeAttribute('aria-current'); });
                item.setAttribute('aria-current', 'true');
                pane.setAttribute('aria-busy', 'true');
                fetch(item.getAttribute('href'), {credentials: 'same-origin'})
                    .then(function (r) { return r.text(); })
                    .then(function (html) {
                        var doc = new DOMParser().parseFromString(html, 'text/html');
                        var content = doc.querySelector('[data-md-content]');
                        pane.innerHTML = '';
                        if (content) pane.appendChild(document.importNode(content, true));
                        else pane.innerHTML = '<div class="empty"><b>불러오지 못했어요</b></div>';
                        pane.removeAttribute('aria-busy');
                        init(pane);
                    })
                    .catch(function () { location.href = item.getAttribute('href'); });
            }
            md.addEventListener('click', function (e) {
                var item = e.target.closest('[data-md-item]');
                if (!item || !wide() || e.metaKey || e.ctrlKey) return;
                e.preventDefault();
                load(item, true);
            });
            var first = $('[data-md-item]', md);
            if (first && wide() && !pane.querySelector('[data-md-content]')) load(first, false);
        });
    }

    // ---------- 7. 세그먼트 필터 (할 일 목록) ----------
    function initFilterTabs(root) {
        $$('[data-filter-tabs]', root).forEach(function (tabs) {
            var target = document.getElementById(tabs.dataset.filterTabs);
            if (!target) return;
            tabs.addEventListener('click', function (e) {
                var b = e.target.closest('[data-filter]');
                if (!b) return;
                $$('[data-filter]', tabs).forEach(function (x) { x.classList.toggle('active', x === b); x.setAttribute('aria-selected', String(x === b)); });
                var f = b.dataset.filter;
                var shown = 0;
                $$('[data-state]', target).forEach(function (row) {
                    var ok = f === 'all' || row.dataset.state === f;
                    row.hidden = !ok;
                    if (ok) shown++;
                });
                var empty = document.querySelector('[data-filter-empty="' + tabs.dataset.filterTabs + '"]');
                if (empty) empty.hidden = shown > 0;
            });
        });
    }

    // ---------- 8. 표 → 좁은 화면 카드 ----------
    // 좁은 화면에서는 머리행을 숨기고 행마다 카드로 보여 준다(CSS). 각 칸 앞에 붙는 칸 이름(data-label)을 thead 에서 채우고,
    // 첫 칸은 제목(cell-title), 머리 글자가 없는 칸은 동작 칸(cell-act), ⋯ 버튼이 든 칸은 오른쪽 위(cell-menu)로 표시한다.
    // 템플릿에 data-label 이나 cell-* 를 직접 적은 칸은 그대로 둔다 (data-label="" 이면 이름 없이)
    function initTableCards(root) {
        $$('.table-box:not(.scroll-x) table.t', root).forEach(function (table) {
            var heads = $$('thead th', table).map(function (th) { return th.textContent.replace(/\s+/g, ' ').trim(); });
            $$('tbody tr', table).forEach(function (tr) {
                var cells = $$(':scope > td', tr);
                if (cells.length === 1 && cells[0].colSpan > 1) return;
                var hasTitle = cells.some(function (td) { return td.classList.contains('cell-title'); });
                cells.forEach(function (td, i) {
                    if (td.querySelector('[data-menu-trigger]')) { td.classList.add('cell-menu'); tr.classList.add('has-menu'); return; }
                    if (td.hasAttribute('data-label') || td.classList.contains('cell-title')) return;
                    if (!hasTitle && i === 0) { td.classList.add('cell-title'); return; }
                    var label = heads[i] || '';
                    if (label) td.setAttribute('data-label', label);
                    else if (heads.length) td.classList.add('cell-act');
                });
            });
        });
        fitTables();
    }
    // 900px 이하이거나, 표가 상자 폭에 다 들어가지 않으면 카드로 (넓은 화면에서 표를 잘라 내거나 가로 스크롤을 만들지 않게)
    var NARROW = window.matchMedia('(max-width: 900px)');
    function fitTables() {
        $$('.table-box:not(.scroll-x)').forEach(function (box) {
            var table = $('table.t', box);
            if (!table) return;
            if (NARROW.matches) { box.classList.add('cards'); return; }
            box.classList.remove('cards');
            if (table.scrollWidth > box.clientWidth + 1) box.classList.add('cards');
        });
    }
    var fitTimer;
    function queueFit() { clearTimeout(fitTimer); fitTimer = setTimeout(fitTables, 80); }
    window.addEventListener('resize', queueFit);
    // 창 크기 말고도 상자 폭이 바뀌면 다시 맞춘다 (높이 변화는 무시)
    if (window.ResizeObserver) {
        var boxWidths = new WeakMap();
        var ro = new ResizeObserver(function (entries) {
            var changed = entries.some(function (en) {
                var w = Math.round(en.contentRect.width);
                var prev = boxWidths.get(en.target);
                boxWidths.set(en.target, w);
                return prev !== undefined && prev !== w;
            });
            if (changed) queueFit();
        });
        $$('.table-box:not(.scroll-x)').forEach(function (box) { ro.observe(box); });
    }

    // ---------- 9. 근무 배정 폼 : 직원의 근무제에 있는 근무 유형만 보이기 ----------
    //  [data-shift-member] 의 option data-policy ↔ [data-shift-type] 의 option data-policy
    //  유형을 고르면 시작·끝 시각을 채우고, 휴무(data-off)면 시각 칸을 잠근다 (서버는 휴무를 시각 없이 저장)
    function initShiftForm(root) {
        $$('[data-shift-form]', root).forEach(function (form) {
            var member = $('[data-shift-member]', form), type = $('[data-shift-type]', form);
            var st = form.querySelector('[name="startTime"]'), et = form.querySelector('[name="endTime"]');
            if (!member || !type) return;
            function policyOf() { var o = member.options[member.selectedIndex]; return o ? o.dataset.policy : ''; }
            function syncTimes(fill) {
                var o = type.options[type.selectedIndex];
                var off = o && o.dataset.off === 'true';
                [st, et].forEach(function (i) { if (i) { i.disabled = off; i.required = !off; } });
                if (fill && o && o.value && !off) {
                    if (st && o.dataset.start) st.value = o.dataset.start;
                    if (et && o.dataset.end) et.value = o.dataset.end;
                }
            }
            function filter() {
                var p = policyOf();
                $$('option', type).forEach(function (o) {
                    if (!o.value) return;
                    var ok = o.dataset.policy === p;
                    o.hidden = !ok;
                    o.disabled = !ok;
                });
                var cur = type.options[type.selectedIndex];
                if (cur && cur.disabled) type.value = '';
                syncTimes(false);
            }
            member.addEventListener('change', filter);
            type.addEventListener('change', function () { syncTimes(true); });
            filter();
        });
    }

    // ---------- 10. 링크 복사 (이슈 목록·상세) ----------
    function copyText(text) {
        if (navigator.clipboard && window.isSecureContext) return navigator.clipboard.writeText(text);
        // http 사내망에서는 clipboard API 가 없으므로 예전 방식으로
        return new Promise(function (resolve, reject) {
            var ta = document.createElement('textarea');
            ta.value = text;
            ta.setAttribute('readonly', '');
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.select();
            var ok = false;
            try { ok = document.execCommand('copy'); } catch (e) { /* 아래에서 실패 처리 */ }
            ta.remove();
            if (ok) resolve(); else reject(new Error('copy'));
        });
    }
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-copy-link]');
        if (!b) return;
        e.preventDefault();
        copyText(new URL(b.dataset.copyLink, location.origin).href)
            .then(function () { toast('링크 복사됨'); })
            .catch(function () { toast('링크를 복사하지 못했어요.', 'error'); });
    });

    function init(root) {
        initShiftForm(root);
        initTableCards(root);
        initSignature(root);
        initPhotoPreview(root);
        initClock(root);
        initCycle(root);
        initBuilder(root);
        initFilterTabs(root);
    }

    init(document);
    initMasterDetail(document);
    flushToasts();
})();

// 월별 스케줄 달력의 '+' : 배정 폼 날짜를 그 날로 바꾸고 폼으로 이동
document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-pick-date]');
    if (!b) return;
    var input = document.getElementById('sdate');
    if (!input) return;
    input.value = b.dataset.pickDate;
    var form = document.getElementById('assign');
    if (form) form.scrollIntoView({behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start'});
    input.focus();
});
