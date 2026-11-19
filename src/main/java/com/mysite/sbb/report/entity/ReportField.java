package com.mysite.sbb.report.entity;

import com.mysite.sbb.report.constant.ReportFieldType;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.List;

// 보고서 양식의 항목 하나
@Entity
@Getter
@Setter
@ToString(exclude = "form")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReportForm form;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private ReportFieldType type;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 500)
    private String options;                 // 객관식 "A,B,C"

    private boolean required;

    private int sortOrder;

    // 수정할 때 지운 항목 : 이미 낸 답이 있으면 지우지 않고 숨긴다 (지난 답 보기 · 엑셀에는 남는다)
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable = false)
    private boolean archived;

    public List<String> getOptionList() {
        if (options == null || options.isBlank()) return List.of();
        return Arrays.stream(options.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
