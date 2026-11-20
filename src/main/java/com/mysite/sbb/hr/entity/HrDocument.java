package com.mysite.sbb.hr.entity;

import com.mysite.sbb.common.entity.FileRef;
import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 직원 한 명의 인사 서류 한 장 (직원 + 종류마다 하나, 다시 올리면 교체)
//  파일은 공개 주소가 아닌 비공개 폴더(app.private-dir)에 두고, /hr/docs/{id}/file 에서 권한 검사 후 내려준다
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_hr_document_member_type", columnNames = {"member_id", "type_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HrDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HrDocType type;

    @Embedded
    private FileRef file;                   // path = 비공개 폴더 기준 상대 경로

    private LocalDate expiresAt;            // 유효기간 (선택, 예: 보건증)

    @ManyToOne(fetch = FetchType.LAZY)
    private Member uploadedBy;

    private LocalDateTime uploadedAt;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDate.now());
    }
}
