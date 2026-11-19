package com.mysite.sbb.organization.entity;

import jakarta.persistence.*;
import lombok.*;

// 팀 (예: 운영팀, 영업팀)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;
}
