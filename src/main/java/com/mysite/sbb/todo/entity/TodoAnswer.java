package com.mysite.sbb.todo.entity;

import com.mysite.sbb.common.entity.FileRef;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.List;

// 항목 하나에 대한 답 : 글자 값(예/아니오, 체크, 선택지, 주관식) 또는 파일(사진, 서명 PNG)
@Entity
@Getter
@Setter
@ToString(exclude = {"assignment", "item"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TodoAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TodoItem item;

    @Column(name = "answer_value", length = 2000)
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
}
