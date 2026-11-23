package com.mysite.sbb.chat;

import com.mysite.sbb.chat.dto.ChatMessageForm;
import com.mysite.sbb.chat.service.ChatMessageService;
import com.mysite.sbb.chat.service.ChatRoomService;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.outbox.service.OutboxRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 서버 + 실제 WebSocket 으로 SEC-04, 07 과 실시간 전달을 확인한다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.upload-dir=build/test-uploads-ws", "app.private-dir=build/test-private-ws", "app.outbox.fast-path=false"})
class ChatWebSocketSecurityTest {

    @LocalServerPort int port;
    @Autowired ChatRoomService roomService;
    @Autowired ChatMessageService messageService;
    @Autowired MemberRepository memberRepository;
    @Autowired OutboxRelay outboxRelay;
    @Autowired SimpUserRegistry userRegistry;

    @Test
    @DisplayName("SEC-04 : 로그인 없이는 /ws 연결 불가")
    void handshakeNeedsLogin() {
        HttpClient anonymous = HttpClient.newHttpClient();
        assertThatThrownBy(() -> anonymous.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + "/ws"), new Frames())
                .get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("SEC-07 : STOMP CONNECT 에 CSRF 토큰이 없으면 거부")
    void connectNeedsCsrf() throws Exception {
        Session s = login("emp1");
        Frames frames = new Frames();
        WebSocket ws = s.open(frames);
        ws.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).get(5, TimeUnit.SECONDS);
        assertThat(frames.next()).startsWith("ERROR");
    }

    @Test
    @DisplayName("SEC-04 : 참여하지 않은 방 구독은 거부, 참여자는 실시간으로 받는다 (AC-06)")
    void subscribeChecksParticipantAndDelivers() throws Exception {
        Long emp1 = memberRepository.findByUsername("emp1").orElseThrow().getId();
        Long emp2 = memberRepository.findByUsername("emp2").orElseThrow().getId();
        Long room = roomService.openDirect(emp1, emp2);

        // 참여하지 않은 emp3
        Session outsider = login("emp3");
        Frames denied = new Frames();
        WebSocket w1 = outsider.open(denied);
        connect(w1, outsider, denied);
        w1.sendText("SUBSCRIBE\nid:s1\ndestination:/topic/rooms/" + room + "\n\n\0", true).get(5, TimeUnit.SECONDS);
        assertThat(denied.next()).startsWith("ERROR");

        // 참여자 emp2 : 방 + 개인 큐
        Session member = login("emp2");
        Frames frames = new Frames();
        WebSocket w2 = member.open(frames);
        connect(w2, member, frames);
        w2.sendText("SUBSCRIBE\nid:s1\ndestination:/topic/rooms/" + room + "\n\n\0", true).get(5, TimeUnit.SECONDS);
        w2.sendText("SUBSCRIBE\nid:s2\ndestination:/user/queue/chat\n\n\0", true).get(5, TimeUnit.SECONDS);
        awaitSubscriptions("emp2", 2);

        ChatMessageForm form = new ChatMessageForm();
        form.setClientMessageId(UUID.randomUUID().toString());
        form.setContent("실시간 메시지");
        messageService.send(room, emp1, form, null);
        outboxRelay.pollOnce();

        String a = frames.next(), b = frames.next();
        assertThat(a + b).contains("실시간 메시지").contains("\"kind\":\"MESSAGE\"").contains("\"kind\":\"NEW\"");
        w2.abort();
    }

    // 내장 브로커는 RECEIPT 를 보내지 않으므로 구독이 등록될 때까지 기다린다
    private void awaitSubscriptions(String username, int count) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            SimpUser u = userRegistry.getUser(username);
            if (u != null && u.getSessions().stream().mapToInt(x -> x.getSubscriptions().size()).sum() >= count) return;
            Thread.sleep(50);
        }
        throw new AssertionError("구독이 등록되지 않았어요");
    }

    // STOMP CONNECT (CSRF 포함) → CONNECTED
    private void connect(WebSocket ws, Session s, Frames frames) throws Exception {
        ws.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nX-CSRF-TOKEN:" + s.csrf + "\n\n\0", true).get(5, TimeUnit.SECONDS);
        assertThat(frames.next()).startsWith("CONNECTED");
    }

    private Session login(String username) throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NORMAL).build();
        String base = "http://localhost:" + port;
        String page = client.send(HttpRequest.newBuilder(URI.create(base + "/member/login")).build(), HttpResponse.BodyHandlers.ofString()).body();
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(page);
        assertThat(m.find()).isTrue();
        String form = "username=" + username + "&password=1234&_csrf=" + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8);
        client.send(HttpRequest.newBuilder(URI.create(base + "/member/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        String chat = client.send(HttpRequest.newBuilder(URI.create(base + "/chat")).build(), HttpResponse.BodyHandlers.ofString()).body();
        Matcher t = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]+)\"").matcher(chat);
        assertThat(t.find()).as("로그인 뒤 채팅 화면의 CSRF 토큰").isTrue();
        String cookie = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                .findFirst().orElseThrow().toString();
        return new Session(cookie, t.group(1), port);
    }

    record Session(String cookie, String csrf, int port) {
        WebSocket open(Frames frames) throws Exception {
            return HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie", cookie)
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create("ws://localhost:" + port + "/ws"), frames).get(5, TimeUnit.SECONDS);
        }
    }

    static class Frames implements WebSocket.Listener {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String f = buffer.toString();
                buffer.setLength(0);
                if (!f.isBlank()) queue.add(f);
            }
            ws.request(1);
            return null;
        }

        String next() throws InterruptedException {
            String f = queue.poll(8, TimeUnit.SECONDS);
            assertThat(f).as("STOMP 프레임 수신").isNotNull();
            return f;
        }
    }
}
