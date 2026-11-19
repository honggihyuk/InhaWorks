package com.mysite.sbb.todo.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 할 일 단위 댓글 (관리자 할 일 상세 화면, @아이디 로 멘션하면 알림)
//  직원별 배정 단위 댓글은 TodoComment
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoTaskComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TodoTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member author;

    @Column(nullable = false, length = 1000)
    private String content;

    private LocalDateTime created;
}
