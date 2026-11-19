package com.mysite.sbb.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 업로드한 파일 한 개 (원래 이름 + 저장된 주소 + 크기)
// 여러 엔티티(할 일 가이드 첨부, 게시글 첨부, 보고서 답변 ...)에서 @Embeddable 로 같이 쓴다.
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FileRef {

    @Column(length = 255)
    private String originalName;    // 사용자가 올린 파일 이름 (예: 매장점검표.pdf)

    @Column(length = 255)
    private String path;            // 화면에서 쓰는 주소 (/uploads/uuid.pdf)

    private Long size;              // 바이트

    public boolean isImage() {
        if (path == null) return false;
        String p = path.toLowerCase();
        return p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".gif") || p.endsWith(".webp");
    }
}
