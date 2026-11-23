package com.mysite.sbb.member.service;

import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    // 댓글의 @아이디 (예: "@emp1 확인 부탁드려요")
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_.\\-]{2,30})");

    private final MemberRepository memberRepository;
    private final com.mysite.sbb.common.file.FileStore fileStore;

    // 로그인한 사용자 아이디(Principal.getName())로 직원 찾기
    public Member getMember(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
    }

    public Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));
    }

    // 관리자 화면 : 전체 직원 목록 (최근 등록순)
    public List<Member> getMemberList() {
        return memberRepository.findAll(Sort.by(Sort.Order.desc("id")));
    }

    // 재직 중인 직원 (대상자·검수자·결재자 선택 목록)
    public List<Member> getActiveMembers() {
        return memberRepository.findByActiveTrueOrderByNameAsc();
    }

    // 소속·근무제까지 함께 (채팅 상대 고르기)
    public List<Member> getActiveMembersWithOrg() {
        return memberRepository.findActiveWithOrg();
    }

    public List<Member> getMembers(List<Long> ids) {
        return (ids == null || ids.isEmpty()) ? List.of() : memberRepository.findAllById(ids);
    }

    // 댓글 본문에서 @아이디 를 찾아 직원 목록으로
    public List<Member> findMentioned(String content) {
        if (content == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        Matcher m = MENTION.matcher(content);
        while (m.find()) names.add(m.group(1));
        return names.isEmpty() ? List.of() : memberRepository.findByUsernameIn(names);
    }

    public List<Member> getAdmins() {
        return memberRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(m -> m.getRole() == MemberRole.ADMIN).toList();
    }

    // 내 정보 : 프로필 사진 변경 / 삭제 (path 가 null 이면 삭제)
    @Transactional
    // 예전 사진은 커밋된 뒤에 지운다 (롤백되면 예전 사진을 계속 써야 하므로)
    public void updatePhoto(String username, String path) {
        Member m = getMember(username);
        String old = m.getPhotoPath();
        m.setPhotoPath(path);
        if (old != null && !old.equals(path)) fileStore.deleteAfterCommit(old);
    }

    // 빈 칸으로 온 선택 항목은 null 로 저장한다 (이메일은 unique 라서 빈 문자열이 여러 개면 겹친다)
    public static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
