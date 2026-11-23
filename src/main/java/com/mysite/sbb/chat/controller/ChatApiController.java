package com.mysite.sbb.chat.controller;

import com.mysite.sbb.chat.dto.ChatMessageDto;
import com.mysite.sbb.chat.dto.ChatMessageForm;
import com.mysite.sbb.chat.dto.ChatRoomView;
import com.mysite.sbb.chat.entity.ChatAttachment;
import com.mysite.sbb.chat.service.ChatMessageService;
import com.mysite.sbb.chat.service.ChatRoomService;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 채팅 JSON API : 메시지 조회·전송, 읽음, 알림 끄기, 나가기, 첨부 (오류는 ChatApiAdvice 가 JSON 으로)
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatApiController {

    private final ChatMessageService messageService;
    private final ChatRoomService roomService;
    private final MemberService memberService;
    private final FileStore fileStore;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/rooms/{id}/messages")
    public Map<String, Object> messages(@PathVariable("id") Long id,
                                        @RequestParam(value = "beforeId", required = false) Long beforeId,
                                        @RequestParam(value = "afterId", required = false) Long afterId, Principal principal) {
        Member me = me(principal);
        List<ChatMessageDto> list = messageService.messages(id, me.getId(), beforeId, afterId);
        ChatRoomView view = roomService.view(id, me.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", list);
        body.put("hasMore", afterId == null && list.size() == ChatMessageService.PAGE_SIZE);
        body.put("participants", view.participants());
        return body;
    }

    @PostMapping("/rooms/{id}/messages")
    public ChatMessageDto send(@PathVariable("id") Long id, @Valid ChatMessageForm form, BindingResult binding,
                               @RequestParam(value = "files", required = false) List<MultipartFile> files, Principal principal) {
        if (binding.hasErrors()) throw new BusinessException(binding.getAllErrors().get(0).getDefaultMessage());
        return messageService.send(id, me(principal).getId(), form, files);
    }

    @PostMapping("/rooms/{id}/read")
    public Map<String, Object> read(@PathVariable("id") Long id, @RequestParam("lastReadMessageId") long lastReadMessageId,
                                    Principal principal) {
        Member me = me(principal);
        Long advanced = messageService.markRead(id, me.getId(), lastReadMessageId);
        if (advanced != null) {
            // 읽음 표시는 다음 조회에서 다시 맞춰지므로 Outbox 없이 바로 보낸다 (REL-22)
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("kind", "READ");
            event.put("memberId", me.getId());
            event.put("lastReadMessageId", advanced);
            messagingTemplate.convertAndSend("/topic/rooms/" + id, (Object) event);
        }
        return Map.of("unreadTotal", roomService.unreadTotal(me.getId()));
    }

    @PostMapping("/rooms/{id}/mute")
    public Map<String, Object> mute(@PathVariable("id") Long id, @RequestParam("muted") boolean muted, Principal principal) {
        Member me = me(principal);
        boolean now = roomService.setMuted(id, me.getId(), muted);
        return Map.of("muted", now, "unreadTotal", roomService.unreadTotal(me.getId()));
    }

    @PostMapping("/rooms/{id}/leave")
    public Map<String, Object> leave(@PathVariable("id") Long id, Principal principal) {
        Member me = me(principal);
        roomService.leave(id, me.getId());
        return Map.of("left", true, "unreadTotal", roomService.unreadTotal(me.getId()));
    }

    @GetMapping("/unread")
    public Map<String, Object> unread(Principal principal) {
        return Map.of("unreadTotal", roomService.unreadTotal(me(principal).getId()));
    }

    // 첨부 : 참여자만. 이미지가 아니면 항상 내려받기 (SEC-05)
    @GetMapping("/attachments/{id}")
    public ResponseEntity<Resource> attachment(@PathVariable("id") Long id, Principal principal) {
        ChatAttachment a = messageService.attachment(id, me(principal).getId());
        Path path = fileStore.resolvePrivate(a.getStoredName());
        if (path == null || !Files.isRegularFile(path)) throw new EntityNotFoundException("파일을 찾을 수 없어요.");
        MediaType type = a.isImage() ? MediaTypeFactory.getMediaType(a.getOriginalName()).orElse(MediaType.APPLICATION_OCTET_STREAM)
                : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition cd = (a.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }

    private Member me(Principal principal) {
        return memberService.getMember(principal.getName());
    }
}
