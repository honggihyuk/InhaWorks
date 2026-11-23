package com.mysite.sbb.chat.entity;

import com.mysite.sbb.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

// 문의 주제 : 이름, 설명, 아이콘, 담당자(관리자·매니저 여러 명), 사용 여부
@Entity
@Table(name = "inquiry_topic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(length = 10)
    private String icon;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @ManyToMany
    @JoinTable(name = "inquiry_topic_handler",
            joinColumns = @JoinColumn(name = "topic_id"), inverseJoinColumns = @JoinColumn(name = "member_id"))
    private Set<Member> handlers = new LinkedHashSet<>();

    private LocalDateTime created;
}
