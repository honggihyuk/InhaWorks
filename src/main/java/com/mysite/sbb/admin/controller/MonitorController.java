package com.mysite.sbb.admin.controller;

import com.mysite.sbb.batch.constant.JobStatus;
import com.mysite.sbb.batch.service.BatchJobs;
import com.mysite.sbb.batch.service.JobExecutionService;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.service.OutboxRelay;
import com.mysite.sbb.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.Map;

// 관리자 : 재처리 모니터
//  - Outbox 이벤트 상태별 목록 (PENDING / PROCESSING / DONE / DEAD) + '재처리' (PENDING 으로 되돌리기)
//  - 배치 실행 기록 (job_execution) + '재실행'
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/monitor")
public class MonitorController {

    private final OutboxService outboxService;
    private final OutboxRelay outboxRelay;
    private final JobExecutionService jobExecutionService;
    private final BatchJobs batchJobs;

    @GetMapping
    public String monitor(@RequestParam(value = "status", required = false) OutboxStatus status, Model model) {
        Map<OutboxStatus, Long> counts = new EnumMap<>(OutboxStatus.class);
        for (OutboxStatus s : OutboxStatus.values()) counts.put(s, outboxService.count(s));
        Map<JobStatus, Long> jobCounts = new EnumMap<>(JobStatus.class);
        for (JobStatus s : JobStatus.values()) jobCounts.put(s, jobExecutionService.count(s));
        model.addAttribute("status", status);
        model.addAttribute("statuses", OutboxStatus.values());
        model.addAttribute("counts", counts);
        model.addAttribute("events", outboxService.getEvents(status));
        model.addAttribute("jobCounts", jobCounts);
        model.addAttribute("jobs", jobExecutionService.getRecent());
        return "admin/monitor";
    }

    @PostMapping("/outbox/{id}/retry")
    public String retry(@PathVariable("id") Long id, @RequestParam(value = "status", required = false) String status,
                        RedirectAttributes ra) {
        outboxService.reprocess(id);
        ra.addFlashAttribute("adminMessage", "이벤트 #" + id + " 을 다시 처리하도록 PENDING 으로 되돌렸어요.");
        return "redirect:/admin/monitor" + (status == null || status.isBlank() ? "" : "?status=" + status);
    }

    // 스케줄러를 기다리지 않고 지금 PENDING 이벤트 처리
    @PostMapping("/outbox/poll")
    public String poll(RedirectAttributes ra) {
        ra.addFlashAttribute("adminMessage", "PENDING 이벤트 " + outboxRelay.pollOnce() + "건을 처리했어요.");
        return "redirect:/admin/monitor";
    }

    @PostMapping("/job/{id}/rerun")
    public String rerun(@PathVariable("id") Long id, RedirectAttributes ra) {
        boolean ok = batchJobs.rerun(id);
        ra.addFlashAttribute("adminMessage", ok ? "배치를 다시 실행했어요." : "배치 재실행이 실패했어요. 오류 내용을 확인해 주세요.");
        return "redirect:/admin/monitor";
    }

    @PostMapping("/job/catch-up")
    public String catchUp(RedirectAttributes ra) {
        ra.addFlashAttribute("adminMessage", "누락·실패 배치 " + batchJobs.catchUp() + "건을 실행했어요.");
        return "redirect:/admin/monitor";
    }
}
