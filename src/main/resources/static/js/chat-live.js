// InhaWorks 채팅 공통 — chat-live.js (로그인한 모든 화면)
//  1) 아주 작은 STOMP 클라이언트 (외부 라이브러리 없음) : CONNECT 에 CSRF 헤더, 자동 재연결
//  2) /user/queue/chat 구독 → 다른 화면 토스트, 사이드바 안 읽은 수
//  3) WebSocket 을 못 쓰면 짧은 주기 폴링으로 대체 (CHT-32)
//  4) 프로필 카드 : [data-member-card="id"] 를 누르면 팝업
(function () {
    'use strict';

    var meta = function (name) { var m = document.querySelector('meta[name="' + name + '"]'); return m ? m.content : ''; };
    var base = meta('iw-base') || '/';
    var csrf = { token: meta('_csrf'), header: meta('_csrf_header') || 'X-CSRF-TOKEN' };
    var api = function (path) { return base.replace(/\/$/, '') + path; };

    // ---------- STOMP ----------
    var ws = null, connected = false, wsFailures = 0, retry = null, everConnected = false;
    var subs = {};      // id → { dest, cb }
    var subSeq = 0, listeners = [];

    function frame(command, headers, body) {
        var out = command + '\n';
        Object.keys(headers || {}).forEach(function (k) { out += k + ':' + headers[k] + '\n'; });
        return out + '\n' + (body || '') + '\0';
    }

    function parse(raw) {
        var end = raw.indexOf('\n\n');
        if (end < 0) return null;
        var lines = raw.substring(0, end).split('\n');
        var command = lines.shift();
        var headers = {};
        lines.forEach(function (l) {
            var i = l.indexOf(':');
            if (i > 0 && headers[l.substring(0, i)] === undefined) headers[l.substring(0, i)] = l.substring(i + 1);
        });
        return { command: command, headers: headers, body: raw.substring(end + 2).replace(/\0[\s\S]*$/, '') };
    }

    function setState(up) {
        connected = up;
        listeners.forEach(function (fn) { try { fn(up); } catch (e) { /* 다른 화면 코드 오류와 분리 */ } });
        syncPolling();
    }

    function connect() {
        if (!window.WebSocket || ws) return;
        var url = location.origin.replace(/^http/, 'ws') + api('/ws');
        try { ws = new WebSocket(url); } catch (e) { ws = null; schedule(); return; }
        ws.onopen = function () {
            var h = { 'accept-version': '1.2', host: location.host, 'heart-beat': '0,0' };
            h[csrf.header] = csrf.token;
            ws.send(frame('CONNECT', h));
        };
        ws.onmessage = function (e) {
            if (typeof e.data !== 'string' || e.data === '\n') return;
            var f = parse(e.data);
            if (!f) return;
            if (f.command === 'CONNECTED') {
                wsFailures = 0;
                everConnected = true;
                Object.keys(subs).forEach(sendSubscribe);
                setState(true);
            } else if (f.command === 'MESSAGE') {
                var s = subs[f.headers.subscription];
                if (s) { try { s.cb(JSON.parse(f.body)); } catch (err) { /* 잘못된 메시지 무시 */ } }
            } else if (f.command === 'ERROR') {
                // 구독 거부 등 : 방 구독을 빼고 다시 연결한다
                Object.keys(subs).forEach(function (id) {
                    if (subs[id].dest.indexOf('/topic/') === 0) {
                        if (subs[id].onDenied) subs[id].onDenied();
                        delete subs[id];
                    }
                });
            }
        };
        ws.onclose = function () {
            ws = null;
            wsFailures++;
            setState(false);
            schedule();
        };
        ws.onerror = function () { /* onclose 로 이어진다 */ };
    }

    function schedule() {
        if (retry) return;
        var delay = Math.min(30000, 1000 * Math.pow(2, Math.min(wsFailures, 5)));
        retry = setTimeout(function () { retry = null; connect(); }, delay + Math.random() * 500);
    }

    function sendSubscribe(id) {
        if (ws && ws.readyState === 1) ws.send(frame('SUBSCRIBE', { id: id, destination: subs[id].dest, ack: 'auto' }));
    }

    function subscribe(dest, cb, onDenied) {
        var id = 'sub-' + (++subSeq);
        subs[id] = { dest: dest, cb: cb, onDenied: onDenied };
        if (connected) sendSubscribe(id);
        return function () {
            if (connected && ws && subs[id]) ws.send(frame('UNSUBSCRIBE', { id: id }));
            delete subs[id];
        };
    }

    // ---------- 사이드바 안 읽은 수 ----------
    function setUnread(n) {
        document.querySelectorAll('[data-chat-unread]').forEach(function (b) {
            b.textContent = n > 99 ? '99+' : String(n);
            b.hidden = !(n > 0);
        });
    }

    // ---------- 폴링 대체 : 연결이 안 된 동안만 ----------
    var pollTimer = null;
    function pollOnce() {
        fetch(api('/chat/unread'), { credentials: 'same-origin', headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (d) { if (d) setUnread(d.unreadTotal); })
            .catch(function () { /* 다음 주기에 다시 */ });
    }
    function syncPolling() {
        if (connected && pollTimer) { clearInterval(pollTimer); pollTimer = null; }
        if (!connected && !pollTimer) pollTimer = setInterval(pollOnce, 15000);
    }

    // ---------- 다른 화면 토스트 ----------
    function onPersonal(e) {
        if (e.kind !== 'NEW') return;
        if (typeof e.unreadTotal === 'number') setUnread(e.unreadTotal);
        var openRoom = window.IWChatRoom && window.IWChatRoom() === e.roomId && !document.hidden;
        if (e.mine || openRoom || (e.muted && !e.mentioned)) { handlers.forEach(function (h) { h(e); }); return; }
        if (window.iwToast) window.iwToast((e.mentioned ? '[멘션] ' : '') + (e.roomName === e.senderName ? '' : e.roomName + ' · ') + e.senderName + ' : ' + e.preview);
        handlers.forEach(function (h) { h(e); });
    }
    var handlers = [];

    // ---------- 프로필 카드 ----------
    document.addEventListener('click', function (e) {
        var t = e.target.closest('[data-member-card]');
        if (!t) return;
        e.preventDefault();
        fetch(api('/members/' + encodeURIComponent(t.dataset.memberCard) + '/card'), { credentials: 'same-origin' })
            .then(function (r) { if (!r.ok) throw new Error('card'); return r.text(); })
            .then(function (html) { openDialog(html, 'iw-card-title'); })
            .catch(function () { if (window.iwToast) window.iwToast('프로필을 불러오지 못했어요.', 'error'); });
    });

    // 공통 팝업 (서버가 그린 조각 또는 노드)
    function openDialog(content, labelId) {
        var scrim = document.createElement('div');
        scrim.className = 'scrim';
        var box = document.createElement('div');
        box.className = 'modal';
        box.setAttribute('role', 'dialog');
        box.setAttribute('aria-modal', 'true');
        if (labelId) box.setAttribute('aria-labelledby', labelId);
        if (typeof content === 'string') box.innerHTML = content; else box.appendChild(content);
        scrim.appendChild(box);
        var last = document.activeElement;
        function close() {
            scrim.classList.remove('open');
            document.removeEventListener('keydown', onKey);
            setTimeout(function () { scrim.remove(); }, 200);
            if (last && last.focus) last.focus();
        }
        function onKey(ev) { if (ev.key === 'Escape') close(); }
        scrim.addEventListener('click', function (ev) { if (ev.target === scrim || ev.target.closest('[data-close]')) close(); });
        document.addEventListener('keydown', onKey);
        document.body.appendChild(scrim);
        requestAnimationFrame(function () { scrim.classList.add('open'); });
        var focusable = box.querySelector('input, button, a[href]');
        if (focusable) focusable.focus();
        return { box: box, close: close };
    }

    window.IWChat = {
        api: api,
        csrf: csrf,
        subscribe: subscribe,
        setUnread: setUnread,
        openDialog: openDialog,
        isConnected: function () { return connected; },
        everConnected: function () { return everConnected; },
        onState: function (fn) { listeners.push(fn); },
        onPersonal: function (fn) { handlers.push(fn); }
    };

    if (csrf.token) {
        subscribe('/user/queue/chat', onPersonal);
        syncPolling();
        connect();
        window.addEventListener('online', function () { if (!ws) connect(); });
    }
})();
