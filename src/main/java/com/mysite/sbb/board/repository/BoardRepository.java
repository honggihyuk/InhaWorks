package com.mysite.sbb.board.repository;

import com.mysite.sbb.board.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {
    List<Board> findAllByOrderBySortOrderAscIdAsc();
}
