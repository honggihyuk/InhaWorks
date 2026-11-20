package com.mysite.sbb.hr.entity;

import com.mysite.sbb.hr.constant.DocScope;
import jakarta.persistence.*;
import lombok.*;

// 인사 서류 종류 (예: 통장 사본, 주민등록 등본, 근로계약서) : 필수 여부, 열람 권한, 직원 제출 가능 여부
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HrDocType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    private boolean required;               // 필수 서류 (없으면 직원 목록·허브에 경고)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocScope scope;

    private boolean employeeUpload;         // 직원이 내 정보에서 직접 제출할 수 있다

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;          // 끄면 목록에서 빠진다 (이미 올린 서류는 남는다)

    private int sortOrder;
}
