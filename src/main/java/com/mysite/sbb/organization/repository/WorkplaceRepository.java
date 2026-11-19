package com.mysite.sbb.organization.repository;

import com.mysite.sbb.organization.entity.Workplace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {
    List<Workplace> findAllByOrderByNameAsc();
    boolean existsByName(String name);
}
