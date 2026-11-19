# InhaWorks 아키텍처

> 사내 업무 관리 웹앱 **InhaWorks** 의 구조, 업무 흐름, 그리고 "실패해도 일이 사라지지 않게" 만든 신뢰성 장치를 그림으로 정리한 문서예요.
> 그림은 Mermaid 로 그렸어요 (GitHub, IntelliJ Markdown 미리보기에서 바로 보여요).

## 목차

1. [전체 구조 (계층)](#1-전체-구조-계층)
2. [도메인 ER 개요](#2-도메인-er-개요)
3. [할 일 : 배정 → 제출 → 검수](#3-할-일--배정--제출--검수)
4. [보고서 : 다단계 결재](#4-보고서--다단계-결재)
5. [Transactional Outbox : 재시도 · DEAD · 수동 재처리](#5-transactional-outbox--재시도--dead--수동-재처리)
6. [배치 실행 기록과 복구 (catch-up)](#6-배치-실행-기록과-복구-catch-up)
7. [전처리 : 파일 보상 삭제 · 중복 제출 방지 · 재시도](#7-전처리--파일-보상-삭제--중복-제출-방지--재시도)
8. [장애 대응 표 (어디서 실패하면 무엇이 막아 주나)](#8-장애-대응-표)
9. [보안](#9-보안)

<br>

## 1. 전체 구조 (계층)

기능(도메인)마다 패키지를 나누고, 각 패키지 안은 `controller → service → repository → entity` 같은 구조예요.
알림 같은 **후처리는 업무 서비스가 직접 하지 않고** Outbox 테이블에 적어 두면 릴레이가 처리해요.

```mermaid
flowchart LR
    B["브라우저<br/>Thymeleaf 화면"] --> SEC

    subgraph SEC["Spring Security"]
        A1["관리자 체인 /admin/**<br/>+ 허용 IP 필터"]
        A2["직원 체인 (나머지)"]
    end

    SEC --> C["Controller<br/>todo · report · board<br/>attendance · notification · admin"]
    C --> S["Service<br/>@Transactional · @Retryable"]
    S --> R["Repository<br/>Spring Data JPA"]
    R --> DB[("MySQL / H2")]
    S -- 같은 트랜잭션 --> OB[("outbox_event")]
    S -- 업로드 --> FS[("uploads 폴더")]

    subgraph ASYNC["후처리 · 배치"]
        RL["OutboxRelay<br/>AFTER_COMMIT @Async + @Scheduled"]
        P["OutboxProcessor<br/>claim → handle → DONE/재시도/DEAD"]
        H["NotificationOutboxHandler"]
        BJ["BatchScheduler / BatchJobs<br/>할 일 생성 · 마감 알림 · 복구"]
        JE[("job_execution")]
    end

    OB --> RL --> P --> H --> N[("notification")]
    BJ --> JE
    BJ --> S
    N -. 안 읽은 개수 .-> GA["GlobalModelAdvice<br/>헤더 🔔"]
```

| 패키지 | 하는 일 |
|---|---|
| `config` | 보안(관리자/직원 체인, IP 필터), 재시도·비동기, 스케줄링, 예시 데이터 |
| `common` | 파일 저장(보상 삭제), 반복 규칙, 공통 화면 값, 오류 화면 |
| `organization` | 팀, 근무지(매장) |
| `member` / `admin` | 직원 로그인, 내 정보 / 직원 관리, 재처리 모니터 |
| `todo` | 반복 할 일, 직원별 하루 할 일, 제출·검수·댓글 |
| `report` | 보고서 양식(폼 빌더), 제출, 결재, 엑셀·zip |
| `board` | 게시판, 글, 이슈 상태, 댓글 |
| `attendance` | 출퇴근, 스케줄, 휴가 |
| `notification` | 앱 안 알림 |
| `outbox` | Transactional Outbox (저장 · 릴레이 · 처리기) |
| `batch` | 배치 실행 기록, 생성·마감·복구 배치 |

<br>

## 2. 도메인 ER 개요

```mermaid
erDiagram
    TEAM ||--o{ MEMBER : "소속"
    WORKPLACE ||--o{ MEMBER : "근무"

    TODO_TASK ||--o{ TODO_ITEM : "항목"
    TODO_TASK }o--o{ MEMBER : "직원 배정"
    TODO_TASK }o--o{ WORKPLACE : "매장 배정"
    TODO_TASK }o--|| MEMBER : "검수자"
    TODO_TASK ||--o{ TODO_ASSIGNMENT : "날짜별"
    MEMBER ||--o{ TODO_ASSIGNMENT : "담당"
    TODO_ASSIGNMENT ||--o{ TODO_ANSWER : "답"
    TODO_ASSIGNMENT ||--o{ TODO_COMMENT : "댓글"

    REPORT_FORM ||--o{ REPORT_FIELD : "항목"
    REPORT_FORM ||--o{ REPORT_APPROVER : "결재선"
    REPORT_FORM ||--o{ REPORT : "제출"
    MEMBER ||--o{ REPORT : "작성"
    REPORT ||--o{ REPORT_ANSWER : "답"
    REPORT ||--o{ REPORT_APPROVAL : "단계별 결재"
    REPORT ||--o{ REPORT_COMMENT : "댓글"

    BOARD ||--o{ POST : "글"
    POST ||--o{ POST_COMMENT : "댓글"

    MEMBER ||--o{ ATTENDANCE : "출퇴근(하루 1줄)"
    MEMBER ||--o{ WORK_SCHEDULE : "근무"
    MEMBER ||--o{ LEAVE_REQUEST : "휴가"
    MEMBER ||--o{ NOTIFICATION : "받는 알림"

    OUTBOX_EVENT {
        string event_type
        string status "PENDING/PROCESSING/DONE/DEAD"
        int retry_count
        datetime next_retry_at
        string idempotency_key UK
    }
    JOB_EXECUTION {
        string job_name UK
        string job_key UK
        string status "RUNNING/COMPLETED/FAILED"
        int attempts
    }
    TODO_ASSIGNMENT {
        long task_id UK
        long member_id UK
        date work_date UK
        string status
        string last_submit_token UK
        long version "낙관적 락"
    }
    NOTIFICATION {
        string source_event_key UK "멱등"
        boolean is_read
    }
```

중복을 막는 **unique 제약**

| 테이블 | unique | 막는 것 |
|---|---|---|
| `todo_assignment` | (task, member, work_date) | 배치를 여러 번 돌려도 같은 날 같은 사람에게 두 번 배정 |
| `todo_assignment` | last_submit_token | 제출 더블클릭 |
| `report` / `post` | submit_token | 보고서·글 중복 등록 |
| `attendance` | (member, work_date) | 출근 두 번 |
| `outbox_event` | idempotency_key | 푸시 더블클릭 등 같은 요청으로 이벤트 두 번 |
| `notification` | source_event_key | 같은 이벤트를 두 번 처리해도 알림은 한 번 |
| `job_execution` | (job_name, job_key) | 같은 날짜 배치 기록 한 줄 |

<br>

## 3. 할 일 : 배정 → 제출 → 검수

```mermaid
sequenceDiagram
    autonumber
    actor A as 관리자
    participant T as TodoTaskService
    participant G as TodoAssignmentGenerator
    participant B as BatchScheduler (00:05)
    actor E as 직원
    participant S as TodoAssignmentService
    actor R as 검수자
    participant O as Outbox

    A->>T: 할 일 만들기 (주기·대상·검수자·항목)
    T->>G: 오늘 몫 바로 생성 (멱등)
    T->>O: "새 할 일" 알림 (같은 트랜잭션)
    B->>G: 매일 그 날짜 생성 (job_execution 기록)
    E->>S: 오늘 할 일 채워서 제출 (submitToken)
    S->>S: 필수 확인 · 사진/서명 저장 · 상태 = 검수 대기중
    S->>O: 검수자에게 "검수 요청"
    R->>S: 재작업 요청 (사유)
    S->>O: 직원에게 "재작업 요청"
    E->>S: 다시 제출
    R->>S: 검수 통과 (@Version + @Retryable)
    S->>O: 직원에게 "검수 통과"
    A->>S: 대시보드에서 '푸시'
    S->>O: 미완료 직원에게만 (멱등성 키 = 화면 토큰 + 할 일)
```

```mermaid
stateDiagram-v2
    [*] --> NOT_SUBMITTED : 배치가 배정
    NOT_SUBMITTED --> PENDING_REVIEW : 제출 (검수자 있음)
    NOT_SUBMITTED --> SUBMITTED : 제출 (검수자 없음)
    PENDING_REVIEW --> APPROVED : 검수 통과
    PENDING_REVIEW --> REWORK : 재작업 요청 (사유)
    REWORK --> PENDING_REVIEW : 다시 제출
    APPROVED --> [*]
    SUBMITTED --> [*]
```

| 상태 | 화면 이름 |
|---|---|
| NOT_SUBMITTED | 제출 미완료 |
| SUBMITTED | 제출 완료 |
| PENDING_REVIEW | 검수 대기중 |
| APPROVED | 검수 통과 |
| REWORK | 재작업 필요 |

반복 규칙 (`RecurrenceRule`)
- DAILY : 시작일 ~ 종료일 매일
- WEEKLY : 고른 요일 (`MONDAY,WEDNESDAY`)
- MONTHLY : 매월 N일, 그 달에 없으면 말일 (31일 → 2월 28일)

<br>

## 4. 보고서 : 다단계 결재

```mermaid
sequenceDiagram
    autonumber
    actor E as 작성자
    participant S as ReportService
    actor A1 as 1단계 결재자
    actor A2 as 2단계 결재자
    participant O as Outbox

    E->>S: 제출 (양식의 결재선을 보고서에 복사)
    S->>O: 1단계에게 "결재 요청"
    A1->>S: 승인 (결재함)
    S->>S: 상태 = 검토중, currentStep = 2
    S->>O: 2단계에게 "결재 요청", 작성자에게 "검토중"
    A2->>S: 승인
    S->>S: 상태 = 검토완료
    S->>O: 작성자에게 "검토완료"
    Note over A1,A2: 어느 단계든 반려(사유 필수) → 상태 = 반려, 작성자에게 알림
```

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED : 제출 (제출완료)
    SUBMITTED --> IN_REVIEW : 1단계 승인 (다음 단계가 있음)
    IN_REVIEW --> IN_REVIEW : 중간 단계 승인
    SUBMITTED --> APPROVED : 마지막 단계 승인
    IN_REVIEW --> APPROVED : 마지막 단계 승인 (검토완료)
    SUBMITTED --> REJECTED : 반려
    IN_REVIEW --> REJECTED : 반려
```

- 결재함은 "대기 중이고, 보고서의 현재 단계가 내 단계"인 결재만 보여 줘요. 2단계 결재자가 1단계보다 먼저 누르면 "결재 차례가 아니에요".
- 관리자는 양식별 **엑셀**(제출 전체, 항목이 열)과 **첨부 zip**(이미지·문서·서명)을 받을 수 있어요.

<br>

## 5. Transactional Outbox : 재시도 · DEAD · 수동 재처리

### 왜 필요한가
업무 처리(예: 검수 통과)와 알림을 따로 하면 이런 일이 생겨요.
- DB 저장은 됐는데 알림 직전에 서버가 죽음 → **알림 유실**
- 알림은 보냈는데 DB 저장이 롤백됨 → **없는 일에 대한 알림**

그래서 업무 데이터와 "할 일 메모(Outbox 이벤트)"를 **같은 트랜잭션**으로 저장하고, 알림은 그 메모를 읽어서 따로 처리해요.
`OutboxService.publish()` 는 `Propagation.MANDATORY` 라서 업무 트랜잭션 밖에서는 부를 수조차 없어요.

```mermaid
sequenceDiagram
    autonumber
    participant S as 업무 서비스 (@Transactional)
    participant DB as DB
    participant L as OutboxRelay.onSaved<br/>(AFTER_COMMIT, @Async)
    participant J as OutboxRelay.poll<br/>(@Scheduled 5초)
    participant P as OutboxProcessor
    participant H as Handler (멱등)

    S->>DB: 업무 데이터 변경
    S->>DB: outbox_event INSERT (PENDING) ← 같은 트랜잭션
    S-->>DB: COMMIT
    DB-->>L: 커밋된 뒤에만 이벤트 전달
    L->>P: process(id) — 빠른 경로
    J->>P: process(id) — 느린 경로 (빠른 경로 실패·서버 재시작 대비)
    P->>DB: ① claim : UPDATE … SET PROCESSING WHERE id=? AND status=PENDING
    alt 1행 갱신 (내가 선점)
        P->>H: ② handle (REQUIRES_NEW)
        alt 성공
            P->>DB: DONE
        else 실패
            P->>DB: ③ retryCount++, nextRetryAt = now + base×2^(n-1)<br/>retryCount ≥ max → DEAD (별도 트랜잭션)
        end
    else 0행 (다른 쪽이 이미 가져감)
        P-->>P: 아무것도 안 함
    end
```

```mermaid
stateDiagram-v2
    [*] --> PENDING : 업무 트랜잭션과 함께 저장
    PENDING --> PROCESSING : claim (원자적 UPDATE)
    PROCESSING --> DONE : 핸들러 성공
    PROCESSING --> PENDING : 실패, 재시도 남음 (지수 백오프)
    PROCESSING --> DEAD : 실패, max-retries 도달
    PROCESSING --> PENDING : lockedAt 이 오래됨 (서버가 처리 중 죽음) → 되살리기
    DEAD --> PENDING : 관리자 '재처리' (retryCount = 0)
    DONE --> [*]
```

| 장치 | 코드 | 설명 |
|---|---|---|
| 같은 트랜잭션 저장 | `OutboxService.publish` (MANDATORY) | 업무와 이벤트가 같이 커밋/롤백 |
| 빠른 경로 | `OutboxRelay.onSaved` (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`) | 커밋 직후 별도 스레드에서 처리 → 알림이 거의 즉시 |
| 느린 경로 | `OutboxRelay.poll` (`@Scheduled`) | PENDING 을 주기적으로 훑음 → 결국은 처리됨 |
| 원자적 선점 | `OutboxEventRepository.claim` | 두 경로가 동시에 집어도 한쪽만 처리 |
| 지수 백오프 | `OutboxEvent.markFailed` | 10s, 20s, 40s … 최대 1시간 |
| DEAD | `max-retries` (기본 5) | 계속 실패하면 멈추고 사람이 보게 |
| 멈춘 이벤트 복구 | `releaseStuck` | PROCESSING 으로 5분 넘게 멈추면 PENDING 으로 |
| 멱등 핸들러 | `Notification.sourceEventKey` unique | 같은 이벤트가 두 번 와도 알림은 한 번 (at-least-once 전달이므로 필수) |
| 멱등성 키 | `OutboxEvent.idempotencyKey` unique | 푸시 버튼 더블클릭, 마감 알림 하루 한 번 |
| 수동 재처리 | 관리자 > 재처리 모니터 | DEAD → PENDING, 횟수 0 |

<br>

## 6. 배치 실행 기록과 복구 (catch-up)

`job_execution` 에 (배치 이름, 날짜) 마다 한 줄씩 기록해서 "그 날짜를 끝냈는지"를 DB 로 판단해요.

```mermaid
flowchart TD
    T1["매일 00:05<br/>generateToday"] --> RUN
    T2["서버 시작 직후<br/>ApplicationReadyEvent"] --> CU
    T3["10분마다<br/>recover"] --> CU
    T4["관리자 '누락·실패 배치 지금 복구'"] --> CU
    CU["catchUp : 최근 N일 각 날짜 + FAILED 기록"] --> RUN

    RUN{"job_execution<br/>(이름, 날짜) 상태?"}
    RUN -- COMPLETED --> SKIP["건너뜀"]
    RUN -- "RUNNING (30분 이내)" --> SKIP
    RUN -- "없음 / FAILED / 오래된 RUNNING" --> START["RUNNING, attempts+1<br/>(REQUIRES_NEW)"]
    START --> WORK["할 일 생성<br/>이미 있는 (할 일, 직원, 날짜)는 건너뜀"]
    WORK -- 성공 --> OK["COMPLETED + 결과 메시지"]
    WORK -- 실패 --> NG["FAILED + 오류 메시지"]
    NG -. 다음 복구 때 다시 .-> CU
    M["관리자 '재실행' (force)"] --> START
```

- 서버가 3일 꺼져 있다가 켜지면 → 시작 직후 catch-up 이 빠진 3일치 할 일을 만들어요.
- 생성 작업 자체가 멱등이라 **강제 재실행(force)** 해도 중복이 생기지 않아요.
- 마감 알림(`TODO_DEADLINE_NOTIFY`)은 1분마다 "마감 시각이 지났는가"를 보고, 그날 한 번만 실행돼요 (job_execution + 멱등성 키 이중 방어).

<br>

## 7. 전처리 : 파일 보상 삭제 · 중복 제출 방지 · 재시도

### 업로드 파일 보상 삭제
파일은 디스크에, 기록은 DB 에 저장돼요. DB 가 롤백되면 디스크 파일만 남는 "고아 파일"이 생기므로,
`FileStore` 가 파일을 저장할 때 `TransactionSynchronization` 을 등록해 두고 **커밋되지 않으면 지워요** (보상, compensation).

```mermaid
sequenceDiagram
    participant S as Service (@Transactional)
    participant F as FileStore
    participant D as 디스크
    participant TX as 트랜잭션
    S->>F: storeImage(사진)
    F->>D: 파일 쓰기
    F->>TX: registerSynchronization(afterCompletion)
    S->>S: 필수 항목 검사 실패! (예외)
    TX-->>F: afterCompletion(ROLLED_BACK)
    F->>D: 방금 쓴 파일 삭제
```

### 중복 제출 방지 (멱등성 토큰)
화면을 열 때 숨은 `submitToken`(UUID)을 만들어 두고, 서버는
- 할 일 : 지난번 제출 토큰과 같으면 무시
- 보고서·게시글 : 같은 토큰으로 만든 것이 있으면 그것을 돌려줌 (unique 제약이 동시 요청까지 막음)
- 화면에서도 `data-once` 로 제출 버튼을 잠가요

### 일시적인 DB 오류 재시도
Spring Framework 7 에 들어간 `@Retryable` (`org.springframework.resilience.annotation`) 을 `@EnableResilientMethods` 로 켰어요.

```java
@Retryable(includes = {ConcurrencyFailureException.class, TransientDataAccessException.class,
        CannotCreateTransactionException.class}, maxRetries = 3, delay = 100, multiplier = 2)
public TodoAssignment review(...)   // 검수 / 결재 / 출퇴근 / 휴가 승인 / 할 일 제출
```

- `@EnableResilientMethods(order = HIGHEST_PRECEDENCE)` → 재시도 프록시가 트랜잭션 프록시 **바깥**에 있어서, 재시도마다 **새 트랜잭션**이 열려요.
- `@Version`(낙관적 락) 과 함께 써서, 두 검수자가 동시에 누르면 늦은 쪽은 충돌 → 재시도 → 최신 상태(이미 통과)를 읽고 "이미 처리된 할 일이에요" 로 안내해요.

<br>

## 8. 장애 대응 표

| 실패 지점 | 생길 수 있는 문제 | 막는 장치 | 위치 |
|---|---|---|---|
| 업무 저장 후 알림 전에 서버 종료 | 알림 유실 | Outbox 같은 트랜잭션 저장 + 스케줄러 재처리 | `OutboxService`, `OutboxRelay.poll` |
| 업무 트랜잭션 롤백 | 없는 일에 대한 알림 | Outbox 행도 같이 롤백, 빠른 경로는 AFTER_COMMIT | `OutboxRelay.onSaved` |
| 알림 처리 중 예외 | 알림 유실 | retryCount++, 지수 백오프 재시도 | `OutboxProcessor`, `OutboxEvent.markFailed` |
| 계속 실패 | 무한 재시도 | max-retries 후 DEAD → 모니터에서 수동 재처리 | `MonitorController` |
| 처리 중(PROCESSING) 서버 종료 | 이벤트가 영원히 멈춤 | lockedAt 타임아웃 후 PENDING 되살리기 | `releaseStuck` |
| 빠른 경로와 스케줄러가 동시에 처리 | 알림 두 번 | 원자적 claim UPDATE + 알림 sourceEventKey unique | `claim`, `Notification` |
| 푸시 버튼 더블클릭 | 알림 두 번 | Outbox idempotencyKey unique | `TodoAssignmentService.remind` |
| 서버가 며칠 꺼짐 | 그 날짜 할 일 없음 | job_execution + 시작 시 catch-up | `BatchJobs.catchUp` |
| 생성 배치 실패 | 그 날짜 할 일 없음 | FAILED 기록 → 10분마다 재시도, 모니터 '재실행' | `JobExecutionService` |
| 배치 두 번 실행 | 할 일 중복 | (task, member, date) unique + 이미 있는 직원 건너뛰기 | `TodoAssignmentGenerator` |
| 두 검수자/결재자 동시 처리 | 상태 덮어쓰기 | `@Version` 낙관적 락 + `@Retryable` | `TodoAssignment`, `Report` |
| DB 일시 장애·락 타임아웃 | 요청 실패 | `@Retryable` 100ms → 200ms → 400ms | 서비스 메서드 |
| 파일 저장 후 DB 롤백 | 고아 파일 | TransactionSynchronization 보상 삭제 | `FileStore` |
| 제출 더블클릭·새로고침 재전송 | 중복 제출 | submitToken (unique) | 할 일·보고서·게시글 |
| 출근 두 번 | 기록 두 줄 | (member, date) unique | `Attendance` |

<br>

## 9. 보안

| 항목 | 내용 | 설정 |
|---|---|---|
| 로그인 분리 | `/admin/**` 는 관리자 체인(관리자 로그인 화면, ADMIN 만), 나머지는 직원 체인 | `SecurityConfig` |
| 관리자 IP 제한 | 허용 IP(앞부분 일치)만 `/admin/**` 접근, 비우면 제한 없음 | `app.security.admin-allowed-ips` |
| 일괄 다운로드 제한 | 엑셀·zip 은 관리자만, 설정으로 끌 수 있음 (다운로드는 로그로 남김) | `app.security.bulk-download-enabled` |
| 퇴사 처리 | 지우지 않고 비활성화 → 로그인 불가, 새 할 일 배정 제외 | `Member.active` |
| 세부 권한 | 검수자·결재자 본인인지 서비스에서 확인 | `canReview`, `canDecide` |
| 업로드 | 확장자 허용 목록, 파일 이름은 UUID, `/uploads/**` 도 로그인 필요 | `FileStore` |
