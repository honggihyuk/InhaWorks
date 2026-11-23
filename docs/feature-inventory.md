# InhaWorks 기능 인벤토리 (UI 리디자인 보존 목록)

> 대상 : Spring Boot 4.1 / Thymeleaf / JPA — `com.mysite.sbb`
> 목적 : UI 를 새로 만들 때 **빠뜨리면 안 되는** 화면·버튼·폼 필드·쿼리 파라미터·상태 전이·엔드포인트를 한곳에 정리한다.
> 표기 : `GET`/`POST` = HTTP 메서드, `{id}` = 경로 변수, `?x=` = 쿼리 파라미터, **flash** = `RedirectAttributes.addFlashAttribute`.
> 모든 POST 폼은 `th:action` 으로 만들어 **CSRF hidden 토큰이 자동 삽입**된다 (새 화면에서도 반드시 유지).

---

## 1. 역할 · 인증

### 1-1. 역할 (`MemberRole`)

| enum | 권한 문자열 | 라벨 | 의미 |
|---|---|---|---|
| `ADMIN` | `ROLE_ADMIN` | 관리자 | `/admin/**` 전체 접근, 모든 할 일/보고서 열람, 검수 대기함 전체, 게시글 고정·수정·삭제 |
| `MANAGER` | `ROLE_MANAGER` | 매니저 | URL 권한은 USER 와 동일. 검수자·결재자로 지정되는 용도 (세부 권한은 서비스에서 "본인이 검수자/결재자인지"로 판단) |
| `USER` | `ROLE_USER` | 직원 | 일반 직원 |

- 회원가입 없음 — 계정은 관리자가 생성. 퇴사(`active=false`) 계정은 `MemberSecurityService` 가 disabled 로 반환 → 로그인 불가.
- 시드 : `app.admin.username/password` (기본 admin / admin1234) 가 없으면 서버 시작 시 생성, `app.seed.enabled` 면 예시 데이터(직원 비번 `app.seed.employee-password`).

### 1-2. SecurityFilterChain 2개 (`config/SecurityConfig.java`)

| 체인 | 매칭 | 접근 규칙 | 로그인 | 로그아웃 | 권한 없음 |
|---|---|---|---|---|---|
| `@Order(1)` 관리자 | `/admin/**` | `/admin/login` permitAll, 나머지 `hasRole("ADMIN")` | 화면 `GET /admin/login`, 처리 `POST /admin/login` (username, password) → 성공 `/admin` (항상), 실패 `/admin/login?error` | `POST /admin/logout` → `/admin/login?logout`, 세션 무효화 | `/admin/login?denied` |
| `@Order(2)` 직원 | 그 외 전부 | `/css/**`, `/js/**`, `/images/**`, `/favicon.ico`, `/member/**` permitAll, 나머지 `authenticated()` (`/uploads/**` 포함) | 화면 `GET /member/login`, 처리 `POST /member/login` → 성공 `/` , 실패 `/member/login?error` | `POST /member/logout` → `/member/login?logout`, 세션 무효화 | `/denied` |

- **관리자 IP 필터** (`AdminIpAllowFilter`) : 관리자 체인 맨 앞(`SecurityContextHolderFilter` 이전). `app.security.admin-allowed-ips` (쉼표 구분, **접두어 매칭** 예 `192.168.0.`) 가 비어 있지 않으면 `request.getRemoteAddr()` 가 목록 중 하나로 시작하지 않을 때 **403 "허용되지 않은 IP 입니다."** — 로그인 화면조차 차단. 비어 있으면 제한 없음.
- **일괄 다운로드 스위치** : `app.security.bulk-download-enabled` (기본 true). false 면 엑셀/zip 버튼 숨김 + 엔드포인트가 BusinessException.
- 로그인 화면 메시지 : `?error`, `?logout`, (관리자) `?denied` 쿼리 파라미터로 알림 박스 표시. 직원 로그인 화면 ↔ 관리자 로그인 화면 상호 링크.
- 세션은 두 체인이 공유 — 직원 로그인 화면으로 들어온 ADMIN 도 `/admin` 접근 가능 (헤더에 `관리자` 링크 `sec:authorize="hasRole('ADMIN')"`).

### 1-3. 공통 모델 / 오류 처리

| 구성요소 | 내용 |
|---|---|
| `GlobalModelAdvice` | 모든 화면에 `loginMember`(Member), `currentUri`(현재 메뉴 강조), `unreadCount`(안 읽은 알림 수) |
| `GlobalExceptionHandler` | `BusinessException`/`IllegalArgumentException` → 400 + `message.html`; `EntityNotFoundException` → 404 + `message.html` (model `message`, "뒤로"=`history.back()`, "홈으로") |
| 레이아웃 `layouts/layout.html` | 헤더/푸터, `bootstrap.bundle.min.js`, `inhaworks.js`; flash `errorMessage` 가 있으면 **`alert()`** 로 표시 |
| `/uploads/**` | `WebConfig` 가 업로드 폴더(`app.upload-dir`)를 정적 매핑. 로그인 필요 |

---

## 2. 공통 UI 요소

### 2-1. 직원 헤더 (`fragments/header.html`, 로그인 시에만)

| 요소 | 링크 | 비고 |
|---|---|---|
| 로고 | `GET /` | |
| 메뉴 탭 | `/attendance` 출퇴근 · `/schedule` 스케줄 · `/leave` 휴가 · `/board` 게시판 · `/todo` 할 일 · `/report` 보고서(`/approvals` 포함 강조) · `/notifications` 알림 | `currentUri` prefix 로 active, 알림 탭은 미읽음 시 `dot` |
| 관리자 링크 | `/admin` | ADMIN 만 |
| 🔔 벨 | `/notifications` | `unreadCount` 배지 (99 초과 시 `99+`) |
| 내 정보 칩 | `/me` | 아바타 + `displayName` |

### 2-2. 관리자 헤더 (`fragments/admin.html :: head(active, title, sub)`)

- "관리자 모드" 제목/부제, 버튼 **사이트로**(`/`), **로그아웃**(`POST /admin/logout`).
- 서브탭 : 직원 `/admin` · 팀·근무지 `/admin/org` · 할 일 `/admin/todo` · 보고서 `/admin/report` · 게시판 `/admin/board` · 출퇴근 `/admin/attendance` · 스케줄 `/admin/schedule` · 휴가 `/admin/leave` · 재처리 모니터 `/admin/monitor`.
- flash `adminMessage` 알림 박스.

### 2-3. 공통 프래그먼트

