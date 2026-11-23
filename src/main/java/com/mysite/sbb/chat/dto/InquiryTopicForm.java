package com.mysite.sbb.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class InquiryTopicForm {

    private Long id;

    @NotBlank(message = "주제 이름을 입력해 주세요.")
    @Size(max = 50, message = "주제 이름은 50자까지예요.")
    private String name;

    @Size(max = 200, message = "설명은 200자까지예요.")
    private String description;

    @Size(max = 4, message = "아이콘은 이모지 하나만 넣어 주세요.")
    private String icon;

    @NotEmpty(message = "담당자를 한 명 이상 골라 주세요.")
    private List<Long> handlerIds = new ArrayList<>();

    private boolean active = true;
}
