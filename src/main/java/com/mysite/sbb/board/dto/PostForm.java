package com.mysite.sbb.board.dto;

import com.mysite.sbb.board.entity.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// 게시글 쓰기 / 고치기 폼
@Getter
@Setter
@NoArgsConstructor
public class PostForm {

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 150)
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    private boolean pinned;                 // 관리자만

    private String submitToken = UUID.randomUUID().toString();

    public static PostForm of(Post p) {
        PostForm f = new PostForm();
        f.setTitle(p.getTitle());
        f.setContent(p.getContent());
        f.setPinned(p.isPinned());
        return f;
    }
}
