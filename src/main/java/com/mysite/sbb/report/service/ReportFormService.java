package com.mysite.sbb.report.service;

import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.organization.service.OrganizationService;
import com.mysite.sbb.report.constant.ReportCategory;
import com.mysite.sbb.report.constant.ReportFieldType;
import com.mysite.sbb.report.dto.ReportFormForm;
import com.mysite.sbb.report.entity.ReportField;
import com.mysite.sbb.report.entity.ReportForm;
import com.mysite.sbb.report.repository.ReportAnswerRepository;
import com.mysite.sbb.report.repository.ReportFormRepository;
import com.mysite.sbb.report.repository.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 보고서 양식 : 관리자 양식 만들기(폼 빌더), 직원이 쓸 수 있는 양식 목록
@Service
@RequiredArgsConstructor
@Transactional
public class ReportFormService {

    private final ReportFormRepository reportFormRepository;
    private final ReportRepository reportRepository;
    private final MemberService memberService;
    private final OrganizationService organizationService;
    private final ReportAnswerRepository reportAnswerRepository;

    @Transactional(readOnly = true)
    public ReportForm get(Long id) {
        return reportFormRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("보고서 양식을 찾을 수 없어요."));
    }

    @Transactional(readOnly = true)
    public List<ReportForm> getAll() {
        return reportFormRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public long countReports(Long formId) {
        return reportRepository.countByFormId(formId);
    }

    // 직원 : 내가 쓸 수 있는 양식 (분류 / 해시태그로 거르기)
    @Transactional(readOnly = true)
    public List<ReportForm> getWritable(Member me, ReportCategory category, String tag) {
        String t = (tag == null || tag.isBlank()) ? null : (tag.startsWith("#") ? tag : "#" + tag);
        return reportFormRepository.findByActiveTrueOrderByIdDesc().stream()
                .filter(f -> f.canWrite(me))
                .filter(f -> category == null || f.getCategory() == category)
                .filter(f -> t == null || f.getTagList().contains(t))
                .toList();
    }

    public ReportForm create(ReportFormForm dto, String adminUsername) {
        ReportForm form = ReportForm.builder()
                .title(dto.getTitle().strip())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .team(organizationService.getTeam(dto.getTeamId()))
                .workplace(organizationService.getWorkplace(dto.getWorkplaceId()))
                .hashtags(MemberService.emptyToNull(dto.getHashtags()))
                .cycle(dto.getCycle())
                .scheduleText(MemberService.emptyToNull(dto.getScheduleText()))
                .createdBy(memberService.getMember(adminUsername))
                .created(LocalDateTime.now())
                .build();
        form.getWriters().addAll(memberService.getMembers(dto.getWriterIds()));

        for (int i = 0; i < dto.getFieldLabels().size(); i++) {
            String label = ReportFormForm.at(dto.getFieldLabels(), i);
            if (label == null || label.isBlank()) continue;
            ReportFieldType type = ReportFieldType.valueOf(ReportFormForm.at(dto.getFieldTypes(), i));
            String options = ReportFormForm.at(dto.getFieldOptions(), i);
            if (type == ReportFieldType.CHOICE && (options == null || options.isBlank())) {
                throw new BusinessException("객관식 항목 '" + label + "' 의 선택지를 쉼표로 입력해 주세요.");
            }
            form.addField(ReportField.builder()
                    .type(type)
                    .label(label.strip())
                    .options(type == ReportFieldType.CHOICE ? options : null)
                    .required("Y".equals(ReportFormForm.at(dto.getFieldRequired(), i)))
                    .build());
        }
        if (form.getFields().isEmpty()) {
            throw new BusinessException("항목을 하나 이상 넣어 주세요.");
        }

        // 결재선 : 비어 있는 단계는 건너뛰고, 같은 사람을 두 번 넣지 않게
        Set<Long> seen = new HashSet<>();
        for (Long approverId : dto.getApproverIds()) {
            if (approverId == null) continue;
            if (!seen.add(approverId)) throw new BusinessException("결재선에 같은 사람이 두 번 들어갔어요.");
            form.addApprover(memberService.getMember(approverId));
        }
        return reportFormRepository.save(form);
    }

    // ---------- 수정 ----------
    //  항목 : fieldIds[i] 가 있으면 기존 항목 고치기(종류는 그대로), 없으면 새 항목.
    //         폼에서 빠진 기존 항목은 낸 답이 있으면 숨기고(archived), 없으면 지운다 → 이미 제출된 보고서·엑셀은 그대로.
    //  결재선 : 새로 바꾼 결재선은 앞으로 제출하는 보고서부터 적용 (이미 제출된 보고서는 제출 때 결재선을 복사해 둠)
    public ReportForm update(Long id, ReportFormForm dto) {
        ReportForm form = get(id);
        form.setTitle(dto.getTitle().strip());
        form.setDescription(dto.getDescription());
        form.setCategory(dto.getCategory());
        form.setTeam(organizationService.getTeam(dto.getTeamId()));
        form.setWorkplace(organizationService.getWorkplace(dto.getWorkplaceId()));
        form.setHashtags(MemberService.emptyToNull(dto.getHashtags()));
        form.setCycle(dto.getCycle());
        form.setScheduleText(MemberService.emptyToNull(dto.getScheduleText()));
        form.getWriters().clear();
        form.getWriters().addAll(memberService.getMembers(dto.getWriterIds()));

        Map<Long, ReportField> existing = new HashMap<>();
        form.getFields().forEach(f -> existing.put(f.getId(), f));
        Set<Long> kept = new HashSet<>();
        List<ReportField> ordered = new ArrayList<>();
        for (int i = 0; i < dto.getFieldLabels().size(); i++) {
            String label = ReportFormForm.at(dto.getFieldLabels(), i);
            if (label == null || label.isBlank()) continue;
            Long fieldId = dto.fieldIdAt(i);
            ReportField field = fieldId == null ? null : existing.get(fieldId);
            ReportFieldType type = field != null ? field.getType() : ReportFieldType.valueOf(ReportFormForm.at(dto.getFieldTypes(), i));
            String options = ReportFormForm.at(dto.getFieldOptions(), i);
            if (type == ReportFieldType.CHOICE && (options == null || options.isBlank())) {
                throw new BusinessException("객관식 항목 '" + label + "' 의 선택지를 쉼표로 입력해 주세요.");
            }
            if (field == null) {
                field = ReportField.builder().type(type).build();
                form.addField(field);
            } else {
                kept.add(field.getId());
            }
            field.setLabel(label.strip());
            field.setOptions(type == ReportFieldType.CHOICE ? options : null);
            field.setRequired("Y".equals(ReportFormForm.at(dto.getFieldRequired(), i)));
            field.setArchived(false);
            ordered.add(field);
        }
        if (ordered.isEmpty()) {
            throw new BusinessException("항목을 하나 이상 넣어 주세요.");
        }
        for (ReportField old : existing.values()) {
            if (kept.contains(old.getId())) continue;
            if (reportAnswerRepository.existsByFieldId(old.getId())) {
                old.setArchived(true);
            } else {
                form.getFields().remove(old);
            }
        }
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setSortOrder(i);
        int tail = ordered.size();
        for (ReportField f : form.getFields()) {
            if (f.isArchived()) f.setSortOrder(tail++);
        }

        form.getApprovers().clear();
        reportFormRepository.flush();           // 지운 결재선 행을 먼저 반영
        Set<Long> seen = new HashSet<>();
        for (Long approverId : dto.getApproverIds()) {
            if (approverId == null) continue;
            if (!seen.add(approverId)) throw new BusinessException("결재선에 같은 사람이 두 번 들어갔어요.");
            form.addApprover(memberService.getMember(approverId));
        }
        return form;
    }

    public void toggleActive(Long id) {
        ReportForm form = get(id);
        form.setActive(!form.isActive());
    }
}
