package com.mysite.sbb.organization.service;

import com.mysite.sbb.chat.service.ChatSyncService;
import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.repository.MemberRepository;
import com.mysite.sbb.organization.entity.Team;
import com.mysite.sbb.organization.entity.Workplace;
import com.mysite.sbb.organization.repository.TeamRepository;
import com.mysite.sbb.organization.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 조직 : 팀 / 근무지(매장) 관리
@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationService {

    private final TeamRepository teamRepository;
    private final WorkplaceRepository workplaceRepository;
    private final MemberRepository memberRepository;
    private final ChatSyncService chatSyncService;

    @Transactional(readOnly = true)
    public List<Team> getTeams() {
        return teamRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Workplace> getWorkplaces() {
        return workplaceRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Team getTeam(Long id) {
        return id == null ? null : teamRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Workplace getWorkplace(Long id) {
        return id == null ? null : workplaceRepository.findById(id).orElse(null);
    }

    public void createTeam(String name, String description) {
        if (name == null || name.isBlank()) throw new BusinessException("팀 이름을 입력해 주세요.");
        if (teamRepository.existsByName(name.strip())) throw new BusinessException("이미 있는 팀이에요.");
        chatSyncService.teamRoom(teamRepository.save(Team.builder().name(name.strip()).description(description).build()));   // 팀 채널 자동 생성
    }

    public void createWorkplace(String name, String address) {
        if (name == null || name.isBlank()) throw new BusinessException("근무지 이름을 입력해 주세요.");
        if (workplaceRepository.existsByName(name.strip())) throw new BusinessException("이미 있는 근무지예요.");
        chatSyncService.workplaceRoom(workplaceRepository.save(Workplace.builder().name(name.strip()).address(address).build()));   // 매장 채널 자동 생성
    }

    // 소속 직원이 있으면 지우지 않는다
    public void deleteTeam(Long id) {
        if (memberRepository.countByTeamId(id) > 0) throw new BusinessException("소속 직원이 있는 팀은 지울 수 없어요.");
        chatSyncService.archiveTeamRoom(id);
        teamRepository.deleteById(id);
    }

    public void deleteWorkplace(Long id) {
        if (memberRepository.countByWorkplaceId(id) > 0) throw new BusinessException("소속 직원이 있는 근무지는 지울 수 없어요.");
        chatSyncService.archiveWorkplaceRoom(id);
        workplaceRepository.deleteById(id);
    }
}