| 프래그먼트 | 용도 |
|---|---|
| `avatar :: avatar(m, size)` | 사진(`photoPath`) 있으면 img, 없으면 `initials` |
| `comments :: box(comments, action)` | 댓글 목록(아바타, 이름, `M.d HH:mm`, 내용) + 입력 `POST {action}` 필드 `content`(required, max 1000). `@아이디` 멘션 → 알림. 앵커 `#comments` |
| `fields :: input(kind, fid, prefix, options, ans)` | 항목 입력 : YES_NO 라디오(Y/N), CHECKBOX 체크(Y), CHOICE 라디오(선택지), TEXT textarea(max 2000), PHOTO/IMAGE `file_{id}` (accept image/*, capture=environment, 기존 사진 미리보기), FILE `file_{id}` (pdf/오피스/hwp/이미지/zip ≤10MB), SIGNATURE 캔버스 + hidden `sig_{id}` + "서명 지우기". 값 이름 = `{prefix}{id}` (할 일 `item_`, 보고서 `field_`) |
| `fields :: view(kind, ans)` | 답 읽기 : 이미지/첨부 다운로드 링크(`download`)/값 (Y→예 또는 ✔ 확인, N→아니오, `|`→`, `) |

### 2-4. `static/js/inhaworks.js` 동작 (data 속성 기반 — 새 UI 에서도 보존)

| 기능 | 트리거 | 동작 |
|---|---|---|
| 서명 패드 | `canvas.sig-pad[data-target]` + `.sig-wrap [data-sig-clear]` | 포인터로 그리기, 제출 시 PNG data URL 을 hidden `sig_{id}` 에 넣음 (안 그렸으면 빈 값) |
| 폼 빌더 행 추가 | `[data-add-row="tplId"][data-into="boxId"]` | `<template>` 복제해 추가 |
| 행 삭제 | `[data-remove-row]` (`.builder-row` 안) | 마지막 1행은 남김 |
| 반복 주기 토글 | `[data-cycle-select]` + `[data-cycle-show="WEEKLY|MONTHLY"]` | 선택값과 일치하는 블록만 표시 |
| 확인 창 | `<form data-confirm="...">` | **`window.confirm`**, 취소 시 submit 중단 |
| 이중 제출 방지 | `<form data-once>` | 제출 직후 submit 버튼 disabled (서버도 토큰으로 이중 방어) |
| 시계 | `[data-clock]` | 1초마다 HH:mm:ss |

- 그 외 인라인 JS : `member/me.html` 사진 input `onchange="this.form.submit()"`, `message.html` `history.back()`, 레이아웃 `alert(errorMessage)`.
- **모달/드롭다운(Bootstrap JS 컴포넌트)은 사용하지 않음.** 팝업은 `window.confirm` 과 `alert` 뿐.

### 2-5. 상태 배지 CSS (enum 의 `css` 필드)

`st-gray`, `st-blue`, `st-orange`, `st-green`, `st-red` — AssignmentStatus / ReportStatus / ApprovalDecision / IssueStatus / LeaveStatus 가 `label`, `css` 를 가짐.

---

## 3. 화면 목록

### 3-1. 관리자 화면

| # | URL (GET) | 템플릿 | 컨트롤러#메서드 | 모델 속성 | 목적 |
|---|---|---|---|---|---|
| A1 | `/admin/login` | admin/login | AdminController#login | (param error/denied/logout) | 관리자 로그인 |
| A2 | `/admin`, `/admin/` | admin/index | AdminController#index | memberList, roles | 직원 목록·권한 변경·퇴사/복직 |
| A3 | `/admin/member/new` | admin/member-form | AdminController#createForm | adminMemberDto, memberId(null), roles, teams, workplaces | 직원 등록 |
| A4 | `/admin/member/{id}/edit` | admin/member-form | AdminController#editForm | adminMemberDto, memberId, roles, teams, workplaces | 직원 수정 |
| A5 | `/admin/org` | admin/org | AdminController#org | teams, workplaces | 팀·근무지 관리 |
| A6 | `/admin/todo` | admin/todo-list | AdminTodoController#list | summaries (List<TaskSummary>) | 할 일 목록 + 오늘 완료율 |
| A7 | `/admin/todo/new` | admin/todo-form | AdminTodoController#createForm | todoTaskForm, members, workplaces, cycles(DAILY/WEEKLY/MONTHLY), weekDays, itemTypes | 반복 할 일 만들기 |
| A8 | `/admin/todo/{id}?date=` | admin/todo-dashboard | AdminTodoController#dashboard | board (TaskDashboard), remindToken | 날짜별 완료율·직원별 상태·푸시 |
| A9 | `/admin/report?category=` | admin/report-list | AdminReportController#list | forms (Map<ReportForm,Long>), categories, category | 보고서 양식 목록 |
| A10 | `/admin/report/new` | admin/report-form | AdminReportController#createForm | reportFormForm, members, teams, workplaces, categories, cycles(전체 4개), fieldTypes | 보고서 양식 빌더 |
| A11 | `/admin/report/{id}` | admin/report-submissions | AdminReportController#submissions | form, reports, bulkDownloadEnabled | 양식별 제출 내역 + 엑셀/zip |
| A12 | `/admin/board` | admin/board | AdminBoardController#list | boards (Map<Board,Long>), types, teams | 게시판 생성/삭제 |
| A13 | `/admin/attendance?date=` | admin/attendance | AdminAttendanceController#attendance | date, records, memberCount | 날짜별 출퇴근 현황 |
| A14 | `/admin/schedule?week=` | admin/schedule | AdminAttendanceController#schedule | monday, days, rows (List<WeekRow>), members | 주간 스케줄 배정 (직원×요일) |
| A15 | `/admin/leave` | admin/leave | AdminAttendanceController#leave | pending, recent | 휴가 승인/반려 |
| A16 | `/admin/monitor?status=` | admin/monitor | MonitorController#monitor | status, statuses, counts, events, jobCounts, jobs | Outbox·배치 재처리 모니터 |

### 3-2. 직원 화면

| # | URL (GET) | 템플릿 | 컨트롤러#메서드 | 모델 속성 | 목적 |
|---|---|---|---|---|---|
| E1 | `/member/login` | member/login | MemberController#login | (param error/logout) | 직원 로그인 |
| E2 | `/` | index | IndexController#index | today, attendance, todos, todoLeft, reviewWaiting, approvalWaiting, unreadTop, pinned, recentPosts | 홈 대시보드 |
| E3 | `/denied` | denied | IndexController#denied | – | 권한 없음 |
| E4 | `/me` | member/me | MeController#me | (loginMember) | 내 정보·사진·로그아웃 |
| E5 | `/todo?date=` | todo/list | TodoController#list | date, today, assignments, overdue, reviewWaiting | 날짜별 내 할 일 + 지난 미완료 |
| E6 | `/todo/review` | todo/review | TodoController#reviewQueue | queue | 검수 대기함 |
| E7 | `/todo/{id}` | todo/detail | TodoController#detail | a, canSubmit, canReview, comments, submitToken | 할 일 제출/검수/댓글 |
| E8 | `/report?category=&tag=` | report/list | ReportController#list | forms, tags, categories, category, tag, myReports, inboxCount | 작성 가능 양식 + 내 보고서 |
| E9 | `/report/write/{formId}` | report/write | ReportController#writeForm | form, canWrite, submitToken | 보고서 작성 |
| E10 | `/report/{id}` | report/detail | ReportController#detail | r, canDecide, comments | 보고서 보기·결재·댓글 |
| E11 | `/approvals` | report/approvals | ReportController#approvals | inbox, decided | 결재함 |
| E12 | `/board` | board/index | BoardController#boards | boards (Map<Board,Long>), pinned | 게시판 목록 + 고정 공지 |
| E13 | `/board/{id}?status=` | board/list | BoardController#posts | board, posts, status, issueStatuses, (이슈판) stats, resolvedRate | 글 목록 |
| E14 | `/board/{id}/write` | board/form | BoardController#writeForm | board, postForm, postId(null) | 글쓰기 |
| E15 | `/board/post/{id}` | board/detail | BoardController#detail | post, canEdit, comments, issueStatuses | 글 보기 (조회수 +1) |
| E16 | `/board/post/{id}/edit` | board/form | BoardController#editForm | postForm, board, postId | 글 고치기 |
| E17 | `/attendance` | attendance/index | AttendanceController#attendance | today, recent(31일), now | 출퇴근 |
| E18 | `/schedule?week=` | attendance/schedule | AttendanceController#schedule | monday, days, byDay, today | 내 주간 스케줄 |
| E19 | `/leave` | attendance/leave | AttendanceController#leave | leaves, types, today | 휴가 신청·내역 |
| E20 | `/notifications` | notification/list | NotificationController#list | notificationList | 알림 목록 |
| – | (오류) | message | GlobalExceptionHandler | message | 업무 오류 안내 |

---

## 4. 화면별 상세 (버튼 · 폼 · 필터 · 표 · 확인창 · 파일)

### 관리자

#### A1 관리자 로그인
- 폼 `POST /admin/login` : `username`(관리자ID), `password` (required). 알림 : `?error`, `?denied`, `?logout`.
- 링크 : "이용자 로그인으로 돌아가기" `/member/login`.

#### A2 직원 관리 `/admin`
- 부제 : 전체 N명. 버튼 **+ 직원 등록** → `/admin/member/new`.
- 표 열 : 이름(아바타+displayName, 퇴사 배지, 퇴사자 행 opacity .5) · 사용자ID · 팀·근무지·직급(`subtitle`) · 등록일 · 권한 · 액션.
- 권한 열 : 인라인 폼 `POST /admin/member/{id}` `role`=ADMIN|MANAGER|USER (select) + **저장**. 내 계정은 ADMIN 해제 불가(flash errorMessage → alert).
- 액션 : **수정** `/admin/member/{id}/edit`; **퇴사 처리 / 복직** `POST /admin/member/{id}/active` — `data-confirm`(window.confirm, 문구가 상태별로 다름). 로그인한 본인 행에는 표시 안 함.

#### A3/A4 직원 등록·수정 (`admin/member-form`, `memberId` null=등록)
- 폼 `POST /admin/member/new` 또는 `POST /admin/member/{id}/edit` (`AdminMemberDto`) :
  `username`(3–30, 중복검사), `name`(≤20), `password`(등록 필수 ≥4 / 수정 시 비우면 유지), `email`(선택, 형식·중복), `teamId`(select, "팀 없음"), `workplaceId`(select, "근무지 없음"), `position`, `role`(select), `phone`, `active`(체크박스 "재직 중").
- 필드 오류 `th:errors` 표시. 본인 수정 시 아이디 변경·ADMIN 해제 불가, active 강제 true.
- 버튼 : 목록으로 `/admin`, 등록하기/저장하기. 성공 → `/admin` + flash adminMessage.

- 이름(아바타+이름)은 **직원 허브** `/admin/members/{id}` 링크. 필수 서류를 안 낸 직원은 이름 옆 빨간 칩 "필수 서류 N" (→ 서류 탭).
- 위 세그먼트 : 직원 · 팀·근무지 · **인사 서류**.
- 직원 폼(A3/A4)에 인사 정보 : `employmentType`(정규직·계약직·파트타임·인턴), `hireDate`, `contractEndDate`, `returnDate`, `workPolicyId`. 바뀐 값은 변경 이력에 자동 기록.

#### A2-1 직원 허브(인사 카드) `/admin/members/{id}?tab=`
- 머리 : 아바타 · 이름 · 팀·근무지·직급 · 재직/퇴사 · 고용 형태 · 근무제 칩 · "필수 서류 N개 미제출"(빨강) · **정보 수정**.
- 탭 `tab=info|attendance|work|docs|history` :
  - 정보 : 인사 정보 카드(기본 정보 : 아이디·이메일·휴대폰·팀·근무지·직무/직급·권한 / 고용 : 고용 형태·입사일(근속)·계약 만료일·복직 예정일·근무제·등록일) + 서류 요약.
  - 근태 `&month=yyyy-MM` : 출근율 도넛(=(출근+지각)/(출근+지각+결근)) + 출근·지각·휴가·결근 수 + 월 달력(출근 파랑 · 지각 노랑 · 휴가 초록 · 결근 연한 빨강 · 휴무 회색 · 예정 테두리). 근무일 = 스케줄(휴무 제외), 그 달 스케줄이 없으면 근무제의 근무 요일. 지각 = 스케줄(또는 근무제) 시작 시각 이후 출근.
  - 업무 : 최근 30일 할 일 완료율 · 제출/통과 · 재작업, 최근 할 일 20건, 보고 및 결재 10건(→ `/report/{id}`).
  - 서류 : 서류 종류마다 상태(✓ 첨부 완료 / ⚠ 필수 미제출 / 없음) · 열람 권한 칩 · 파일(→ `/hr/docs/{id}/file`) · 유효기간 · **제출/교체** `POST /admin/members/{id}/docs` (typeId, file, expiresAt) · **지우기** `POST /admin/members/{id}/docs/{docId}/delete` (확인).
  - 이력 : 날짜 · 항목(팀·근무지·직급·권한·근무제·재직 상태·고용 형태·입사일·계약 만료일·복직 예정일·인사 서류) · 이전 · 변경 · 바꾼 사람.

#### A2-2 인사 서류 설정 `/admin/hr/doc-types`
- 표 : 서류 · 필수 · 열람 권한(본인·리더·관리자 / 본인·관리자 / 관리자만) · 직원 제출 여부 · 받은 수. ⋯ 메뉴 : 고치기(`#edit-{id}` 카드) · 사용 중지(확인)/다시 사용 `POST /admin/hr/doc-types/{id}/toggle`.
- 추가 `POST /admin/hr/doc-types`, 고치기 `POST /admin/hr/doc-types/{id}` : name, description, scope, required, employeeUpload (‘관리자만’ 서류는 직원 제출 불가).
- 처음 실행 기본 7종 : 근로계약서(필수, 관리자가 올림) · 통장 사본(필수) · 주민등록 등본(필수) · 개인정보 수집·이용 동의서(필수, 리더 열람) · 가족관계증명서 · 보건증(리더 열람) · 급여 정보(관리자만).

#### A5 팀·근무지 `/admin/org`
- 팀 추가 `POST /admin/org/team` : `name`(required ≤50), `description`(≤200). 중복 이름 오류.
- 팀 목록 행 : 이름/설명 + **삭제** `POST /admin/org/team/{id}/delete` (`data-confirm` "이 팀을 지울까요?"; 소속 직원 있으면 오류).
- 근무지 추가 `POST /admin/org/workplace` : `name`(required), `address`. 삭제 `POST /admin/org/workplace/{id}/delete` (confirm, 소속 직원 있으면 오류).

#### A6 할 일 관리 `/admin/todo`
- 버튼 **+ 할 일 만들기** → `/admin/todo/new`. 빈 상태 문구.
- 표 열 : 할 일(제목 링크 → 대시보드, "사용 중지" 배지) · 주기(`cycleText`) · 대상(`targetText`) · 검수자 · 오늘 완료율(진행바 + `rate% (done/total)`) · 액션.
- 액션 : **대시보드** `/admin/todo/{id}`; **사용 중지/다시 사용** `POST /admin/todo/{id}/active` (확인창 없음).

#### A7 할 일 만들기 `/admin/todo/new`
- 폼 `POST /admin/todo/new`, `multipart/form-data`, `data-once`. 전역 오류 박스.
- 기본 정보 : `title`(≤100, 필수), `guide`(textarea), `guideFiles`(file multiple → 첨부 저장).
- 반복·기간 : `cycle`(DAILY/WEEKLY/MONTHLY, `data-cycle-select`), `startDate`(필수), `endDate`(선택), `dayOfMonth`(1–31, MONTHLY 때만 표시), `days`(월~일 체크칩 `MONDAY`…, WEEKLY 때만 표시).
- 대상·검수 : `workplaceIds`(체크 그리드, 매장 단위), `memberIds`(체크 그리드, 직원 단위), `reviewerId`(select "검수 없음" 포함).
- 항목 빌더(`#item-rows`, 템플릿 `#item-tpl`) : 행마다 `itemTypes`(YES_NO/CHECKBOX/PHOTO/SIGNATURE/CHOICE/TEXT), `itemLabels`, `itemOptions`(객관식 쉼표), `itemRequired`(Y/N), × 삭제. **+ 항목 추가**.
- 검증 : WEEKLY 요일 필수, MONTHLY 1–31, 종료일≥시작일, 대상(직원 or 근무지) 1개 이상, 항목 1개 이상, CHOICE 선택지 필수.
- 성공 → `/admin/todo/{id}` (오늘 몫 즉시 생성 + 대상자 알림).

#### A8 할 일 대시보드 `/admin/todo/{id}?date=yyyy-MM-dd`
- 날짜 필터 폼 `GET /admin/todo/{id}` `date` + **보기**.
- **🔔 푸시 (미완료 N명)** : `POST /admin/todo/{id}/remind` hidden `date`, `token`(=remindToken 멱등키). `data-once` + `data-confirm`("미완료 직원에게 다시 알림을 보낼까요?"). 미완료 0이면 disabled.
- 통계 카드 : 완료율(rate%, done/total) + AssignmentStatus 5개별 인원. 진행바.
- 표 열 : 직원(아바타) · 근무지 · 상태 배지 · 제출(HH:mm) · 검수(검수자 · 재작업 사유) · **보기** `/todo/{assignmentId}`.
- 빈 상태 안내(배치 미실행 시 재처리 모니터 확인 안내). 하단 항목 칩(`type.label · label`).

#### A9 보고서 관리 `/admin/report`
- 분류 필터 칩 : 전체 `/admin/report`, `?category=TEAM|TOPIC|WORKPLACE`. 버튼 **+ 양식 만들기**.
- 표 열 : 양식(제목 링크 + 해시태그 배지) · 분류 · 작성 대상(`writerText`) · 언제(`scheduleText` 또는 cycle) · 결재선(`approvalLineText`) · 제출 N건 · 액션.
- 액션 : **제출 내역** `/admin/report/{id}`; **사용 중지/다시 사용** `POST /admin/report/{id}/active`.

#### A10 보고서 양식 만들기 `/admin/report/new`
- 폼 `POST /admin/report/new` (`ReportFormForm`, `data-once`, multipart 아님).
- 기본 : `title`(≤100 필수), `description`, `category`(TEAM/TOPIC/WORKPLACE), `hashtags`(공백 구분 `#태그`).
- 누가·언제 : `teamId`, `workplaceId`, `cycle`(DAILY/WEEKLY/MONTHLY/NONE), `scheduleText`(안내 글), `writerIds`(직원 체크 그리드). 팀·매장·직원 모두 비우면 전 직원.
- 결재선 : `approverIds` select ×3 (1·2·3단계, "없음" 가능, 빈 단계 건너뜀, 중복 인원 오류).
- 항목 빌더(`#field-rows`/`#field-tpl`) : `fieldTypes`(YES_NO/CHECKBOX/IMAGE/SIGNATURE/CHOICE/TEXT/FILE, 기본 TEXT), `fieldLabels`, `fieldOptions`, `fieldRequired`(Y/N).
- 성공 → `/admin/report/{id}`.

#### A11 제출 내역 `/admin/report/{id}`
- 요약 : 제출 N건 · 항목 N개.
- `bulkDownloadEnabled` 일 때 : **엑셀 다운로드** `GET /admin/report/{id}/excel` (.xlsx, 파일명 `{양식}_{날짜}.xlsx`, 시트 "제출 내역", 열 = 번호·작성자·팀·근무지·제출일시·상태·결재 단계 + 항목별 라벨), **첨부 zip** `GET /admin/report/{id}/files.zip` (zip 안 파일명 `{보고서번호}_{작성자}_{항목}_{원래이름}`). 꺼져 있으면 회색 배지. 다운로드는 로그로 반출 기록.
- 표 열 : # · 작성자(아바타) · 제출(yyyy.M.d HH:mm) · 상태 배지 · 결재 단계(`currentStep / 총단계`) · **보기** `/report/{id}`.

#### A12 게시판 관리 `/admin/board`
- 생성 폼 `POST /admin/board` : `name`(required ≤50), `description`, `type`(GENERAL/ISSUE), `teamId`("전 직원" 또는 팀 전용).
- 표 열 : 게시판(링크 `/board/{id}` + 설명) · 종류 · 볼 수 있는 사람 · 글 수 · **삭제** `POST /admin/board/{id}/delete` (`data-confirm`; 글 있으면 오류).

#### A13 출퇴근 현황 `/admin/attendance`
- 날짜 폼 `GET /admin/attendance?date=` + **보기**; "출근 N명 / 재직 M명".
- 표 열 : 직원 · 팀·근무지 · 출근 · 퇴근 · 근무 시간(`workedText`). 빈 상태.

#### A14 스케줄 `/admin/schedule` (리스트) · `/admin/schedule/month` (캘린더)
- 사이드바 메뉴 "스케줄" 하나 (출퇴근·휴가와 분리). 위쪽 **근무제 탭** `?policy={id}` : 전체 + 정책마다 소속 직원 얼굴·인원. 탭을 고르면 그 근무제 직원만 보이고 **근무제 카드**(1주 소정근로시간, 근무 요일, 하루 소정근로, 근무시간, 휴게, 근무지, 근무 유형 칩) + "+ 근무 유형" · "정책 수정" 링크.
- 보기 전환 : 리스트(주) / 캘린더(월). 캘린더는 근무 유형 필터 `?shift={id}`, 직원 필터 `?who={id}`.
- 배정 폼 `POST /admin/schedule` : `memberId`, `date`, `shiftTypeId`(선택, 직원 근무제의 유형만 보임), `startTime`·`endTime`(유형을 고르면 채워짐, 휴무면 비움), `memo`(≤100). 저장 뒤 같은 직원·유형·시각 유지, 날짜는 다음 날. 그 주 근로시간이 **52시간을 넘으면 errorMessage 토스트**(저장은 됨). 직원 알림.
- 리스트 표 : 행 = 재직 직원(근무제 칩), 열 = 월~일, 셀 = `.shift-pill`(유형 이름 + 시각) + × 삭제 `POST /admin/schedule/{id}/delete` (`data-confirm`), 마지막 열 "주 근로"(법정 휴게 뺀 시간, 소정 초과 파랑 · 52시간 초과 빨강).
- 캘린더 : 날마다 근무 유형별 색 블록(이름 · 시각 · 인원) + 사람 이름 × 지우기.

#### A14-1 근무제 관리 `/admin/schedule/policies`
- 정책 카드 목록(종류 칩 · 이름 · 주 시간 · 요일 · 근무시간 · 인원 · 근무 유형 수) + 새 정책 폼 `POST /admin/schedule/policies` (이름, 근무제 종류, 근무 요일, 1주 소정근로 1~52, 기본 출근·퇴근(재량·간주는 비움), 휴게 분, 근무지, 색).
- 상세 `/admin/schedule/policies/{id}` : 정책 수정 `POST /{id}` · 근무 유형 추가 `POST /{id}/shifts` · 수정 `POST /{id}/shifts/{sid}` · 지우기 `POST /{id}/shifts/{sid}/delete`(확인, 배정된 근무는 메모로 이름을 남기고 연결만 해제) · 직원 옮기기 `POST /{id}/members` `memberIds`(확인) · 정책 지우기 `POST /{id}/delete`(확인, 소속 직원이 있으면 막음).
- 근무제 종류 : 고정 근무제 · 시차 출퇴근제 · 선택적 근무제 · 탄력적 근무제 · 스케줄 근무제 · 재량 근무제 · 간주 근무제.
- 처음 실행 : 정책이 없으면 "사무실 고정 근무"(월~금 09–18, 사무실 근무·오전 근무·휴무) + "매장 스케줄 근무"(오픈 06–12 · 미들 11–16 · 마감 17–22 · 휴무)를 만들고, 근무제가 없는 직원을 배정(예시 데이터를 새로 만든 경우 직원은 매장, 관리자는 사무실). 메모가 유형 이름과 같은 기존 스케줄은 그 유형으로 연결.
- 직원 관리 : 목록에 "근무제" 열(칩 → 정책 상세), 직원 등록·수정 폼에 근무제 선택.

#### A15 휴가 승인 `/admin/leave`
- 대기 목록 행 : 아바타 · `이름 · 종류 · 기간 (N일)` · 사유 + 폼 `POST /admin/leave/{id}` `comment`(의견) + 버튼 `decision=approve`(승인) / `decision=reject`(반려). `data-once`, 확인창 없음.
- "최근 휴가" 표(최근 50) 열 : 직원 · 종류 · 기간 · 상태 배지 · 처리(처리자 · 시각).

#### A16 재처리 모니터 `/admin/monitor`
- Outbox 섹션 : **지금 처리 (PENDING)** `POST /admin/monitor/outbox/poll`.
- 상태 카드(PENDING/PROCESSING/DONE/DEAD 건수, 클릭 → `?status=`; DEAD>0 빨간 숫자). 필터 칩 : 전체 + 상태별 + 종류별. 표는 20건씩 최신순 페이지 (`outboxPage`, 0부터).
- 이벤트 표 열 : # · 종류/출처(eventType, `aggregateType #aggregateId`, idempotencyKey) · 상태 배지 · 재시도(`retryCount / maxRetries`) · 다음 시도(PENDING 만) · 마지막 오류 · 만든 시각 · **재처리** `POST /admin/monitor/outbox/{id}/retry` hidden `status`(현재 필터 유지) — DONE/PROCESSING 이 아닐 때만.
- 배치 섹션 : **누락·실패 배치 지금 복구** `POST /admin/monitor/job/catch-up`. JobStatus 별 건수 칩.
- 배치 표(20건씩 최신순 페이지, `jobPage`, 이벤트 페이지와 독립. 재처리·재실행 뒤에도 필터와 페이지 유지) 열 : 배치(jobName) · 대상(jobKey 날짜) · 상태 · 시도(attempts) · 시작·끝 · 결과/오류 · **재실행** `POST /admin/monitor/job/{id}/rerun` (`data-confirm`).

### 직원

#### E1 로그인 `/member/login`
- 폼 `POST /member/login` : `username`, `password`. `?error`(퇴사 계정 안내 포함), `?logout`. 링크 "관리자 로그인" `/admin/login`.

#### E2 홈 `/`
- 출퇴근 버튼 : 미출근 → **출근하기** `POST /attendance/in`; 출근·미퇴근 → **퇴근하기** `POST /attendance/out` (둘 다 `data-once`); 퇴근 완료 → "퇴근 HH:mm" 배지.
- 요약 카드(링크) : 오늘 남은 할 일 `todoLeft / 오늘 건수` → `/todo`; 내 결재 대기 `approvalWaiting` → `/approvals`; 검수 대기 `reviewWaiting` → `/todo/review`; 오늘 출근 시각 → `/attendance`.
- 오늘 할 일 목록 → `/todo/{id}` (제목, 주기, 상태 배지). "전체 보기" `/todo`.
- 새 알림(미읽음 최근 5) : 각 항목이 폼 버튼 `POST /notifications/{id}/read` (읽음 후 링크 이동). "전체 보기".
- 📌 공지(고정 글 최근 5, 접근 가능한 것) → `/board/post/{id}`; 최근 글 5 → `/board/post/{id}` (이슈 상태 배지).

#### E4 내 정보 `/me`
- 아바타(lg), 이름, subtitle, username, 역할 배지.
- **사진 바꾸기** : `POST /me/photo` multipart `photo`(image/*) — 파일 선택 즉시 `onchange` 자동 제출. 오류 시 flash errorMessage(alert).
- **지우기** (사진 있을 때) `POST /me/photo/delete`.
- **로그아웃** `POST /member/logout`.

#### E5 할 일 `/todo`
- 날짜 이동 : ‹ 전날 `?date=d-1`, 오늘(오늘이 아닐 때) `/todo`, 다음날 › `?date=d+1`. **검수 대기 N** 버튼 `/todo/review` (N>0 일 때; ADMIN 은 전체 대기 수).
- 목록 : 제목 · `주기 · 항목 N개 · 검수 X` · 상태 배지 → `/todo/{id}`.
- "지난 날짜 미완료 · 재작업" 섹션(오늘 이전 + NOT_SUBMITTED/REWORK).

#### E6 검수 대기함 `/todo/review`
- 내가 검수자인 PENDING_REVIEW (ADMIN 은 전체). 행 : 아바타 · 제목 · `담당자 · 날짜 · 제출 HH:mm` · 상태 → `/todo/{id}`. 버튼 "내 할 일" `/todo`.

#### E7 할 일 상세 `/todo/{id}` (열람 : 담당자, 검수자, ADMIN)
- 헤더 : 날짜, 제목, `주기 · 담당 · 검수`, 상태 배지. flash `todoMessage`.
- REWORK 이면 경고 박스(사유, 검수자, 시각). 가이드 텍스트, 가이드 첨부 다운로드 링크.
- **제출 폼** (`canSubmit` = 담당자 && NOT_SUBMITTED/REWORK && workDate ≤ 오늘) : `POST /todo/{id}/submit` multipart, `data-once`, hidden `submitToken`; 항목 입력 `item_{itemId}`, `file_{itemId}`, `sig_{itemId}` (재작업 시 기존 답 채움). 버튼 "제출하기"/"다시 제출하기", 링크 "목록".
- 제출 불가 시 : 답 읽기 + "제출 시각 · N번째 제출".
- **검수** (`canReview` = PENDING_REVIEW && (검수자 || ADMIN)) : `POST /todo/{id}/review` `decision=approve` (✔ 검수 통과) / `decision=rework` + `reason`(required) (재작업 요청). 둘 다 `data-once`.
- 댓글 `POST /todo/{id}/comment` `content`.

#### E8 보고서 `/report`
- 버튼 **결재함 N** `/approvals`.
- 필터 칩 : 전체 `/report`, 분류 `?category=TEAM|TOPIC|WORKPLACE`, 해시태그 `?tag=#xxx` (작성 가능 양식의 태그 집합).
- "작성할 수 있는 양식" 행 : 제목 · `분류 · 언제 · 결재선` · 태그 · **작성** `/report/write/{formId}`.
- "내 보고서"(최근 50) 행 : 양식명 · 제출 시각 · `현재단계/총단계` · 상태 배지 → `/report/{id}`.

#### E9 보고서 작성 `/report/write/{formId}`
- 설명 박스. `canWrite` false 면 경고("대상 아님", writerText).
- 폼 `POST /report/write/{formId}` multipart, `data-once`, hidden `submitToken`; 항목 `field_{fieldId}`, `file_{fieldId}`, `sig_{fieldId}`. 버튼 "취소" `/report`, "제출하기". 성공 → `/report/{id}` (flash reportMessage); 동시 중복 → `/report` flash errorMessage.

#### E10 보고서 상세 `/report/{id}` (열람 : 작성자, 결재선, ADMIN)
- 헤더 : `보고서 #id`, 양식명, 작성자·subtitle·제출일시, 상태 배지. flash reportMessage.
- 결재선 : 단계별 카드(N단계, 결재자, 결정 배지, 결정 시각·의견), 현재 단계 `current` 강조.
- **결재** (`canDecide` = 현재 단계 결재자 본인) : `POST /report/{id}/decide` `comment` + `decision=approve`(승인)/`reject`(반려, 의견 필수). `data-once`.
- 항목별 답 읽기. 댓글 `POST /report/{id}/comment`.

#### E11 결재함 `/approvals`
- "내 차례" : WAITING 이고 보고서가 열린(SUBMITTED/IN_REVIEW) 결재 → `/report/{id}` (작성자, `step단계 / 총`, 제출 시각, 상태).
- "내가 처리한 결재"(최근 30) : 양식명, 작성자, 단계, 결정 시각, 결정 배지.

#### E12 게시판 `/board`
- 📌 고정 공지(최대 5). 게시판 목록(접근 가능) : 이름 · "이슈" 배지 · "{팀} 전용" 배지 · 설명 · 글 수 → `/board/{id}`.

#### E13 글 목록 `/board/{id}`
- 버튼 **글쓰기** `/board/{id}/write`.
- 이슈 & 해결 게시판 : 지점 칩(전체 지점 + 근무지 `?place=`) → 통계 2장(**처리 대기 중** n건 · 미해결 기간 평균/최장 일, **해결 완료** n건 · 해결률 · 해결 소요 기간 평균/최장 일, 지점 필터 반영) → 상태 세그먼트(전체 / 처리 대기(처리 중 포함) / 해결 완료 `?status=`) + 분류 select(`?category=` 재고·시설·장비·고객·인력·시스템·안전·기타) → 카드 목록(상태 칩 · 경과일 "n일째"(3일 이상 빨강)/"n일 만에 해결" · 제목 · 분류 | 지점 · 작성자 · 시각 · 첨부 수 · 해결 한 줄).
- 글 행 : 📌 공지 배지 · 제목 · 📎(첨부) · `작성자 · M.d HH:mm · 조회 N` · 이슈 상태 배지. 정렬 = 고정 먼저, 최신순.

#### E14/E16 글쓰기·고치기 (`board/form`, `postId` null=쓰기)
- 폼 `POST /board/{boardId}/write` 또는 `POST /board/post/{id}/edit`, multipart, `data-once` : hidden `submitToken`, `title`(≤150 필수), (이슈판) `category`·`workplaceId`(기본 = 내 근무지), `content`(필수; 이슈판 placeholder 다름), `files`(multiple, 수정 시 추가만), `pinned`(체크 — ADMIN 만 표시 "공지로 맨 위에 고정 (전 직원에게 알림)").
- 취소 링크 : 목록 또는 글.

#### E15 글 보기 `/board/post/{id}`
- 게시판 링크, 📌 배지, 제목, 작성자·일시·조회수, 이슈 배지.
- **고정/고정 풀기** `POST /board/post/{id}/pin` (ADMIN 만, `sec:authorize`). **수정** `/board/post/{id}/edit`, **삭제** `POST /board/post/{id}/delete` (`data-confirm`) — `canEdit` (작성자 또는 ADMIN).
- 본문, 첨부 다운로드 목록.
- 이슈 게시판 : 상태 배너(처리 대기 연한 빨강 / 해결 완료 연한 파랑 + 경과일) → 제목 · 분류 | 지점 · 작성자 · 시각 → **타임라인** : 빨간 ! "이슈"(본문 + 첨부 썸네일/파일 카드) → 파란 ✓ "해결"(해결자 · 시각 · 해결 내용 · 해결 첨부).
  - 해결 전 : **처리 시작** `POST /board/post/{id}/issue/start` (OPEN → IN_PROGRESS), **해결 등록** `POST /board/post/{id}/issue/resolve` multipart `resolution`(필수) + `files` → 해결자·시각 기록, 글쓴이 알림. 볼 수 있는 직원 누구나.
  - 해결 후 : **해결 취소…** `POST /board/post/{id}/issue/reopen` (확인 모달; 해결한 사람·글쓴이·매니저·관리자) → 해결 내용·첨부 비우고 처리 대기, 글쓴이 알림.
  - 예전 상태 폼 `POST /board/post/{id}/issue` 도 남아 있다 (해결자 기록).
- 댓글 `POST /board/post/{id}/comment`.

#### E17 출퇴근 `/attendance`
- flash attendanceMessage. 실시간 시계(`data-clock`), 오늘 출근/퇴근 시각(HH:mm:ss).
- **출근** `POST /attendance/in` (미출근 시), **퇴근 / 퇴근 시각 다시 찍기** `POST /attendance/out` (출근 후, 재클릭 시 시각 갱신). `data-once`.
- 최근 한 달 표 열 : 날짜 · 출근 · 퇴근 · 근무 시간.

#### E18 스케줄 `/schedule`
- 사이드바 메뉴 "스케줄" (출퇴근·휴가와 분리). 위에 **내 근무제 카드**(종류 · 이름 · 설명 · 이번 주 근무 시간 · 1주 소정근로 · 근무 요일 · 근무시간 · 휴게 · 근무지 · 근무 유형 칩).
- 주 이동 ‹ 지난주 / 이번 주 / 다음 주 › (`?week=`). 요일 카드 7개(오늘 강조) : 근무 유형 색 칩(이름 + 시각), 없으면 "휴무". 읽기 전용. 월별 `/schedule/month` 도 같은 카드 + 유형 색 칩.

#### E19 휴가 `/leave`
- flash leaveMessage. 신청 폼 `POST /leave` (`data-once`) : `type`(ANNUAL/HALF_AM/HALF_PM/SICK/OTHER), `startDate`, `endDate`(기본 오늘, 끝≥시작), `reason`(선택 ≤500).
- 내 휴가 목록 : `종류 · 기간 (N일)` · 사유 · 관리자 의견 · 상태 배지 · REQUESTED 면 **취소** `POST /leave/{id}/cancel` (`data-confirm`).

#### E20 알림 `/notifications`
- "안 읽은 알림 N개 · 최근 50개". **모두 읽음** `POST /notifications/read-all` (미읽음 있을 때).
- 각 알림 = 폼 버튼 `POST /notifications/{id}/read` → 읽음 처리 후 `link` 로 redirect (없으면 `/notifications`). 읽은 것 opacity .55, 타입 배지, 제목, 메시지, 시각.

---

## 5. 사용자 워크플로우 · 상태 전이

### 5-1. 할 일 (Todo)

**엔티티** : `TodoTask`(반복 정의: title, guide, guideFiles, cycle, daysOfWeek, dayOfMonth, startDate, endDate, reviewer, assignees, workplaces, items, active) → `TodoAssignment`(직원×날짜: status, submittedAt, submitCount, lastSubmitToken, reviewedBy, reviewedAt, reworkReason, answers, @Version) → `TodoAnswer`, `TodoComment`.

**반복 규칙** (`RecurrenceRule.occursOn`, startDate ≤ d ≤ endDate 이고 task.active) : DAILY 매일 · WEEKLY 선택 요일 · MONTHLY dayOfMonth (그 달에 없으면 말일) · NONE 시작일 하루 (할 일 폼에서는 NONE 선택 불가).
**대상** = 직접 지정한 재직 직원 ∪ 지정 근무지의 재직 직원.

**AssignmentStatus**

| enum | 라벨 | css | isDone | isSubmittable |
|---|---|---|---|---|
| NOT_SUBMITTED | 제출 미완료 | st-gray | | ✔ |
| SUBMITTED | 제출 완료 | st-blue | ✔ | |
| PENDING_REVIEW | 검수 대기중 | st-orange | ✔ | |
| APPROVED | 검수 통과 | st-green | ✔ | |
| REWORK | 재작업 필요 | st-red | | ✔ |

```
(배정) NOT_SUBMITTED ─제출─▶ PENDING_REVIEW (검수자 있음) ─통과─▶ APPROVED
                     └─제출─▶ SUBMITTED      (검수자 없음)   └─재작업 요청(사유 필수)─▶ REWORK ─다시 제출─▶ PENDING_REVIEW
```

| 단계 | 누가 | 엔드포인트 | 효과 / 알림 (NotificationType) |
|---|---|---|---|
| 1. 생성 | ADMIN | `POST /admin/todo/new` | 오늘 해당되면 즉시 배정, 대상자 "새 할 일" (TODO, 멱등키 `task-created:{id}`) |
| 2. 자동 배정 | 배치 | `TODO_ASSIGNMENT_GENERATE` 매일 00:05 | (task, member, date) unique — 멱등 |
| 3. 제출 | 담당자 | `POST /todo/{id}/submit` | 필수 항목·CHOICE 값 검증, 사진/서명 파일 저장; submitToken 중복 무시; 검수자에게 "검수 요청" (TODO) |
| 4. 검수 | 검수자/ADMIN | `POST /todo/{id}/review` decision=approve/rework | 담당자에게 "검수 통과"/"재작업 요청" (TODO) |
| 5. 재제출 | 담당자 | `POST /todo/{id}/submit` | REWORK→PENDING_REVIEW, submitCount+1 |
| 푸시 | ADMIN | `POST /admin/todo/{id}/remind` | NOT_SUBMITTED/REWORK 직원에게 "[다시 알림]" (멱등키 `remind:{token}:{assignmentId}`) |
| 마감 알림 | 배치 | `TODO_DEADLINE_NOTIFY` (매분 확인, `app.todo.deadline-time` 18:00 이후 하루 1회) | 미완료자 "[마감 임박]" (멱등키 `deadline:{date}:{id}`) |
| 댓글 | 열람 가능자 | `POST /todo/{id}/comment` | @멘션 대상 MENTION, 담당자·검수자 "새 댓글" (TODO) |
| 사용 중지 | ADMIN | `POST /admin/todo/{id}/active` | active 토글 → 이후 배정 안 됨 |

- 동시성 : `@Version` + `@Retryable`(최대 3회, 100ms×2) — 이미 처리됐으면 "이미 처리된 할 일이에요".
- 항목 타입 `TodoItemType` : YES_NO(예/아니오), CHECKBOX(체크박스), PHOTO(사진), SIGNATURE(서명), CHOICE(객관식), TEXT(주관식).

### 5-2. 보고서 (Report)

**엔티티** : `ReportForm`(title, description, category, team, workplace, hashtags, cycle, scheduleText, writers, approvers(step), fields, active) → `Report`(author, status, currentStep, submittedAt, completedAt, submitToken unique, answers, approvals, @Version) → `ReportApproval`(step, approver, decision, comment, decidedAt), `ReportComment`.

- 작성 가능(`canWrite`) : 양식 active 이고 (writers·team·workplace 모두 비었으면 전 직원) 또는 writers 포함 또는 같은 팀 또는 같은 근무지.
- `ReportCategory` : TEAM(팀별), TOPIC(주제별), WORKPLACE(매장별). `RepeatCycle` : DAILY 매일, WEEKLY 매주, MONTHLY 매월, NONE 수시 (보고서는 안내용, 자동 배정 없음).
- `ReportFieldType` : YES_NO, CHECKBOX, IMAGE(이미지), SIGNATURE, CHOICE, TEXT, FILE(문서). 파일형 = IMAGE/SIGNATURE/FILE.

**ReportStatus** : SUBMITTED 제출완료(st-blue) · IN_REVIEW 검토중(st-orange) · APPROVED 검토완료(st-green) · REJECTED 반려(st-red). `isOpen` = SUBMITTED|IN_REVIEW.
**ApprovalDecision** : WAITING 대기(st-gray) · APPROVED 승인(st-green) · REJECTED 반려(st-red).

```
제출 ─▶ SUBMITTED (currentStep=1, 결재선 없으면 0) ─1단계 승인─▶ IN_REVIEW (step+1) ─ … ─마지막 단계 승인─▶ APPROVED
                    └──────────── 어느 단계든 반려(의견 필수) ───────────────▶ REJECTED
```

| 단계 | 누가 | 엔드포인트 | 효과 / 알림 |
|---|---|---|---|
| 양식 만들기 | ADMIN | `POST /admin/report/new` | 결재선 최대 3단계 |
| 작성·제출 | 대상 직원 | `POST /report/write/{formId}` | 결재선 복사(전부 WAITING), 1단계 결재자 "결재 요청" (APPROVAL, `report-step:{id}:1`) |
| 승인(중간) | 현재 단계 결재자 | `POST /report/{id}/decide` decision=approve | 다음 결재자 "결재 요청" (APPROVAL), 작성자 "검토중" (REPORT) |
| 승인(마지막) | 〃 | 〃 | APPROVED, completedAt, 작성자 "검토완료" (REPORT) |
| 반려 | 〃 | decision=reject + comment | REJECTED, 작성자 "보고서 반려" (REPORT) |
| 댓글 | 작성자·결재선·ADMIN | `POST /report/{id}/comment` | 작성자+결재선+@멘션 "새 댓글" (REPORT) |
| 내보내기 | ADMIN | `GET /admin/report/{id}/excel`, `GET /admin/report/{id}/files.zip` | bulk-download 스위치 + ADMIN 이중 확인 |
| 사용 중지 | ADMIN | `POST /admin/report/{id}/active` | 작성 목록에서 제외 |

### 5-3. 게시판 · 이슈

- `BoardType` : GENERAL(일반), ISSUE(이슈 & 해결). `Board.team` 있으면 **팀 전용**(그 팀 직원 + ADMIN 만 접근).
- `IssueStatus` : OPEN 미해결(st-red) → IN_PROGRESS 처리중(st-orange) → RESOLVED 해결(st-green). 이슈판 새 글은 OPEN. 어느 상태로든 변경 가능, RESOLVED 는 `resolution` 필수(resolvedAt 기록, 다른 상태로 가면 null). 작성자가 아니면 작성자에게 "이슈 {상태}" (BOARD).
- 고정 공지 : 작성 시 `pinned`(ADMIN 만) → 접근 가능 재직자 전원 "[공지]" (BOARD, `post-pinned:{id}`). 이후 `POST /board/post/{id}/pin` 토글(알림 없음). 목록 고정 먼저 정렬, 홈/게시판 상단에 최근 5.
- 글 수정·삭제 : 작성자 또는 ADMIN. 삭제 시 댓글도 삭제. 글 있는 게시판은 삭제 불가.
- 중복 등록 방지 : `PostForm.submitToken` 이 같으면 기존 글 반환.
- 댓글 : 작성자 + @멘션 "새 댓글" (BOARD).
- 조회수 : 상세 열람마다 +1.

### 5-4. 근태

| 기능 | 엔드포인트 | 규칙 / 알림 |
|---|---|---|
| 출근 | `POST /attendance/in` | 하루 1행 (member, workDate unique); 중복 시 "이미 출근" |
| 퇴근 | `POST /attendance/out` | 출근 필요, 다시 누르면 퇴근 시각 갱신 |
| 스케줄 배정 | `POST /admin/schedule` | 근무 유형(선택) · 휴무는 시간 없음 · 그 외 end > start; 주 52시간 초과 시 경고; 직원 "근무 스케줄 배정" (ATTENDANCE, 링크 `/schedule?week=`) |
| 근무 정책 | `POST /admin/schedule/policies[/{id}]` | 이름 필수, 1주 소정 1~52, 휴게 0~240분, 퇴근 > 출근 |
| 근무 유형 | `POST /admin/schedule/policies/{id}/shifts[/{sid}]` | 휴무가 아니면 시작·끝 필수, 끝 > 시작 |
| 정책 지우기 | `POST /admin/schedule/policies/{id}/delete` | 소속 직원이 있으면 막음 |
| 스케줄 삭제 | `POST /admin/schedule/{id}/delete` | 알림 없음 |
| 휴가 신청 | `POST /leave` | 끝≥시작, REQUESTED; 모든 ADMIN 에게 "휴가 신청" (ATTENDANCE, 링크 `/admin/leave`) |
| 휴가 취소 | `POST /leave/{id}/cancel` | 본인 & REQUESTED 만 → CANCELED |
| 승인/반려 | `POST /admin/leave/{id}` decision=approve/reject, comment | REQUESTED 만; 직원 "휴가 승인/반려" (ATTENDANCE, `leave-decided:{id}`) |

- `LeaveStatus` : REQUESTED 승인 대기(st-orange) · APPROVED 승인(st-green) · REJECTED 반려(st-red) · CANCELED 취소(st-gray).
- `LeaveType` : ANNUAL 연차 · HALF_AM 오전 반차 · HALF_PM 오후 반차 · SICK 병가 · OTHER 기타.
- 주 시작 = 월요일 (`AttendanceService.weekStart`).

### 5-5. 알림

- `NotificationType` : TODO 할 일 · REPORT 보고서 · APPROVAL 결재 · BOARD 게시판 · ATTENDANCE 근태 · MENTION 멘션 · SYSTEM 시스템.
- 생성 경로 : 업무 트랜잭션에서 `OutboxService.notify()` → Outbox 이벤트(NOTIFICATION) → `NotificationOutboxHandler` 가 수신자별 `Notification` 생성 (`sourceEventKey` unique 로 멱등).
- 표시 위치 : 헤더 🔔 배지 + "알림" 탭 dot (`unreadCount`), 홈 "새 알림" 최근 미읽음 5, `/notifications` 최근 50.
- 읽음 : 클릭 = `POST /notifications/{id}/read` (본인 것만) → `link` 로 이동; 모두 읽음 `POST /notifications/read-all`.
- 멘션 : 댓글 본문의 `@([A-Za-z0-9_.-]{2,30})` → username 매칭.

### 5-6. Outbox / 배치 신뢰성 모니터

**OutboxStatus** : PENDING 대기 · PROCESSING 처리 중 · DONE 완료 · DEAD 실패(DEAD)

```
PENDING ─(claim)─▶ PROCESSING ─성공─▶ DONE
   ▲                   │ 실패 & 재시도 남음 (백오프 10s×2^(n-1), 최대 3600s)
   └───────────────────┘
                       └ 실패 & max-retries(5) 초과 ─▶ DEAD ─(관리자 재처리)─▶ PENDING (retryCount 0)
PROCESSING 5분(stuck-timeout) 초과 → 폴링 시 PENDING 으로 되살림
```

- 처리 경로 : ① 빠른 경로 — 커밋 후(AFTER_COMMIT) @Async 즉시 처리 (`app.outbox.fast-path`); ② 느린 경로 — 5초마다 폴링(`poll-interval-ms`). 관리자 수동 `POST /admin/monitor/outbox/poll`.
- 재처리 `POST /admin/monitor/outbox/{id}/retry` : 상태 무관 PENDING 으로 리셋 + 즉시 처리 이벤트 발행 (UI 는 PENDING/DEAD 에만 버튼).

**배치** (`JobExecution`: jobName, jobKey=날짜, status, attempts, startedAt, finishedAt, message, lastError)
- **JobStatus** : RUNNING 실행 중 · COMPLETED 완료 · FAILED 실패.
- 잡 : `TODO_ASSIGNMENT_GENERATE` (cron `app.batch.generate-cron` 기본 00:05), `TODO_DEADLINE_NOTIFY` (매분 확인).
- COMPLETED 인 (jobName, jobKey) 는 재실행 안 함 (force 제외). RUNNING 30분 초과는 stale 로 재시작 허용.
- 복구 catch-up : 최근 `catch-up-days`(7)일 생성 배치 + 모든 FAILED 생성 배치 재실행; 서버 시작 시(`catch-up-on-startup`) 및 10분마다(`recovery-interval-ms`). 수동 `POST /admin/monitor/job/catch-up`.
- 재실행 `POST /admin/monitor/job/{id}/rerun` : force=true 로 해당 날짜 다시 실행 (생성은 멱등).
- `app.scheduling.enabled` 가 꺼져 있으면 @Scheduled 미동작.

### 5-7. 직원 · 조직 관리

| 기능 | 엔드포인트 | 규칙 |
|---|---|---|
| 직원 등록 | `POST /admin/member/new` | username/email 중복 금지, 비번 ≥4 |
| 직원 수정 | `POST /admin/member/{id}/edit` | 비번 비우면 유지; 본인은 아이디·ADMIN·재직 변경 불가 |
| 권한 변경 | `POST /admin/member/{id}` role | 본인 ADMIN 해제 불가 |
| 퇴사/복직 | `POST /admin/member/{id}/active` | 본인 불가; 퇴사자는 로그인 불가 · 새 할 일 대상 제외 · 행 흐리게 |
| 팀 추가/삭제 | `POST /admin/org/team`, `/admin/org/team/{id}/delete` | 이름 중복 금지, 소속 직원 있으면 삭제 불가 |
| 근무지 추가/삭제 | `POST /admin/org/workplace`, `/admin/org/workplace/{id}/delete` | 〃 |
| 프로필 사진 | `POST /me/photo`, `POST /me/photo/delete` | 이미지 확장자 제한 (`FileStore`) |

### 5-8. 중복 제출 방지 토큰 (UI 에서 반드시 hidden 으로 유지)

| 화면 | 필드 | 서버 처리 |
|---|---|---|
| 할 일 제출 | `submitToken` (모델) | 직전 토큰과 같으면 무시 |
| 보고서 작성 | `submitToken` (모델) | unique — 같은 토큰이면 기존 보고서 반환 / 동시 시 "이미 제출된 보고서예요" |
| 글쓰기·수정 | `submitToken` (`PostForm`) | 같은 토큰 글 있으면 그 글 반환 |
| 할 일 푸시 | `token` (`remindToken`) | 멱등키 `remind:{token}:{assignmentId}` |

---

## 6. 엔드포인트 체크리스트 (전체 80개 + Security 4개)

| 기능 | 관리자/직원 | 엔드포인트 |
|---|---|---|
| 관리자 로그인 화면 | 관리자(공개) | `GET /admin/login` |
| 관리자 로그인 처리 (Security) | 관리자 | `POST /admin/login` |
| 관리자 로그아웃 (Security) | 관리자 | `POST /admin/logout` |
| 직원 목록 | 관리자 | `GET /admin` , `GET /admin/` |
| 권한 변경 | 관리자 | `POST /admin/member/{id}` |
| 직원 등록 화면 | 관리자 | `GET /admin/member/new` |
| 직원 등록 | 관리자 | `POST /admin/member/new` |
| 직원 수정 화면 | 관리자 | `GET /admin/member/{id}/edit` |
| 직원 수정 | 관리자 | `POST /admin/member/{id}/edit` |
| 퇴사/복직 토글 | 관리자 | `POST /admin/member/{id}/active` |
| 팀·근무지 화면 | 관리자 | `GET /admin/org` |
| 팀 추가 | 관리자 | `POST /admin/org/team` |
| 팀 삭제 | 관리자 | `POST /admin/org/team/{id}/delete` |
| 근무지 추가 | 관리자 | `POST /admin/org/workplace` |
| 근무지 삭제 | 관리자 | `POST /admin/org/workplace/{id}/delete` |
| 재처리 모니터 | 관리자 | `GET /admin/monitor?status=` |
| Outbox 재처리 | 관리자 | `POST /admin/monitor/outbox/{id}/retry` |
| Outbox 지금 처리 | 관리자 | `POST /admin/monitor/outbox/poll` |
| 배치 재실행 | 관리자 | `POST /admin/monitor/job/{id}/rerun` |
| 배치 복구(catch-up) | 관리자 | `POST /admin/monitor/job/catch-up` |
| 할 일 목록 | 관리자 | `GET /admin/todo` |
| 할 일 만들기 화면 | 관리자 | `GET /admin/todo/new` |
| 할 일 만들기 | 관리자 | `POST /admin/todo/new` |
| 할 일 대시보드 | 관리자 | `GET /admin/todo/{id}?date=` |
| 푸시(다시 알림) | 관리자 | `POST /admin/todo/{id}/remind` |
| 할 일 사용 중지 토글 | 관리자 | `POST /admin/todo/{id}/active` |
| 보고서 양식 목록 | 관리자 | `GET /admin/report?category=` |
| 양식 만들기 화면 | 관리자 | `GET /admin/report/new` |
| 양식 만들기 | 관리자 | `POST /admin/report/new` |
| 제출 내역 | 관리자 | `GET /admin/report/{id}` |
| 양식 사용 중지 토글 | 관리자 | `POST /admin/report/{id}/active` |
| 엑셀 다운로드 | 관리자 | `GET /admin/report/{id}/excel` |
| 첨부 zip 다운로드 | 관리자 | `GET /admin/report/{id}/files.zip` |
| 게시판 관리 | 관리자 | `GET /admin/board` |
| 게시판 생성 | 관리자 | `POST /admin/board` |
| 게시판 삭제 | 관리자 | `POST /admin/board/{id}/delete` |
| 출퇴근 현황 | 관리자 | `GET /admin/attendance?date=` |
| 스케줄 배정 화면 | 관리자 | `GET /admin/schedule?week=` |
| 스케줄 배정 | 관리자 | `POST /admin/schedule` |
| 스케줄 삭제 | 관리자 | `POST /admin/schedule/{id}/delete` |
| 스케줄 캘린더 | 관리자 | `GET /admin/schedule/month?month=&policy=&shift=&who=` |
| 근무제 관리 | 관리자 | `GET/POST /admin/schedule/policies`, `GET/POST /admin/schedule/policies/{id}` |
| 근무 유형 추가·수정·삭제 | 관리자 | `POST /admin/schedule/policies/{id}/shifts[/{sid}[/delete]]` |
| 근무제로 직원 옮기기 | 관리자 | `POST /admin/schedule/policies/{id}/members` |
| 근무 정책 삭제 | 관리자 | `POST /admin/schedule/policies/{id}/delete` |
| 휴가 승인 화면 | 관리자 | `GET /admin/leave` |
| 휴가 승인/반려 | 관리자 | `POST /admin/leave/{id}` |
| 직원 로그인 화면 | 직원(공개) | `GET /member/login` |
| 직원 로그인 처리 (Security) | 직원 | `POST /member/login` |
| 직원 로그아웃 (Security) | 직원 | `POST /member/logout` |
| 홈 | 직원 | `GET /` |
| 권한 없음 | 직원 | `GET /denied` |
| 내 정보 | 직원 | `GET /me` |
| 프로필 사진 변경 | 직원 | `POST /me/photo` |
| 프로필 사진 삭제 | 직원 | `POST /me/photo/delete` |
| 내 할 일 | 직원 | `GET /todo?date=` |
| 검수 대기함 | 직원(검수자/ADMIN) | `GET /todo/review` |
| 할 일 상세 | 직원 | `GET /todo/{id}` |
| 할 일 제출 | 직원(담당자) | `POST /todo/{id}/submit` |
| 검수 통과/재작업 | 직원(검수자/ADMIN) | `POST /todo/{id}/review` |
| 할 일 댓글 | 직원 | `POST /todo/{id}/comment` |
| 보고서 목록 | 직원 | `GET /report?category=&tag=` |
| 보고서 작성 화면 | 직원 | `GET /report/write/{formId}` |
| 보고서 제출 | 직원 | `POST /report/write/{formId}` |
| 보고서 상세 | 직원 | `GET /report/{id}` |
| 결재 승인/반려 | 직원(결재자) | `POST /report/{id}/decide` |
| 보고서 댓글 | 직원 | `POST /report/{id}/comment` |
| 결재함 | 직원 | `GET /approvals` |
| 게시판 목록 | 직원 | `GET /board` |
| 글 목록 | 직원 | `GET /board/{id}?status=` |
| 글쓰기 화면 | 직원 | `GET /board/{id}/write` |
| 글쓰기 | 직원 | `POST /board/{id}/write` |
| 글 보기 | 직원 | `GET /board/post/{id}` |
| 글 고치기 화면 | 직원(작성자/ADMIN) | `GET /board/post/{id}/edit` |
| 글 고치기 | 직원(작성자/ADMIN) | `POST /board/post/{id}/edit` |
| 글 삭제 | 직원(작성자/ADMIN) | `POST /board/post/{id}/delete` |
| 공지 고정 토글 | ADMIN | `POST /board/post/{id}/pin` |
| 이슈 상태 변경 | 직원 | `POST /board/post/{id}/issue` |
| 글 댓글 | 직원 | `POST /board/post/{id}/comment` |
| 출퇴근 화면 | 직원 | `GET /attendance` |
| 출근 | 직원 | `POST /attendance/in` |
| 퇴근 | 직원 | `POST /attendance/out` |
| 내 스케줄 | 직원 | `GET /schedule?week=` |
| 휴가 화면 | 직원 | `GET /leave` |
| 휴가 신청 | 직원 | `POST /leave` |
| 휴가 취소 | 직원 | `POST /leave/{id}/cancel` |
| 알림 목록 | 직원 | `GET /notifications` |
| 알림 읽고 이동 | 직원 | `POST /notifications/{id}/read` |
| 모두 읽음 | 직원 | `POST /notifications/read-all` |
| 업로드 파일 | 직원(로그인) | `GET /uploads/**` (정적 매핑) |

---
---

## 7. 화면 개편 이후 바뀐 점 (2026-11-19)

새 디자인(사이드바 + 상단바, `layouts/layout.html` · `static/css/app.css` · `static/js/app.js`)이 **기본 화면**이 되었고, Bootstrap 기반 기존 화면은 지웠다.
지운 파일은 `backup/legacy-ui-2026-11-19.zip` 에 남아 있다. 위 1~6장의 URL·입력 이름(item_ / field_ / file_ / sig_, submitToken, token)은 그대로다.

| 바뀐 것 | 내용 |
|---|---|
| 관리자 메인 | `GET /admin` = 대시보드 (AdminDashboardController). 직원 목록은 `GET /admin/members` 로 이동 (직원 등록·수정·퇴사 후 여기로 돌아옴) |
| 로그인 | `/member/login`, `/admin/login` 새 디자인 (동작·주소 같음) |
| 확인 창 | `window.confirm` 대신 확인 모달. 반려·재작업 요청은 사유를 입력해야 확인 버튼이 켜진다 |
| 알림 메시지 | 화면 오른쪽 아래 토스트. 검수 통과·승인 직후에는 '실행 취소' 버튼 |
| 스케줄 | 배정 후 고른 직원·시간·메모 유지, 날짜는 다음 날로. 월별 화면 `GET /admin/schedule/month`, `GET /schedule/month` |

### 새로 생긴 기능 · 엔드포인트

| 기능 | 대상 | 엔드포인트 | 비고 |
|---|---|---|---|
| 할 일 수정 | 관리자 | `GET/POST /admin/todo/{id}/edit` | 기존 항목은 `itemIds` 로 구분 (종류는 그대로). 지운 항목은 낸 답이 있으면 숨김(archived) — 지난 기록 보존. 가이드 첨부 지우기 `removeGuideFiles`. 대상이 늘면 오늘 몫 바로 배정 |
| 할 일 삭제 | 관리자 | `POST /admin/todo/{id}/delete` | 확인 모달. 목록·자동 배정에서 빠지고(deleted), 오늘부터 아직 제출하지 않은 배정은 함께 삭제. 지난 제출·검수 기록은 남음 |
| 할 일 단위 댓글 | 관리자 | `POST /admin/todo/{id}/comment` | 할 일 상세 화면 아래. @아이디 멘션 알림 (관리자는 할 일 상세로, 직원은 내 할 일로) |
| 보고서 양식 수정 | 관리자 | `GET/POST /admin/report/{id}/edit` | 기존 항목은 `fieldIds` 로 구분. 지운 항목은 낸 답이 있으면 숨김 → 이미 제출된 보고서·엑셀 그대로. 바꾼 결재선은 다음 제출부터 |
| 검수 통과 실행 취소 | 검수자 · 관리자 | `POST /todo/{id}/review/undo` | 검수 통과 후 10분 안, 검수한 사람(또는 관리자). 다시 '검수 대기중', 직원에게 취소 알림 |
| 승인 실행 취소 | 결재자 | `POST /report/{id}/decide/undo` | 승인 후 10분 안, 다음 결재자가 처리하기 전까지. 내 단계가 다시 '대기', 작성자·다음 결재자에게 취소 알림 |

DB 변경 (ddl-auto update 로 자동 추가, 기본값 false) : `todo_task.deleted`, `todo_item.archived`, `report_field.archived`, 새 테이블 `todo_task_comment`.

---

## 이슈 고도화 · 사내 채팅 (2026-11-23 추가)

> 이 절이 위의 이슈 설명(`OPEN`, 처리 대기)보다 우선해요.

### 이슈 상태

| enum | 라벨 | 다음 단계 (칩 메뉴) |
|---|---|---|
| `REPORTED` | 보고 완료 | 검토 중 |
| `REVIEWING` | 검토 중 | 처리 대기 중, 처리 중, 해결 완료 |
| `WAITING` | 처리 대기 중 | 처리 중 |
| `IN_PROGRESS` | 처리 중 | 해결 완료 |
| `RESOLVED` | 해결 완료 | 검토 중 (재오픈) |

| 엔드포인트 | 권한 | 내용 |
|---|---|---|
| `POST /board/post/{id}/issue/status` | 관리자 | `status`, `memo`, `resolution`(해결 완료 때 필수) |
| `POST /board/post/{id}/issue/start` | 볼 수 있는 직원 | 해결 전 → 처리 중 |
| `POST /board/post/{id}/issue/resolve` | 볼 수 있는 직원 | 해결 내용 + 첨부 |
| `POST /board/post/{id}/issue/reopen` | 해결자·글쓴이·매니저·관리자 | 해결 내용·첨부를 비우고 검토 중으로 |
| `POST /board/post/{id}/issue` | 볼 수 있는 직원 | 예전 폼 호환 (`OPEN` 은 보고 완료로) |
| `GET /board/post/{id}/files/{name}` | 글을 볼 수 있는 사람 | 첨부 내려받기 |
| `GET /admin/issues/export?formats=excel\|zip&board=&place=&status=&category=&from=&to=` | 관리자 + 다운로드 스위치 | 현재 필터 그대로 |

- 목록 `GET /board/{id}` 필터 : `place`, `status`(비움 / `PENDING` 해결 외 전체 / 단계 이름, 예전 `OPEN` 은 `PENDING`), `category`, `from`, `to`.
- 목록·상세의 **링크 복사** 버튼, 권한이 없는 링크는 403 안내 화면. 상세 타임라인에 상태 이력 단계 점.

### 채팅 화면 · API

| 메서드 · 주소 | 설명 |
|---|---|
| `GET /chat`, `GET /chat/rooms/{id}` | 목록 + 대화창 (모바일은 한 단) |
| `GET /chat/rooms/list?q=&active=` | 목록 조각 HTML (검색·실시간 갱신) |
| `GET /chat/rooms/{id}/messages?beforeId=&afterId=` | JSON : 이전 50개 / 빠진 메시지 + 참여자 읽음 위치 |
| `POST /chat/rooms/{id}/messages` | multipart : `clientMessageId`(필수), `content`(2000자), `mentionIds`, `files`(10개, 개당 20MB) |
| `POST /chat/rooms/{id}/read` | `lastReadMessageId` |
| `POST /chat/rooms/{id}/mute` · `/leave` | 알림 끄기(`muted`) · 나가기 (매장·팀·공지 불가) |
| `POST /chat/rooms/{id}/inquiry/close` | 문의 종료 (담당자) |
| `POST /chat/direct/{memberId}` · `POST /chat/group` | 1:1 열기 · 그룹 만들기 (`name`, `memberIds`) |
| `GET /chat/inquiry` · `POST /chat/inquiry/{topicId}` | 문의 주제 고르기 · 문의 방 열기 |
| `GET /chat/unread` · `GET /chat/attachments/{id}` | 전체 안 읽은 수 · 첨부 (참여자만) |
| `GET /members/{id}/card` | 프로필 카드 조각 (사진, 이름, 직급, 근무지, 근무 상태, 근무제 칩, 채팅 버튼) |
| `/ws` (STOMP) | 구독 `/topic/rooms/{id}`, `/user/queue/chat`. CONNECT 에 CSRF 헤더 |
| `GET /admin/chat` · `POST /admin/chat/notice` | 채널 현황·통계, 전체 공지 |
| `GET·POST /admin/chat/topics`, `POST /admin/chat/topics/{id}/toggle` | 문의 주제·담당자 |
| `GET /admin/chat/inquiries?topic=&status=`, `POST .../{id}/open`, `.../{id}/close` | 문의함 |
| `GET /admin/monitor?status=&type=&outboxPage=&jobPage=` | 재처리 모니터 : 상태·종류 필터, 이벤트·배치 기록 각각 페이지 이동 |

- 사이드바 **채팅**(안 읽은 수, 알림 끈 방 제외), 관리자 **채팅 관리**. 다른 화면에서는 토스트.
- 근무제 칩 : `fragments/ui :: policyChip(m)` (직원 목록, 프로필 카드, 채팅, 스케줄).
- 설정 : `app.chat.max-file-mb`(20), `app.chat.max-files`(10).
