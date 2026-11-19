package com.mysite.sbb.report.dto;

import com.mysite.sbb.common.constant.RepeatCycle;
import com.mysite.sbb.report.constant.ReportCategory;
import com.mysite.sbb.report.entity.ReportField;
import com.mysite.sbb.report.entity.ReportForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// 관리자 : 보고서 양식 만들기 폼 (항목은 같은 순서의 배열로, 결재선은 1·2·3 단계 select)
@Getter
@Setter
@NoArgsConstructor
public class ReportFormForm {

    @NotBlank(message = "양식 이름을 입력해 주세요.")
    @Size(max = 100)
    private String title;

    private String description;

    @NotNull(message = "분류를 골라 주세요.")
    private ReportCategory category = ReportCategory.TOPIC;

    private Long teamId;

    private Long workplaceId;

    @Size(max = 200)
    private String hashtags;

    private RepeatCycle cycle = RepeatCycle.WEEKLY;

    @Size(max = 100)
    private String scheduleText;

    private List<Long> writerIds = new ArrayList<>();

    private List<Long> approverIds = new ArrayList<>();    // [1단계, 2단계, 3단계] (빈 값은 건너뜀)

    private List<String> fieldTypes = new ArrayList<>();
    private List<String> fieldLabels = new ArrayList<>();
    private List<String> fieldOptions = new ArrayList<>();
    private List<String> fieldRequired = new ArrayList<>();
    private List<Long> fieldIds = new ArrayList<>();        // 수정 : 기존 항목 ID (새 항목은 빈 값)

    public static String at(List<String> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    public String typeAt(int i) {
        return at(fieldTypes, i);
    }

    public String optionAt(int i) {
        return at(fieldOptions, i);
    }

    public String requiredAt(int i) {
        return at(fieldRequired, i);
    }

    // 결재선 i 단계 (0부터)
    public Long approverAt(int i) {
        return (approverIds != null && i < approverIds.size()) ? approverIds.get(i) : null;
    }

    public Long fieldIdAt(int i) {
        return (fieldIds != null && i < fieldIds.size()) ? fieldIds.get(i) : null;
    }

    // 수정 화면 : 저장된 양식으로 채우기 (숨긴 항목은 빼고)
    public static ReportFormForm of(ReportForm f) {
        ReportFormForm d = new ReportFormForm();
        d.setTitle(f.getTitle());
        d.setDescription(f.getDescription());
        d.setCategory(f.getCategory());
        d.setTeamId(f.getTeam() == null ? null : f.getTeam().getId());
        d.setWorkplaceId(f.getWorkplace() == null ? null : f.getWorkplace().getId());
        d.setHashtags(f.getHashtags());
        d.setCycle(f.getCycle());
        d.setScheduleText(f.getScheduleText());
        f.getWriters().forEach(w -> d.getWriterIds().add(w.getId()));
        f.getApprovers().forEach(a -> d.getApproverIds().add(a.getApprover().getId()));
        for (ReportField field : f.getActiveFields()) {
            d.getFieldIds().add(field.getId());
            d.getFieldTypes().add(field.getType().name());
            d.getFieldLabels().add(field.getLabel());
            d.getFieldOptions().add(field.getOptions());
            d.getFieldRequired().add(field.isRequired() ? "Y" : "N");
        }
        return d;
    }
}
