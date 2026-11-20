package com.mysite.sbb.attendance.constant;

import lombok.Getter;

// 근무제 종류 (근로기준법의 유연근무제 + 고정 근무제)
//  label : 탭·제목, shortLabel : 직원 목록의 칩, description : 정책 화면 설명, tone : 기본 색
@Getter
public enum WorkPolicyType {
    FIXED("고정 근무제", "고정 근무", "모든 근무일에 같은 시각으로 출퇴근해요", Tone.GREY),
    STAGGERED("시차 출퇴근제", "시차 출퇴근", "하루 근무 시간은 같고 출퇴근 시각만 직원마다 달라요", Tone.BLUE),
    SELECTIVE("선택적 근무제", "선택 근로", "정산 기간의 총 근로시간 안에서 직원이 출퇴근 시각을 골라요", Tone.PURPLE),
    FLEXIBLE("탄력적 근무제", "탄력 근로", "바쁜 주는 늘리고 한가한 주는 줄여 평균 주 40시간을 맞춰요", Tone.TEAL),
    SHIFT("스케줄 근무제", "스케줄 근무", "오픈·미들·마감 같은 근무 유형을 날마다 배정해요", Tone.YELLOW),
    DISCRETIONARY("재량 근무제", "재량 근로", "업무 수행 방식과 시간을 직원 재량에 맡겨요", Tone.GREEN),
    DEEMED("간주 근무제", "간주 근로", "외근처럼 시간을 재기 어려우면 정한 시간을 일한 것으로 봐요", Tone.ORANGE);

    private final String label;
    private final String shortLabel;
    private final String description;
    private final Tone tone;

    WorkPolicyType(String label, String shortLabel, String description, Tone tone) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.description = description;
        this.tone = tone;
    }
}
