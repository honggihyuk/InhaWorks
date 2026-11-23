# InhaWorks · 사내 업무 관리

> 출퇴근, 근무 스케줄, 휴가, 게시판, **반복 할 일(체크리스트)**, **보고서 결재**, 알림을 한곳에서 관리하는 사내 업무 관리 웹앱이에요.
> 매장·팀 단위로 일하는 회사에서 "오늘 할 일을 했는지", "보고서가 어디까지 결재됐는지"를 한눈에 보는 것이 목표예요.
> 알림 같은 후처리는 **Transactional Outbox**, 날짜별 배치는 **실행 기록 + 복구(catch-up)** 로 만들어서 서버가 죽거나 DB 가 잠깐 끊겨도 일이 사라지지 않아요.

<br>

## 목차

1. [프로젝트 소개](#1-프로젝트-소개)
2. [개발 기간 · 개발자](#2-개발-기간--개발자)
3. [개발 환경 · 기술 스택](#3-개발-환경--기술-스택)
4. [주요 기능](#4-주요-기능)
5. [프로젝트 구조](#5-프로젝트-구조)
6. [설치 및 실행 방법](#6-설치-및-실행-방법)
7. [사용 방법](#7-사용-방법)
8. [버전 및 업데이트 정보](#8-버전-및-업데이트-정보)
9. [알려진 문제 · FAQ](#9-알려진-문제--faq)
10. [참고 및 출처 · 저작권](#10-참고-및-출처--저작권)

<br>

## 1. 프로젝트 소개

| 항목 | 내용 |
|---|---|
| 이름 | InhaWorks — 사내 업무 관리 |
| 목적 | 직원은 오늘 할 일·보고서·근태를 한 화면에서, 관리자는 완료율·결재 현황을 한눈에 |
| 대상 | 회사 직원(관리자가 계정을 만들어 줘요)과 관리자 |
| 설계 문서 | [`docs/architecture.md`](docs/architecture.md) — 계층 구조, ER, 할 일·결재 흐름, Outbox·재시도·복구, 장애 대응 표 (Mermaid 그림) |

**메뉴 '우리 회사'**

| 메뉴 | 주소 | 한 줄 설명 |
|---|---|---|
| 출퇴근 | `/attendance` | 출근·퇴근 버튼, 최근 한 달 기록 |
| 스케줄 | `/schedule` | 관리자가 배정한 이번 주 근무 |
| 휴가 | `/leave` | 휴가 신청 → 관리자 승인 |
| 게시판 | `/board` | 일반 · 이슈 & 해결 · 팀 전용 게시판, 고정 공지 |
| 할 일 | `/todo` | 오늘 할 일 체크리스트 제출, 검수 대기함 |
| 보고서 | `/report` | 양식 골라 작성, 결재함(`/approvals`) |
| 채팅 | `/chat` | 매장·팀·공지 채널, 1:1·그룹, 관리자 문의, 실시간 |
| 알림 | `/notifications` | 헤더 🔔 안 읽은 개수 |

<br>

## 2. 개발 기간 · 개발자

- **개발 기간** : 2026.11 – 진행 중 (InhaWorks 로 전환)
- **개발자**

| 이름 | 역할 |
|---|---|
| 홍기혁 ([@honggihyuk](https://github.com/honggihyuk)) | 기획, 화면 설계, 백엔드·프론트엔드 개발 |

<br>

## 3. 개발 환경 · 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1 (Spring Framework 7), Spring MVC, Spring Data JPA, Spring Security, Bean Validation |
| 신뢰성 | Transactional Outbox, `@TransactionalEventListener` + `@Async`, `@Scheduled`, Spring 7 `@Retryable` / `@EnableResilientMethods`, `@Version` 낙관적 락 |
| 화면 | Thymeleaf, Thymeleaf Layout Dialect, Bootstrap 5, 직접 만든 CSS (`style.css`), 서명 패드(canvas) |
| 엑셀 | Apache POI (`poi-ooxml`) |
| 데이터베이스 | MySQL 8 (운영), H2 (MySQL 없이 실행 · 테스트) |
| 빌드 | Gradle |
| 기타 | Lombok, Spring Boot DevTools |
| 개발 도구 | IntelliJ IDEA |

<br>

## 4. 주요 기능

### 홈 `/`
- 출근·퇴근 버튼, **오늘 남은 할 일 / 내 결재 대기 / 검수 대기 / 오늘 출근 시각** 요약
- 오늘 할 일, 새 알림, 고정 공지, 최근 글

### 할 일 `/todo` · 관리자 `/admin/todo`
- 관리자가 **반복 할 일**을 만들어요 : 제목, 가이드 글 + 첨부, 주기(**매일 / 매주 요일 / 매월 N일**), 시작·종료일, 대상(**직원** 또는 **근무지(매장) 전체**), 검수자
- 항목 종류 : **예/아니오, 체크박스, 사진, 서명(손가락으로 그리기 → PNG), 객관식, 주관식**
- 매일 00:05 배치가 직원별 하루 할 일을 만들어요 (할 일 + 직원 + 날짜는 하나만)
- 직원은 오늘 할 일을 채워 제출 → 검수자가 **검수 통과** 또는 **재작업 요청(사유)** → 직원에게 알림, 다시 제출
- 대시보드 : 할 일별 **완료율**, 직원별 상태(**제출 완료 / 제출 미완료 / 검수 대기중 / 검수 통과 / 재작업 필요**)
- **푸시** 버튼 : 미완료 직원에게만 다시 알림 (두 번 눌러도 한 번만)
- **마감 알림** : 설정한 시각(`app.todo.deadline-time`, 기본 18:00)이 지나면 미완료 직원에게 하루 한 번
- 댓글 + **@아이디 멘션** 알림

### 보고서 `/report` · 결재함 `/approvals` · 관리자 `/admin/report`
- 관리자 **폼 빌더** : 예/아니오, 체크박스, 이미지, 서명, 객관식, 주관식, **문서(파일)**
- 누가 쓰나(팀 / 매장 / 직원, 비우면 전 직원), 언제 쓰나(주기 + "매주 월요일" 같은 안내 글)
- 분류(**팀별 / 주제별 / 매장별**) + **해시태그**(#주간보고)로 거르기
- **다단계 결재선** : 제출완료 → 검토중 → **검토완료** (어느 단계든 **반려**), 단계마다 알림
- 댓글, 관리자 **엑셀 다운로드**(양식의 모든 제출) · **첨부 zip**

### 게시판 `/board` · 관리자 `/admin/board`
- 일반 게시판, **이슈 & 해결** 게시판, 주제별·**팀 전용** 게시판
- 이슈 5단계 : **보고 완료 → 검토 중 → 처리 대기 중 → 처리 중 → 해결 완료**. 관리자는 상태 칩으로 바꾸고, 단계마다 이력과 글쓴이 알림이 남아요
- 이슈 **링크 복사**, 관리자 **Excel · 첨부 Zip 내보내기**(지점·상태·분류·기간 필터 그대로)
- 첨부 파일, 댓글(@멘션), 관리자 **고정 공지**(전 직원 알림)

### 사내 채팅 `/chat` · 관리자 `/admin/chat`
- 매장·팀·전체 공지 채널 자동 생성·자동 참여, 직접 만드는 1:1·그룹 대화
- 텍스트, 파일·이미지(10개, 개당 20MB), 읽음 표시, **@멘션 자동완성**, 알림 끄기, 나가기
- **실시간** STOMP WebSocket, 끊기면 자동 재연결 + 빠진 메시지 보정, WebSocket 을 못 쓰면 폴링
- 프로필 카드 : 오늘 근무 상태(출근 전 / 근무 중 / 퇴근 / 휴가), 근무제 칩, 채팅 버튼
- **관리자와 채팅하기** : 주제별 문의 방, 관리자 문의함(미답변 / 답변 중 / 종료). 1:1 본문은 관리자도 볼 수 없어요

### 근태 : 출퇴근 · 스케줄 · 휴가
- 출퇴근 : 하루 한 줄 (두 번 눌러도 한 번), 근무 시간 계산
- 스케줄 : 관리자가 주간 표에서 배정 → 직원에게 알림, 직원은 이번 주 근무 확인
- 휴가 : 신청 → 관리자 승인 / 반려 → 알림

### 알림 `/notifications`
- 헤더 🔔 에 안 읽은 개수 (`@ControllerAdvice`), 누르면 해당 화면으로 이동하며 읽음 처리

### 관리자 `/admin`
- **직원 관리** : 등록·수정·퇴사 처리(로그인 불가, 배정 제외), 팀 · 근무지 · 직급 · 권한(직원 / 매니저 / 관리자)
- **팀 · 근무지** 관리
- **재처리 모니터** `/admin/monitor` : Outbox 이벤트 상태별 목록(대기 / 처리 중 / 완료 / DEAD) + **재처리**, 배치 실행 기록 + **재실행**, 누락 배치 복구

### 신뢰성 (자세한 그림은 [`docs/architecture.md`](docs/architecture.md))

| 장치 | 하는 일 |
|---|---|
| Transactional Outbox | 업무 변경과 이벤트를 같은 트랜잭션에 저장 → 알림 유실·헛알림 없음 |
| 빠른 경로 + 느린 경로 | 커밋 직후 `@Async` 처리, 실패·재시작은 `@Scheduled` 가 다시 |
| 재시도 → DEAD → 수동 재처리 | 지수 백오프로 재시도, 최대 횟수 넘으면 DEAD, 모니터에서 다시 |
| 멱등 처리 | 알림 `sourceEventKey`, Outbox `idempotencyKey`, 할 일 (task, member, date) unique |
| 배치 실행 기록 · 복구 | `job_execution` 으로 끝낸 날짜를 기록, 시작 시·주기적으로 빠진 날짜 채우기 |
| `@Retryable` + `@Version` | 동시 검수·결재 충돌, 일시적인 DB 오류를 새 트랜잭션으로 재시도 |
| 업로드 보상 삭제 | DB 가 롤백되면 방금 저장한 파일 삭제 |
| 중복 제출 방지 | 화면마다 숨은 UUID 토큰 (unique) |
| 채팅 유실·중복 방지 | `clientMessageId` unique, Outbox 브로드캐스트, 재연결 시 `afterId` 보정, 화면은 메시지 id 로 중복 제거 |

### 권한

| 권한 | 할 수 있는 일 |
|---|---|
| 직원 (`ROLE_USER`) | 출퇴근, 스케줄·휴가, 게시판, 채팅, 할 일 제출, 보고서 작성 |
| 매니저 (`ROLE_MANAGER`) | + 검수자·결재자·문의 담당자로 지정되면 처리 |
| 관리자 (`ROLE_ADMIN`) | + 관리자 모드(`/admin/**`) 전체, 고정 공지, 이슈 단계 변경·내보내기, 채팅 공지·문의함, 모든 할 일 검수 |

<br>

## 5. 프로젝트 구조

기능마다 패키지를 나누고, 각 패키지 안은 `constant / controller / dto / entity / repository / service` 같은 구조를 따라요.

```
src/main
├── java/com/mysite/sbb
│   ├── SbbApplication.java
│   ├── config         # 보안(관리자/직원 체인, IP 필터), 재시도·비동기, 스케줄링, 예시 데이터
│   ├── common         # 파일 저장(보상 삭제), 반복 규칙, 공통 모델(🔔), 오류 화면, FileRef
│   ├── organization   # 팀, 근무지(매장)
│   ├── member         # 로그인, 내 정보
│   ├── admin          # 직원 관리, 조직, 재처리 모니터
│   ├── todo           # 반복 할 일, 날짜별 배정, 제출·검수·댓글, 대시보드·푸시
│   ├── report         # 보고서 양식(폼 빌더), 제출, 결재, 엑셀·zip
│   ├── board          # 게시판, 글, 이슈 5단계·이력·내보내기, 댓글
│   ├── attendance     # 출퇴근, 스케줄, 휴가
│   ├── chat           # 대화방, 메시지, 읽음·멘션, 문의 채널, 프로필 카드
│   ├── notification   # 앱 안 알림
│   ├── outbox         # Transactional Outbox (저장 · 릴레이 · 처리기)
│   └── batch          # 배치 실행 기록, 할 일 생성 · 마감 알림 · 복구
└── resources
    ├── application.yml, application-h2.yml
    ├── static         # css/style.css, js/inhaworks.js (서명 패드, 폼 빌더)
    └── templates      # layouts, fragments(header, admin, fields, comments), 기능별 폴더
```

```
브라우저 ──▶ Spring Security (관리자 체인 /admin/** · 직원 체인)
         ──▶ Controller ──▶ Service ──▶ Repository (JPA) ──▶ MySQL / H2
                              └─ 같은 트랜잭션 ─▶ outbox_event ─▶ OutboxRelay ─▶ 알림
         ◀── Thymeleaf 템플릿으로 그린 HTML
```

<br>

## 6. 설치 및 실행 방법

### 준비물
- JDK 21
- MySQL 8 (없으면 H2 프로필로 실행)

### 1) 저장소 받기
```bash
git clone <저장소 주소>
cd sbb-InhaWorks
```

### 2-A) MySQL 로 실행
데이터베이스 `inhaworks` 는 없으면 자동으로 만들어져요 (`createDatabaseIfNotExist=true`). 테이블도 JPA 가 만들어요 (`ddl-auto: update`).
```bash
./gradlew bootRun          # Windows : gradlew.bat bootRun
```

### 2-B) MySQL 없이 H2 로 실행
```bash
./gradlew bootRun --args='--spring.profiles.active=h2'
# Windows PowerShell : .\gradlew.bat bootRun --args="--spring.profiles.active=h2"
```
데이터는 프로젝트 폴더의 `data/inhaworks.mv.db` 에 저장돼요. 지우면 처음 상태(예시 데이터)로 돌아가요.

브라우저에서 http://localhost:8080 으로 들어가요.

### 3) 처음 실행하면 만들어지는 계정 (예시 데이터)

| 구분 | 아이디 | 비밀번호 | 이름 · 소속 | 역할 |
|---|---|---|---|---|
| 관리자 | `admin` | `admin1234` | 관리자 · 운영팀 · 인하점 | 관리자 모드, 보고서 2단계 결재자 |
| 직원 | `emp1` | `1234` | 김민수 · 운영팀 · 인하점 · 매니저 | 할 일 검수자, 보고서 1단계 결재자 |
| 직원 | `emp2` | `1234` | 이서연 · 영업팀 · 인하점 · 사원 | 할 일 대상(인하점), 주간 보고 작성 대상(영업팀) |
| 직원 | `emp3` | `1234` | 박지훈 · 영업팀 · 송도점 · 사원 | 주간 보고 작성 대상 |

함께 만들어지는 것 : 팀 2(운영팀, 영업팀), 근무지 2(인하점, 송도점), 할 일 "매장 오픈 체크리스트"(매일, 인하점, 검수 김민수, 6개 항목), 보고서 양식 "주간 업무 보고"(매주 월요일, 영업팀, 결재선 김민수 → 관리자), 게시판 2(사내 공지, 이슈 & 해결), 고정 공지 1.

### 4) 설정 — `src/main/resources/application.yml`

| 설정 | 기본값 | 설명 |
|---|---|---|
| `spring.datasource.username / password` | `root` / `rootroot` | 내 MySQL 계정으로 바꿔요 |
| `app.admin.username / password` | `admin` / `admin1234` | 처음 실행할 때 자동으로 만들어지는 관리자 계정 |
| `app.seed.enabled` | `true` | 팀이 하나도 없을 때 예시 데이터 만들기 |
| `app.upload-dir` | `uploads` | 올린 사진·서명·문서가 저장되는 폴더 |
| `app.todo.deadline-time` | `18:00` | 할 일 마감 알림 시각 |
| `app.outbox.max-retries` / `base-backoff-seconds` | `5` / `10` | Outbox 재시도 횟수, 백오프 시작 간격 |
| `app.batch.catch-up-days` | `7` | 서버가 꺼져 있던 동안 빠진 할 일을 며칠 전까지 채울지 |
| `app.security.admin-allowed-ips` | (비움) | 관리자 접속 허용 IP. 예) `127.0.0.1,0:0:0:0:0:0:0:1,192.168.0.` |
| `app.security.bulk-download-enabled` | `true` | 보고서·이슈 엑셀·zip 일괄 다운로드 허용 |
| `app.chat.max-file-mb` / `max-files` | `20` / `10` | 채팅 첨부 파일 하나 크기, 한 번에 보내는 개수 |

### 5) 테스트
```bash
./gradlew test
```
H2 메모리 DB 로 돌아요 (`src/test/resources/application.yml`).

| 테스트 | 확인하는 것 |
|---|---|
| `RecurrenceRuleTest` | 매일 / 매주 요일 / 매월 N일(말일 처리) / 기간 |
| `TodoAssignmentGeneratorTest` | 생성 배치 멱등, 배치 기록 COMPLETED 건너뛰기·강제 재실행, catch-up |
| `OutboxRetryTest` | 실패 → 재시도 → DEAD → 재처리 → DONE, 알림 핸들러 멱등, 멱등성 키, MANDATORY |
| `RollbackVerificationTest` | 파일·DB·Outbox 롤백, 채팅 전송·이슈 상태 변경 실패 포함 |
| `IssueWorkflowTest` | 이슈 5단계·이력·알림, 데이터 이전, Excel·Zip, 링크 권한 |
| `ChatFlowTest` | 채널 동기화, 멱등 전송, 읽음, 멘션, 문의, 퇴사, 재처리, 권한 |
| `ChatWebSocketSecurityTest` | 실제 WebSocket 으로 로그인·CSRF·구독 권한·실시간 전달 |

<br>

## 7. 사용 방법

1. **관리자 로그인** (`/admin/login`) : `admin` / `admin1234`
2. **팀 · 근무지**와 **직원**을 등록해요 (회원가입은 없어요. 계정은 관리자가 만들어요).
3. **할 일 만들기** : 주기·대상(매장 또는 직원)·검수자·항목을 정하면 오늘 몫이 바로 배정돼요.
4. **직원 로그인** (`/member/login`, 예: `emp2` / `1234`) → **할 일**에서 체크리스트를 채워 제출해요. 사진은 휴대폰 카메라로, 서명은 손가락으로.
5. **검수자**(`emp1`)는 홈의 '검수 대기' 또는 `/todo/review` 에서 통과 / 재작업 요청을 해요.
6. 관리자는 **할 일 대시보드**에서 완료율과 직원별 상태를 보고, 미완료자에게 **푸시**를 보내요.
7. **보고서** : 직원이 양식을 골라 제출 → 결재자들이 **결재함**에서 순서대로 승인 → 관리자는 **엑셀·zip** 으로 받아요.
8. 알림이 안 오거나 배치가 빠진 것 같으면 관리자 **재처리 모니터**에서 확인하고 다시 처리해요.

<br>

## 8. 버전 및 업데이트 정보

| 버전  | 날짜 | 내용                                                                                                                                                                                                   |
|-------|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0.0.1 | 2026.11.19 | **InhaWorks** 출퇴근·스케줄·휴가·게시판·할 일·보고서·알림, 관리자 모드(직원·조직·할 일·보고서·게시판·근태·재처리 모니터), Transactional Outbox·배치 복구·재시도, 테스트 추가 |
| 0.0.2 | 2026.11.23 | **이슈 고도화**(5단계·이력·링크 복사·Excel/Zip), **사내 채팅**(채널·1:1·그룹·문의·실시간), 근무제 칩, 재처리 모니터 확장 |

<br>

## 9. 알려진 문제 · FAQ

**Q. 로그인하지 않으면 아무 화면도 안 보여요.**
사내 시스템이라 로그인 화면만 열려 있어요. 계정은 관리자에게 받아요.

**Q. 알림이 늦게 와요 / 안 와요.**
알림은 업무 처리가 커밋된 직후 따로 만들어져요. 보통 바로 오지만, 실패하면 5초마다 다시 시도해요.
계속 실패하면 DEAD 가 되니 관리자 > **재처리 모니터**에서 '재처리'를 눌러 주세요.

**Q. 서버를 며칠 껐다 켰더니 그동안의 할 일이 없어요.**
서버가 켜질 때 최근 7일(`app.batch.catch-up-days`) 중 빠진 날짜를 자동으로 채워요. 재처리 모니터의 '누락·실패 배치 지금 복구'로도 할 수 있어요.

**Q. 관리자 화면이 403 으로 막혀요.**
`app.security.admin-allowed-ips` 에 지금 내 IP 가 들어 있는지 확인해 주세요. 비워 두면 제한하지 않아요.

**알려진 문제 · 할 수 있는 것**
- 알림은 앱 안 알림만 있어요 (휴대폰 푸시·이메일 없음). Outbox 처리기를 하나 더 만들면 붙일 수 있어요.
- 채팅은 서버 1대 기준(Spring 내장 브로커)이에요. 서버를 늘리려면 외부 STOMP 브로커가 필요해요.
- 관리자 IP 제한은 프록시(X-Forwarded-For) 뒤를 고려하지 않은 가벼운 버전이에요.
- 할 일 양식은 만든 뒤에 항목을 고치는 화면이 없어요 (사용 중지 후 새로 만들기).
- 버그나 제안은 저장소 Issues 에 남겨 주세요.

<br>

## 10. 참고 및 출처 · 저작권

- 화면 스타일 : [Bootstrap](https://getbootstrap.com) (MIT License), 글꼴 [Pretendard](https://github.com/orioncactus/pretendard) (OFL)
- 엑셀 : [Apache POI](https://poi.apache.org) (Apache License 2.0)
- 재시도 : [Spring Framework 7 Resilience (@Retryable)](https://docs.spring.io/spring-framework/reference/core/resilience.html)
- Outbox 패턴 : [microservices.io — Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- 학습 교재 : 「점프 투 스프링부트」 SBB 프로젝트 구조

이 프로젝트는 대학 수업(스프링 프레임워크) 학습을 위해 만들었어요.
