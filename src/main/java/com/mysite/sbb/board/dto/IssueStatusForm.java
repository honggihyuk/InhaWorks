package com.mysite.sbb.board.dto;

import com.mysite.sbb.board.constant.IssueStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 이슈 상태 칩 메뉴 : 새 상태 + 메모 (해결 완료로 바꿀 때는 해결 내용이 필수 → 서비스에서 확인)
@Getter
@Setter
public class IssueStatusForm {

    @NotNull(message = "상태를 골라 주세요.")
    private IssueStatus status;

    @Size(max = 200, message = "메모는 200자까지 쓸 수 있어요.")
    private String memo;

    @Size(max = 5000, message = "해결 내용은 5000자까지 쓸 수 있어요.")
    private String resolution;
}
