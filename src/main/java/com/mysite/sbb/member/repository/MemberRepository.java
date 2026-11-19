package com.mysite.sbb.member.repository;

import com.mysite.sbb.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 로그인할 때 사용자 ID 로 직원 조회
    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);

    // 관리자 : 직원 수정할 때 이메일 중복 확인
    Optional<Member> findByEmail(String email);

    // 재직 중인 직원 (이름순) : 할 일 대상자, 결재자 고르기
    List<Member> findByActiveTrueOrderByNameAsc();

    // 할 일 대상 : 근무지(매장) 단위 배정
    List<Member> findByActiveTrueAndWorkplaceIdIn(Collection<Long> workplaceIds);

    // @멘션 : 아이디로 찾기
    List<Member> findByUsernameIn(Collection<String> usernames);

    long countByTeamId(Long teamId);

    long countByWorkplaceId(Long workplaceId);
}
