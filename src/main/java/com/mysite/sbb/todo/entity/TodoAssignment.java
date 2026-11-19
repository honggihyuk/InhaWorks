package com.mysite.sbb.todo.entity;

import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.todo.constant.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 직원 한 명의 하루 할 일 (task + member + workDate 는 하나만 → unique 제약)
// [신뢰성] unique 제약 덕분에 생성 배치를 여러 번 돌려도 같은 날 같은 사람에게 두 번 생기지 않는다 (멱등)
// [신뢰성] @Version 낙관적 락 : 두 검수자가 동시에 검수하면 늦은 쪽은 충돌 → @Retryable 로 다시 읽고 "이미 처리됨" 안내
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_assignment_task_member_date",
        columnNames = {"task_id", "member_id", "work_date"}))
@Getter
@Setter
@ToString(of = {"id", "workDate", "status"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TodoTask task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @Column(nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status;

    private LocalDateTime submittedAt;

    private int submitCount;                // 제출 횟수 (재작업 후 다시 내면 2, 3 ...)

    // [신뢰성] 중복 제출 방지 : 화면을 열 때 만든 UUID. 같은 값으로 두 번 오면 (더블클릭) 두 번째는 무시
    @Column(unique = true, length = 36)
    private String lastSubmitToken;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String reworkReason;            // 재작업 요청 사유

    @Version
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TodoAnswer> answers = new ArrayList<>();

    public Optional<TodoAnswer> answerOf(TodoItem item) {
        return answers.stream().filter(a -> a.getItem().getId().equals(item.getId())).findFirst();
    }

    // 화면용 : 항목 id → 답 (없으면 null)
    public TodoAnswer getAnswer(Long itemId) {
        return answers.stream().filter(a -> a.getItem().getId().equals(itemId)).findFirst().orElse(null);
    }
}
