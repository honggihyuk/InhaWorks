package com.mysite.sbb.admin.controller;

import com.mysite.sbb.batch.constant.JobStatus;
import com.mysite.sbb.batch.entity.JobExecution;
import com.mysite.sbb.batch.service.BatchJobs;
import com.mysite.sbb.batch.service.JobExecutionService;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.service.OutboxRelay;
import com.mysite.sbb.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

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

    // outboxPage, jobPage : 두 표의 페이지 번호 (0부터, 서로 독립)
    @GetMapping
    public String monitor(@RequestParam(value = "status", required = false) OutboxStatus status,
                          @RequestParam(value = "type", required = false) String type,
                          @RequestParam(value = "outboxPage", defaultValue = "0") int outboxPage,
                          @RequestParam(value = "jobPage", defaultValue = "0") int jobPage, Model model) {
        Map<OutboxStatus, Long> counts = new EnumMap<>(OutboxStatus.class);
        for (OutboxStatus s : OutboxStatus.values()) counts.put(s, outboxService.count(s));
        Map<JobStatus, Long> jobCounts = new EnumMap<>(JobStatus.class);
        for (JobStatus s : JobStatus.values()) jobCounts.put(s, jobExecutionService.count(s));
        Page<OutboxEvent> eventPage = outboxService.getEventPage(status, type, outboxPage);
        Page<JobExecution> jobPageData = jobExecutionService.getPage(jobPage);
        model.addAttribute("status", status);
        model.addAttribute("statuses", OutboxStatus.values());
        model.addAttribute("counts", counts);
        model.addAttribute("type", type == null || type.isBlank() ? null : type);
        model.addAttribute("eventTypes", outboxService.getEventTypes());
        model.addAttribute("eventPage", eventPage);
        model.addAttribute("events", eventPage.getContent());
        model.addAttribute("outboxPage", eventPage.getNumber());
        model.addAttribute("jobCounts", jobCounts);
        model.addAttribute("jobPageData", jobPageData);
        model.addAttribute("jobs", jobPageData.getContent());
        model.addAttribute("jobPage", jobPageData.getNumber());
        return "admin/monitor";
    }

    @PostMapping("/outbox/{id}/retry")
    public String retry(@PathVariable("id") Long id, @RequestParam(value = "status", required = false) OutboxStatus status,
                        @RequestParam(value = "type", required = false) String type,
                        @RequestParam(value = "outboxPage", defaultValue = "0") int outboxPage,
                        @RequestParam(value = "jobPage", defaultValue = "0") int jobPage, RedirectAttributes ra) {
        outboxService.reprocess(id);
        ra.addFlashAttribute("adminMessage", "이벤트 #" + id + " 을 다시 처리하도록 PENDING 으로 되돌렸어요.");
        return back(status, type, outboxPage, jobPage, "outbox");
    }

    // 스케줄러를 기다리지 않고 지금 PENDING 이벤트 처리
    @PostMapping("/outbox/poll")
    public String poll(@RequestParam(value = "status", required = false) OutboxStatus status,
                       @RequestParam(value = "type", required = false) String type,
                       @RequestParam(value = "outboxPage", defaultValue = "0") int outboxPage,
                       @RequestParam(value = "jobPage", defaultValue = "0") int jobPage, RedirectAttributes ra) {
        ra.addFlashAttribute("adminMessage", "PENDING 이벤트 " + outboxRelay.pollOnce() + "건을 처리했어요.");
        return back(status, type, outboxPage, jobPage, "outbox");
    }

    @PostMapping("/job/{id}/rerun")
    public String rerun(@PathVariable("id") Long id, @RequestParam(value = "status", required = false) OutboxStatus status,
                        @RequestParam(value = "type", required = false) String type,
                        @RequestParam(value = "outboxPage", defaultValue = "0") int outboxPage,
                        @RequestParam(value = "jobPage", defaultValue = "0") int jobPage, RedirectAttributes ra) {
        boolean ok = batchJobs.rerun(id);
        ra.addFlashAttribute("adminMessage", ok ? "배치를 다시 실행했어요." : "배치 재실행이 실패했어요. 오류 내용을 확인해 주세요.");
        return back(status, type, outboxPage, jobPage, "jobs");
    }

    @PostMapping("/job/catch-up")
    public String catchUp(@RequestParam(value = "status", required = false) OutboxStatus status,
                          @RequestParam(value = "type", required = false) String type,
                          @RequestParam(value = "outboxPage", defaultValue = "0") int outboxPage,
                          @RequestParam(value = "jobPage", defaultValue = "0") int jobPage, RedirectAttributes ra) {
        ra.addFlashAttribute("adminMessage", "누락·실패 배치 " + batchJobs.catchUp() + "건을 실행했어요.");
        return back(status, type, outboxPage, jobPage, "jobs");
    }

    // 처리 뒤 보던 필터·페이지·표로 돌아간다
    private static String back(OutboxStatus status, String type, int outboxPage, int jobPage, String anchor) {
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/monitor")
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .queryParamIfPresent("type", Optional.ofNullable(type).filter(t -> !t.isBlank()))
                .queryParam("outboxPage", Math.max(outboxPage, 0))
                .queryParam("jobPage", Math.max(jobPage, 0))
                .fragment(anchor)
                .encode().build().toUriString();
    }
}
