package com.mysite.sbb.report.entity;

import com.mysite.sbb.common.entity.FileRef;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.List;

// 보고서 항목 하나의 답
@Entity
@Getter
@Setter
@ToString(exclude = {"report", "field"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReportField field;

    @Column(name = "answer_value", columnDefinition = "TEXT")
    private String value;

    @Embedded
    private FileRef file;

    public List<String> getValues() {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.asList(value.split("\\|"));
    }

    public boolean hasFile() {
        return file != null && file.getPath() != null;
    }

    // 엑셀 칸에 넣을 글자
    public String toCellText() {
        if (hasFile()) return file.getOriginalName() + " (" + file.getPath() + ")";
        if (value == null) return "";
        return switch (value) {
            case "Y" -> "예";
            case "N" -> "아니오";
            default -> value.replace("|", ", ");
        };
    }
}
