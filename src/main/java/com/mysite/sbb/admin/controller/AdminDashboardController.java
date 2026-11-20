package com.mysite.sbb.admin.controller;

import com.mysite.sbb.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

// 관리자 메인 : 대시보드 (오늘 할 일 완료율, 검수·결재 대기, 근무지별 현황, 이번 주 제출, 확인 필요)
@Controller
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final com.mysite.sbb.hr.service.HrService hrService;

    @GetMapping({"/admin", "/admin/"})
    public String dashboard(Model model) {
        model.addAttribute("dash", adminDashboardService.build(LocalDate.now()));
        model.addAttribute("hrAlerts", hrService.getAlerts());       // 계약 만료 · 복직 예정 (30일 안)
        return "admin/dashboard";
    }
}
