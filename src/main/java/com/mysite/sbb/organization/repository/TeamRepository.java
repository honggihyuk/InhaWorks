package com.mysite.sbb.organization.repository;

import com.mysite.sbb.organization.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findAllByOrderByNameAsc();
    boolean existsByName(String name);
}
