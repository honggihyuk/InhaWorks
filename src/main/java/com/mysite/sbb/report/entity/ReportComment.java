package com.mysite.sbb.report.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member author;

    @Column(nullable = false, length = 1000)
    private String content;

    private LocalDateTime created;
}
