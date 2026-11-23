// InhaWorks 채팅 화면 — chat.js
//  대화방 목록 갱신, 메시지 그리기(textContent 만 사용 : XSS 방지), 전송(clientMessageId 멱등), 읽음, 멘션 자동완성,
//  이전 50개, 재연결 시 afterId 보정, WebSocket 불가 시 폴링
(function () {
    'use strict';

    var shell = document.querySelector('[data-chat]');
    if (!shell || !window.IWChat) return;
    var C = window.IWChat;
    var meId = Number(shell.dataset.me);
    var roomId = shell.dataset.room ? Number(shell.dataset.room) : null;
    window.IWChatRoom = function () { return roomId; };

    var MAX_FILES = 10, MAX_FILE_BYTES = 20 * 1024 * 1024;
    var ALLOWED = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'hwp', 'hwpx', 'txt', 'csv', 'zip'];

    function post(path, data) {
        var headers = { Accept: 'application/json' };
        headers[C.csrf.header] = C.csrf.token;
        return fetch(C.api(path), { method: 'POST', credentials: 'same-origin', headers: headers, body: data });
    }
    function json(res) {
        return res.text().then(function (t) {
            var d = null;
            try { d = t ? JSON.parse(t) : null; } catch (e) { /* HTML 오류 화면 */ }
            if (!res.ok) throw new Error(d && d.message ? d.message : (res.status === 413 ? '파일이 너무 커요.' : '처리하지 못했어요. 다시 시도해 주세요.'));
            return d;
        });
    }
    function form(obj) {
        var f = new URLSearchParams();
        Object.keys(obj).forEach(function (k) { f.append(k, obj[k]); });
        return f;
    }
    function el(tag, cls, text) {
        var n = document.createElement(tag);
        if (cls) n.className = cls;
        if (text !== undefined && text !== null) n.textContent = text;
        return n;
    }
    function uuid() {
        if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
        return 'm-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
    }
    function sizeText(b) {
        if (b >= 1048576) return (b / 1048576).toFixed(1) + 'MB';
        return Math.max(1, Math.round(b / 1024)) + 'KB';
    }

    // ---------- 대화방 목록 ----------
    var search = document.querySelector('[data-room-search]');
    var listTimer = null;
    function refreshList() {
        clearTimeout(listTimer);
        listTimer = setTimeout(function () {
            var q = search ? search.value.trim() : '';
            var url = '/chat/rooms/list?' + form({ q: q, active: roomId || '' }).toString();
            fetch(C.api(url), { credentials: 'same-origin' })
                .then(function (r) { if (!r.ok) throw new Error(); return r.text(); })
                .then(function (html) {
                    var cur = document.querySelector('[data-room-list]');
                    if (cur && !cur.querySelector('.menu.open')) cur.outerHTML = html;
                })
                .catch(function () { /* 다음 이벤트에 다시 */ });
        }, 150);
    }
    if (search) search.addEventListener('input', refreshList);
    C.onPersonal(refreshList);

    document.addEventListener('click', function (e) {
        var mute = e.target.closest('[data-room-mute]');
        var leave = e.target.closest('[data-room-leave]');
        if (!mute && !leave) return;
        var row = e.target.closest('[data-room-id]');
        var id = row.dataset.roomId;
        if (mute) {
            post('/chat/rooms/' + id + '/mute', form({ muted: row.dataset.muted !== 'true' })).then(json)
                .then(function (d) { C.setUnread(d.unreadTotal); window.iwToast(d.muted ? '알림을 껐어요.' : '알림을 켰어요.'); refreshList(); })
                .catch(function (err) { window.iwToast(err.message, 'error'); });
        } else {
            window.iwConfirm({ title: '이 대화방을 나갈까요?', body: '새 메시지가 와도 다시 들어오지 않아요.', ok: '나가기', danger: true }).then(function (ok) {
                if (!ok) return;
                post('/chat/rooms/' + id + '/leave', form({})).then(json).then(function () {
                    if (Number(id) === roomId) location.href = C.api('/chat'); else refreshList();
                }).catch(function (err) { window.iwToast(err.message, 'error'); });
            });
        }
    });

    // ---------- 새 대화 ----------
    var newBtn = document.querySelector('[data-new-chat]');
    if (newBtn) newBtn.addEventListener('click', function () {
        var tpl = document.querySelector('template[data-new-chat-tpl]');
        var d = C.openDialog(tpl.content.cloneNode(true), 'iw-newchat-title');
        var box = d.box, start = box.querySelector('[data-start]');
        function picked() { return Array.prototype.map.call(box.querySelectorAll('[data-people] input:checked'), function (i) { return i.value; }); }
        box.querySelector('[data-people]').addEventListener('change', function () {
            var n = picked().length;
            start.disabled = n === 0;
            start.textContent = n > 1 ? '그룹 대화 시작 (' + n + '명)' : '대화 시작';
        });
        box.querySelector('[data-people-search]').addEventListener('input', function (e) {
            var q = e.target.value.trim().toLowerCase();
            box.querySelectorAll('.chat-person').forEach(function (p) { p.hidden = q && p.dataset.search.toLowerCase().indexOf(q) < 0; });
        });
        start.addEventListener('click', function () {
            var ids = picked();
            start.disabled = true;
            if (ids.length === 1) {
                var f = document.getElementById('chat-direct-form');
                f.action = f.action.replace(/\/0$/, '/' + encodeURIComponent(ids[0]));
                f.submit();
                return;
            }
            var g = document.getElementById('chat-group-form');
            ids.forEach(function (id) { var i = el('input'); i.type = 'hidden'; i.name = 'memberIds'; i.value = id; g.appendChild(i); });
            var name = el('input'); name.type = 'hidden'; name.name = 'name'; name.value = box.querySelector('[data-group-name]').value;
            g.appendChild(name);
            g.submit();
        });
    });

    if (!roomId) return;

    // ---------- 대화창 ----------
    var log = document.querySelector('[data-log]');
    var moreBtn = document.querySelector('[data-more]');
    var emptyHint = document.querySelector('[data-log-empty]');
    var conn = document.querySelector('[data-conn]');
    var seen = {};          // [REL-23] 메시지 id 로 화면 중복을 거른다
    var pendingByClient = {};
    var lastId = 0, firstId = null, loadingMore = false;
    var people = {};        // memberId → { name, lastRead, ... }
    var readTimer = null, sentRead = 0;

    function setPeople(list) {
        people = {};
        (list || []).forEach(function (p) { people[p.id] = p; });
        var c = document.querySelector('[data-member-count]');
        if (c) c.textContent = (list || []).length + '명';
    }

    function atBottom() { return log.scrollHeight - log.scrollTop - log.clientHeight < 80; }
    function toBottom() { log.scrollTop = log.scrollHeight; }

    // 본문 : 텍스트 노드 + http(s) 자동 링크 + 멘션 강조 (HTML 로 해석하지 않는다)
    var URL_RE = /https?:\/\/[^\s<>"']+/g;
    function renderText(target, text, mentionNames) {
        var pos = 0, m;
        function plain(s) {
            if (!s) return;
            var names = mentionNames.filter(Boolean);
            if (!names.length) { target.appendChild(document.createTextNode(s)); return; }
            var re = new RegExp('(' + names.map(function (n) { return '@' + n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }).join('|') + ')', 'g');
            s.split(re).forEach(function (part, i) {
                if (i % 2) target.appendChild(el('span', 'chat-mention', part));
                else if (part) target.appendChild(document.createTextNode(part));
            });
        }
        URL_RE.lastIndex = 0;
        while ((m = URL_RE.exec(text))) {
            plain(text.substring(pos, m.index));
            var a = el('a', null, m[0]);
            a.href = m[0];
            a.target = '_blank';
            a.rel = 'noopener noreferrer';
            target.appendChild(a);
            pos = m.index + m[0].length;
        }
        plain(text.substring(pos));
    }

    function avatar(s) {
        if (s && s.photo) { var i = el('img', 'avatar sm'); i.src = s.photo; i.alt = ''; return i; }
        var a = el('span', 'avatar sm', s ? s.initials : '·');
        a.setAttribute('aria-hidden', 'true');
        return a;
    }

    function timeText(iso) {
        var d = new Date(iso);
        if (isNaN(d)) return '';
        return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    }
    function dayText(iso) {
        var d = new Date(iso);
        return isNaN(d) ? '' : d.getFullYear() + '. ' + (d.getMonth() + 1) + '. ' + d.getDate() + '.';
    }

    function buildMessage(m) {
        var mine = m.sender && m.sender.id === meId;
        var row = el('div', 'chat-msg' + (mine ? ' mine' : ''));
        row.dataset.id = m.id;
        row.dataset.day = dayText(m.createdAt);
        if (!mine) {
            var face = el('button', 'chat-face');
            face.type = 'button';
            if (m.sender) { face.dataset.memberCard = m.sender.id; face.setAttribute('aria-label', m.sender.name + ' 프로필'); }
            face.appendChild(avatar(m.sender));
            row.appendChild(face);
        }
        var body = el('div', 'chat-msg-body');
        if (!mine && m.sender) {
            var who = el('div', 'chat-who');
            who.appendChild(el('b', null, m.sender.name + (m.sender.resigned ? ' (퇴사)' : '')));
            if (m.sender.policyLabel) who.appendChild(el('span', 'chip policy-chip ' + (m.sender.policyTone || ''), m.sender.policyLabel));
            body.appendChild(who);
        }
        var line = el('div', 'chat-line');
        var bubble = el('div', 'chat-bubble');
        if (m.content) {
            var t = el('div', 'chat-text');
            var names = (m.mentionIds || []).map(function (id) { return people[id] ? people[id].name : null; });
            if (m.mentionIds && m.mentionIds.indexOf(meId) >= 0) row.classList.add('mentioned');
            renderText(t, m.content, names);
            bubble.appendChild(t);
        }
        (m.attachments || []).forEach(function (a) {
            var url = C.api(a.url);
            if (a.image) {
                var link = el('a', 'chat-img');
                link.href = url; link.target = '_blank'; link.rel = 'noopener';
                var img = el('img'); img.src = url; img.alt = a.name; img.loading = 'lazy';
                link.appendChild(img);
                bubble.appendChild(link);
            } else {
                var file = el('a', 'chat-file');
                file.href = url;
                file.setAttribute('download', a.name);
                file.appendChild(el('span', null, '📎'));
                var info = el('span');
                info.appendChild(el('b', null, a.name));
                info.appendChild(el('small', null, sizeText(a.size)));
                file.appendChild(info);
                bubble.appendChild(file);
            }
        });
        line.appendChild(bubble);
        var meta = el('div', 'chat-meta');
        var read = el('span', 'chat-read');
        read.dataset.read = '';
        meta.appendChild(read);
        meta.appendChild(el('time', 'tnum', timeText(m.createdAt)));
        line.appendChild(meta);
        body.appendChild(line);
        row.appendChild(body);
        row._msg = m;
        updateRead(row);
        return row;
    }

    // 읽지 않은 사람 수 (나와 보낸 사람 제외). 1:1 은 상대가 읽으면 ✓
    function updateRead(row) {
        var m = row._msg;
        if (!m || !m.id) return;
        var target = row.querySelector('[data-read]');
        if (!target) return;
        var ids = Object.keys(people).map(Number).filter(function (id) { return !m.sender || id !== m.sender.id; });
        var unread = ids.filter(function (id) { return people[id].lastRead < m.id; }).length;
        var mine = m.sender && m.sender.id === meId;
        if (ids.length === 1 && mine) {
            target.textContent = unread ? '1' : '✓';
            target.classList.toggle('done', !unread);
            target.setAttribute('aria-label', unread ? '안 읽음' : '읽음');
        } else {
            target.textContent = unread > 0 ? String(unread) : '';
            target.setAttribute('aria-label', unread > 0 ? '안 읽은 사람 ' + unread + '명' : '');
        }
    }
    function updateAllRead() {
        log.querySelectorAll('.chat-msg').forEach(updateRead);
    }

    // id 순서를 지켜 끼워 넣는다 (브로드캐스트가 늦게·두 번 와도 안전)
    function insert(m) {
        if (seen[m.id]) return false;
        seen[m.id] = true;
        var row = buildMessage(m);
        var rows = log.querySelectorAll('.chat-msg');
        var before = null;
        for (var i = rows.length - 1; i >= 0; i--) {
            if (Number(rows[i].dataset.id) < m.id) break;
            before = rows[i];
        }
        log.insertBefore(row, before);
        lastId = Math.max(lastId, m.id);
        firstId = firstId === null ? m.id : Math.min(firstId, m.id);
        if (emptyHint) emptyHint.hidden = true;
        updateDays();
        return true;
    }

    function updateDays() {
        log.querySelectorAll('.chat-day').forEach(function (d) { d.remove(); });
        var prev = null;
        log.querySelectorAll('.chat-msg').forEach(function (r) {
            if (r.dataset.day !== prev) {
                log.insertBefore(el('div', 'chat-day', r.dataset.day), r);
                prev = r.dataset.day;
            }
        });
    }

    function load(params) {
        return fetch(C.api('/chat/rooms/' + roomId + '/messages' + (params ? '?' + params : '')),
            { credentials: 'same-origin', headers: { Accept: 'application/json' } }).then(json);
    }

    function markRead() {
        if (document.hidden || !lastId || lastId <= sentRead || !atBottom()) return;
        clearTimeout(readTimer);
        readTimer = setTimeout(function () {
            var target = lastId;
            post('/chat/rooms/' + roomId + '/read', form({ lastReadMessageId: target })).then(json).then(function (d) {
                sentRead = Math.max(sentRead, target);
                if (people[meId]) people[meId].lastRead = Math.max(people[meId].lastRead, target);
                C.setUnread(d.unreadTotal);
                refreshList();
            }).catch(function () { /* 읽음은 다음 요청이 자동으로 보정한다 (REL-22) */ });
        }, 200);
    }

    // 처음 불러오기
    load('').then(function (d) {
        setPeople(d.participants);
        d.messages.forEach(insert);
        moreBtn.hidden = !d.hasMore;
        if (emptyHint) emptyHint.hidden = d.messages.length > 0;
        toBottom();
        markRead();
    }).catch(function (err) { window.iwToast(err.message, 'error'); });

    // 이전 50개
    function loadOlder() {
        if (loadingMore || moreBtn.hidden || firstId === null) return;
        loadingMore = true;
        var h = log.scrollHeight;
        load('beforeId=' + firstId).then(function (d) {
            d.messages.forEach(insert);
            moreBtn.hidden = !d.hasMore;
            log.scrollTop += log.scrollHeight - h;
        }).catch(function (err) { window.iwToast(err.message, 'error'); })
            .then(function () { loadingMore = false; });
    }
    moreBtn.addEventListener('click', loadOlder);
    log.addEventListener('scroll', function () {
        if (log.scrollTop < 40) loadOlder();
        markRead();
    });

    // [REL-22] 재연결·화면 복귀 때 마지막으로 받은 id 이후를 받아 빈틈을 메운다
    var catching = false;
    function catchUp() {
        if (catching) return;
        catching = true;
        var stick = atBottom();
        load('afterId=' + lastId).then(function (d) {
            setPeople(d.participants);
            d.messages.forEach(insert);
            updateAllRead();
            if (stick) toBottom();
            markRead();
        }).catch(function () { /* 다음 주기에 다시 */ }).then(function () { catching = false; });
    }

    // 실시간 수신
    var unsubscribe = null;
    function subscribeRoom() {
        if (unsubscribe) return;
        unsubscribe = C.subscribe('/topic/rooms/' + roomId, function (e) {
            if (e.kind === 'MESSAGE') {
                var stick = atBottom() || (e.message.sender && e.message.sender.id === meId);
                if (e.message.mentionIds && e.message.mentionIds.some(function (id) { return !people[id]; })) catchUp();
                if (insert(e.message) && stick) toBottom();
                markRead();
            } else if (e.kind === 'READ') {
                if (people[e.memberId]) {
                    people[e.memberId].lastRead = Math.max(people[e.memberId].lastRead, e.lastReadMessageId);
                    updateAllRead();
                }
            }
        }, function () {
            unsubscribe = null;
            denied = true;
            window.iwToast('이 대화방에 더 이상 참여하고 있지 않아요.', 'error');
            var c = document.querySelector('[data-compose]');
            if (c) c.hidden = true;
        });
    }
    var denied = false;
    subscribeRoom();

    // 연결 상태 + 폴링 대체 (CHT-31, 32)
    var roomPoll = null;
    function showConn(up) {
        if (conn) { conn.hidden = up; conn.textContent = C.everConnected() ? '다시 연결 중…' : '실시간 연결 대기 중 (자동 새로 고침)'; }
        if (up && roomPoll) { clearInterval(roomPoll); roomPoll = null; }
        if (!up && !roomPoll && !denied) roomPoll = setInterval(catchUp, 3000);
    }
    C.onState(function (up) {
        showConn(up);
        if (up) { if (!denied) subscribeRoom(); catchUp(); }
    });
    showConn(C.isConnected());
    document.addEventListener('visibilitychange', function () { if (!document.hidden) { catchUp(); markRead(); } });

    // ---------- 보내기 ----------
    var compose = document.querySelector('[data-compose]');
    if (!compose) return;
    var text = compose.querySelector('[data-text]');
    var fileInput = compose.querySelector('[data-file-input]');
    var fileList = compose.querySelector('[data-file-list]');
    var sendBtn = compose.querySelector('[data-send]');
    var mentionBox = compose.querySelector('[data-mention-box]');
    var files = [], pickedMentions = {};
    var draftId = null;     // 실패 후 다시 보낼 때 같은 키를 쓴다 (REL-20)

    function autoGrow() {
        text.style.height = 'auto';
        text.style.height = Math.min(text.scrollHeight, 140) + 'px';
    }
    text.addEventListener('input', function () { draftId = null; autoGrow(); updateMention(); });

    function renderFiles() {
        fileList.textContent = '';
        fileList.hidden = files.length === 0;
        files.forEach(function (f, i) {
            var c = el('span', 'chat-file-chip');
            c.appendChild(el('span', null, f.name + ' · ' + sizeText(f.size)));
            var x = el('button', null, '×');
            x.type = 'button';
            x.setAttribute('aria-label', f.name + ' 빼기');
            x.addEventListener('click', function () { files.splice(i, 1); draftId = null; renderFiles(); });
            c.appendChild(x);
            fileList.appendChild(c);
        });
    }
    function addFiles(list) {
        Array.prototype.forEach.call(list, function (f) {
            var ext = f.name.indexOf('.') >= 0 ? f.name.split('.').pop().toLowerCase() : '';
            if (ALLOWED.indexOf(ext) < 0) { window.iwToast(f.name + ' : 올릴 수 없는 형식이에요.', 'error'); return; }
            if (f.size > MAX_FILE_BYTES) { window.iwToast(f.name + ' : 20MB 를 넘어요.', 'error'); return; }
            if (files.length >= MAX_FILES) { window.iwToast('파일은 한 번에 10개까지예요.', 'error'); return; }
            files.push(f);
        });
        draftId = null;
        renderFiles();
    }
    fileInput.addEventListener('change', function () { addFiles(fileInput.files); fileInput.value = ''; });
    compose.addEventListener('dragover', function (e) { e.preventDefault(); });
    compose.addEventListener('drop', function (e) {
        e.preventDefault();
        if (e.dataTransfer && e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
    });

    var sending = false;
    function send() {
        if (sending) return;
        var content = text.value.trim();
        if (!content && !files.length) return;
        if (content.length > 2000) { window.iwToast('메시지는 2,000자까지 보낼 수 있어요.', 'error'); return; }
        sending = true;
        sendBtn.disabled = true;
        draftId = draftId || uuid();
        var data = new FormData();
        data.append('clientMessageId', draftId);
        data.append('content', content);
        Object.keys(pickedMentions).forEach(function (id) {
            if (content.indexOf('@' + pickedMentions[id]) >= 0) data.append('mentionIds', id);
        });
        files.forEach(function (f) { data.append('files', f, f.name); });
        post('/chat/rooms/' + roomId + '/messages', data).then(json).then(function (m) {
            text.value = '';
            files = [];
            pickedMentions = {};
            draftId = null;
            renderFiles();
            autoGrow();
            if (insert(m)) toBottom();
            sentRead = Math.max(sentRead, m.id);
            if (people[meId]) people[meId].lastRead = Math.max(people[meId].lastRead, m.id);
            refreshList();
        }).catch(function (err) {
            // 응답을 못 받았어도 같은 키로 다시 보내면 한 번만 저장된다
            window.iwToast(err.message === 'Failed to fetch' ? '보내지 못했어요. 다시 눌러 주세요.' : err.message, 'error');
        }).then(function () {
            sending = false;
            sendBtn.disabled = false;
            text.focus();
        });
    }
    compose.addEventListener('submit', function (e) { e.preventDefault(); send(); });

    // ---------- 멘션 자동완성 ----------
    var mentionItems = [], mentionIndex = 0, mentionStart = -1;
    function closeMention() { mentionBox.hidden = true; mentionItems = []; mentionStart = -1; }
    function updateMention() {
        var pos = text.selectionStart;
        var before = text.value.substring(0, pos);
        var at = before.lastIndexOf('@');
        if (at < 0 || (at > 0 && !/\s/.test(before.charAt(at - 1)))) { closeMention(); return; }
        var q = before.substring(at + 1);
        if (/\s/.test(q)) { closeMention(); return; }
        q = q.toLowerCase();
        mentionItems = Object.keys(people).map(function (id) { return people[id]; })
            .filter(function (p) { return p.id !== meId && (p.name.toLowerCase().indexOf(q) >= 0 || (p.org || '').toLowerCase().indexOf(q) >= 0); })
            .slice(0, 6);
        if (!mentionItems.length) { closeMention(); return; }
        mentionStart = at;
        mentionIndex = 0;
        renderMention();
    }
    function renderMention() {
        mentionBox.textContent = '';
        mentionBox.hidden = false;
        mentionItems.forEach(function (p, i) {
            var b = el('button', 'chat-mention-item' + (i === mentionIndex ? ' active' : ''));
            b.type = 'button';
            b.setAttribute('role', 'option');
            b.setAttribute('aria-selected', String(i === mentionIndex));
            b.appendChild(avatar({ photo: p.photo, initials: p.initials }));
            var t = el('span');
            t.appendChild(el('b', null, p.name));
            t.appendChild(el('small', null, p.org || ''));
            b.appendChild(t);
            if (p.policyLabel) b.appendChild(el('span', 'chip policy-chip ' + (p.policyTone || ''), p.policyLabel));
            b.addEventListener('mousedown', function (e) { e.preventDefault(); pickMention(p); });
            mentionBox.appendChild(b);
        });
    }
    function pickMention(p) {
        var pos = text.selectionStart;
        var insertText = '@' + p.name + ' ';
        text.value = text.value.substring(0, mentionStart) + insertText + text.value.substring(pos);
        var next = mentionStart + insertText.length;
        text.setSelectionRange(next, next);
        pickedMentions[p.id] = p.name;
        draftId = null;
        closeMention();
        autoGrow();
        text.focus();
    }
    text.addEventListener('blur', function () { setTimeout(closeMention, 120); });
    text.addEventListener('keydown', function (e) {
        if (!mentionBox.hidden && mentionItems.length) {
            if (e.key === 'ArrowDown') { e.preventDefault(); mentionIndex = (mentionIndex + 1) % mentionItems.length; renderMention(); return; }
            if (e.key === 'ArrowUp') { e.preventDefault(); mentionIndex = (mentionIndex + mentionItems.length - 1) % mentionItems.length; renderMention(); return; }
            if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); pickMention(mentionItems[mentionIndex]); return; }
            if (e.key === 'Escape') { e.preventDefault(); closeMention(); return; }
        }
        if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); send(); }
    });
})();
