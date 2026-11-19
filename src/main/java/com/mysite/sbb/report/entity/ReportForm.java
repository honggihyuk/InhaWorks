package com.mysite.sbb.report.entity;

import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.organization.entity.Team;
import com.mysite.sbb.organization.entity.Workplace;
import com.mysite.sbb.report.constant.ReportCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

// 보고서 양식 (관리자가 만든다)
//  - 항목 : 예/아니오, 체크박스, 이미지, 서명, 객관식, 주관식, 문서
//  - 누가 쓰나 : 고른 직원 + 팀 + 매장 (아무것도 안 고르면 전 직원)
//  - 언제 쓰나 : 주기(매주 등) + 안내 글 (예: "매주 월요일 오전")
//  - 결재선   : 1단계 → 2단계 → ... 순서대로 승인
@Entity
@Getter
@Setter
@ToString(of = {"id", "title"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReportCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;                      // 작성 대상 팀 (선택)

    @ManyToOne(fetch = FetchType.LAZY)
    private Workplace workplace;            // 작성 대상 매장 (선택)

    @Column(length = 200)
    private String hashtags;                // "#주간보고 #영업팀"

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", length = 20)
    private RepeatCycle cycle;

    @Column(length = 100)
    private String scheduleText;            // "매주 월요일"

    @Builder.Default
    @ManyToMany
    @JoinTable(name = "report_form_writer", joinColumns = @JoinColumn(name = "form_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id"))
    private Set<Member> writers = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<ReportField> fields = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("step asc")
    private List<ReportApprover> approvers = new ArrayList<>();

    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member createdBy;

    private LocalDateTime created;

    // 지금 쓰는 항목 (수정 때 지워서 숨긴 항목은 빼고)
    public List<ReportField> getActiveFields() {
        return fields.stream().filter(f -> !f.isArchived()).toList();
    }

    public void addField(ReportField field) {
        field.setForm(this);
        field.setSortOrder(fields.size());
        fields.add(field);
    }

    public void addApprover(Member approver) {
        approvers.add(ReportApprover.builder().form(this).step(approvers.size() + 1).approver(approver).build());
    }

    // "#주간보고 #영업팀" → [#주간보고, #영업팀]
    public List<String> getTagList() {
        if (hashtags == null || hashtags.isBlank()) return List.of();
        return Arrays.stream(hashtags.split("[\\s,]+")).filter(s -> !s.isBlank())
                .map(s -> s.startsWith("#") ? s : "#" + s).distinct().toList();
    }

    // 이 직원이 쓸 수 있는 양식인가
    public boolean canWrite(Member m) {
        if (!active) return false;
        if (writers.isEmpty() && team == null && workplace == null) return true;    // 전 직원
        if (writers.stream().anyMatch(w -> w.getId().equals(m.getId()))) return true;
        if (team != null && m.getTeam() != null && team.getId().equals(m.getTeam().getId())) return true;
        return workplace != null && m.getWorkplace() != null && workplace.getId().equals(m.getWorkplace().getId());
    }

    public String getWriterText() {
        List<String> names = new ArrayList<>();
        if (team != null) names.add(team.getName());
        if (workplace != null) names.add(workplace.getName());
        writers.forEach(w -> names.add(w.getDisplayName()));
        return names.isEmpty() ? "전 직원" : String.join(" · ", names);
    }

    public String getApprovalLineText() {
        if (approvers.isEmpty()) return "결재 없음";
        return String.join(" → ", approvers.stream().map(a -> a.getApprover().getDisplayName()).toList());
    }
}
