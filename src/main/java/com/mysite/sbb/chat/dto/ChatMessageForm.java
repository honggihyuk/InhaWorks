package com.mysite.sbb.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 메시지 전송 폼 (파일은 컨트롤러에서 따로 받는다)
@Getter
@Setter
public class ChatMessageForm {

    @NotBlank(message = "clientMessageId 가 필요해요.")
    @Size(max = 64, message = "clientMessageId 가 너무 길어요.")
    private String clientMessageId;

    @Size(max = 2000, message = "메시지는 2,000자까지 보낼 수 있어요.")
    private String content;

    private List<Long> mentionIds;      // 자동완성으로 고른 사람
}
