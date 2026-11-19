package com.mysite.sbb.batch.entity;

import com.mysite.sbb.batch.constant.JobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [신뢰성] 배치 실행 기록 (job_execution)
//  (jobName, jobKey) 가 unique : 예) ("TODO_ASSIGNMENT_GENERATE", "2026-11-19") 는 한 줄만 존재
//  → "이 날짜 배치를 이미 끝냈는가?"를 DB 로 판단할 수 있어서
//    서버가 꺼져 있던 날짜를 나중에 찾아서 돌리거나(catch-up), 실패한 배치만 골라 다시 돌릴 수 있다.
@Entity
@Table(name = "job_execution",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_name_key", columnNames = {"job_name", "job_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String jobName;                 // 배치 종류

    @Column(nullable = false, length = 50)
    private String jobKey;                  // 대상 (보통 날짜 yyyy-MM-dd)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    private int attempts;                   // 실행한 횟수 (재실행 포함)

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(length = 500)
    private String message;                 // 결과 요약 (예: "할 일 12건 생성")

    @Column(length = 1000)
    private String lastError;

    // 같은 배치를 두 곳에서 동시에 시작하려 하면 한쪽은 낙관적 락 충돌로 시작하지 못한다
    @Version
    private Long version;
}
