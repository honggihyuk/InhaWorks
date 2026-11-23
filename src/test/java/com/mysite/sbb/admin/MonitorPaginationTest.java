package com.mysite.sbb.admin;

import com.mysite.sbb.batch.constant.JobStatus;
import com.mysite.sbb.batch.entity.JobExecution;
import com.mysite.sbb.batch.repository.JobExecutionRepository;
import com.mysite.sbb.outbox.constant.OutboxStatus;
import com.mysite.sbb.outbox.entity.OutboxEvent;
import com.mysite.sbb.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 재처리 모니터 : 이벤트와 배치 기록을 20건씩, 서로 독립적으로 넘긴다
@SpringBootTest(properties = {"app.upload-dir=build/test-uploads-monitor", "app.private-dir=build/test-private-monitor"})
class MonitorPaginationTest {

    @Autowired WebApplicationContext context;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired JobExecutionRepository jobRepository;

    MockMvc mvc;
    String type;
    List<Long> eventIds = new ArrayList<>();
    Long deadId, jobId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        type = "PAGE_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 45; i++) {
            OutboxEvent e = outboxRepository.save(OutboxEvent.builder().eventType(type).aggregateType("Test").aggregateId((long) i)
                    .payload("{}").status(i == 0 ? OutboxStatus.DEAD : OutboxStatus.DONE).maxRetries(3).nextRetryAt(now).created(now).build());
            eventIds.add(e.getId());
        }
        deadId = eventIds.get(0);
        for (int i = 0; i < 25; i++) {
            jobId = jobRepository.save(JobExecution.builder().jobName("PAGE_TEST").jobKey(type + "-" + i)
                    .status(JobStatus.COMPLETED).attempts(1).startedAt(now).finishedAt(now).build()).getId();
        }
    }

    @Test
    @DisplayName("이벤트는 20건씩 최신순, 100건 제한 없이 마지막 페이지까지 본다")
    void eventsArePagedNewestFirst() throws Exception {
        Page<OutboxEvent> first = events(monitor("type", type));
        assertThat(first.getContent()).hasSize(20);
        assertThat(first.getTotalElements()).isEqualTo(45);
        assertThat(first.getContent().get(0).getId()).isEqualTo(eventIds.get(44));

        Page<OutboxEvent> last = events(monitor("type", type, "outboxPage", "2"));
        assertThat(last.getContent()).hasSize(5);
        assertThat(last.getContent().get(4).getId()).isEqualTo(deadId);
    }

    @Test
    @DisplayName("이벤트 페이지와 배치 페이지는 서로 독립이고, 화면 링크도 상대 페이지를 유지한다")
    void pagesAreIndependent() throws Exception {
        MvcResult r = monitor("type", type, "outboxPage", "2", "jobPage", "1");
        assertThat(events(r).getNumber()).isEqualTo(2);
        assertThat(jobs(r).getNumber()).isEqualTo(1);
        String html = r.getResponse().getContentAsString().replace("&amp;", "&");
        assertThat(html).contains("outboxPage=1&jobPage=1#outbox");   // 이벤트만 이동
        assertThat(html).contains("outboxPage=2&jobPage=0#jobs");     // 배치만 이동
    }

    @Test
    @DisplayName("상태 필터가 페이지에 적용되고, 범위를 벗어난 페이지는 마지막 페이지로")
    void filterAndClamp() throws Exception {
        Page<OutboxEvent> dead = events(monitor("type", type, "status", "DEAD"));
        assertThat(dead.getTotalElements()).isEqualTo(1);
        assertThat(events(monitor("type", type, "outboxPage", "999")).getNumber()).isEqualTo(2);
        assertThat(events(monitor("type", type, "outboxPage", "-3")).getNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("재처리·재실행 뒤에도 필터와 두 페이지 번호를 유지한다")
    void redirectsKeepState() throws Exception {
        String retry = mvc.perform(post("/admin/monitor/outbox/" + deadId + "/retry").with(csrf()).with(admin())
                        .param("status", "DEAD").param("type", type).param("outboxPage", "2").param("jobPage", "1"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(retry).contains("status=DEAD").contains("type=" + type).contains("outboxPage=2").contains("jobPage=1").endsWith("#outbox");

        // 재실행은 실제 배치 이름과 날짜 키가 필요하다 (지난 날짜라 만들어지는 할 일은 없다)
        Long realJob = jobRepository.save(JobExecution.builder().jobName("TODO_ASSIGNMENT_GENERATE").jobKey("2000-01-01")
                .status(JobStatus.FAILED).attempts(1).build()).getId();
        String rerun = mvc.perform(post("/admin/monitor/job/" + realJob + "/rerun").with(csrf()).with(admin())
                        .param("type", type).param("outboxPage", "2").param("jobPage", "1"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(rerun).contains("type=" + type).contains("outboxPage=2").contains("jobPage=1").endsWith("#jobs");
    }

    private MvcResult monitor(String... params) throws Exception {
        var request = get("/admin/monitor").with(admin());
        for (int i = 0; i < params.length; i += 2) request = request.param(params[i], params[i + 1]);
        return mvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    @SuppressWarnings("unchecked")
    private static Page<OutboxEvent> events(MvcResult r) {
        return (Page<OutboxEvent>) r.getModelAndView().getModel().get("eventPage");
    }

    @SuppressWarnings("unchecked")
    private static Page<JobExecution> jobs(MvcResult r) {
        return (Page<JobExecution>) r.getModelAndView().getModel().get("jobPageData");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
