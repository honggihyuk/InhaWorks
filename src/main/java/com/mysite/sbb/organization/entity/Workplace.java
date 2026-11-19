package com.mysite.sbb.organization.entity;

import jakarta.persistence.*;
import lombok.*;

// 근무지 / 매장 (예: 인하점, 송도점)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workplace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String address;
}
