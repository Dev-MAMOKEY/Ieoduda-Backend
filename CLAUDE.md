# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**이어주다(늑대와 함께 춤을)** — 사용자가 생전에 가족/업무/관계 처리 의도를 계획(Plan)으로 남겨두면, 사망이 안전하게 확인된 뒤(확인자 2명 독립 신고 + 외부 파트너 승인 + 7~30일 대기 + 이의 제기 없음, 4중 검증) 관련자들에게 정해진 순서로 이메일을 보내는 백엔드 서비스. Java 21 / Spring Boot 4.1 / PostgreSQL / OpenAI / AWS S3.

기능 요구사항의 원천은 `Docs/` 아래 문서다(`ieojuda_기능명세서.md`, `ieojuda_backend_build_order.md`). 이 저장소 밖(Notion, Figma)에도 명세가 있으며 계속 갱신되므로, 명세와 실제 코드가 어긋나 보이면 추측하지 말고 사용자에게 확인할 것.

## Commands

```bash
./gradlew compileJava          # 컴파일만 (JDK 21 필요 — 로컬 환경이 못 찾으면 -Dorg.gradle.java.home 지정)
./gradlew test                 # 전체 테스트 (JUnit 5)
./gradlew test --tests "com.mamoki.ieojuda.domain.plan.service.PlanServiceTest"   # 단일 테스트 클래스
./gradlew test --tests "com.mamoki.ieojuda.domain.plan.service.PlanServiceTest.methodName"  # 단일 테스트 메서드
./gradlew bootRun              # 로컬 서버 기동 (포트 8080)
```

- 실행 전 저장소 루트에 `.env` 필요 (`.env.example` 참고): `DB_URL/DB_USERNAME/DB_PASSWORD`, `OPENAI_API_KEY`, `MAIL_USERNAME/MAIL_PASSWORD`, `AWS_S3_*`, `JWT_SECRET`, `APP_BASE_URL`, `APP_CONTACT_EMAIL`. 없으면 부팅 자체가 실패한다.
- `spring.jpa.hibernate.ddl-auto=update` — 로컬 PostgreSQL 스키마가 엔티티 변경에 따라 자동 갱신된다. 운영 프로파일 분리 규칙은 `Docs/spring-boot-rules.md` 참고.
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`.

## Architecture

### 도메인형 패키지 구조

`domain/{도메인}/{controller,service,repository,dto,entity}` 형태를 따른다. 전 도메인 공통 코드는 `global/`에 있다: `global/exception`(CustomException/ErrorCode/GlobalExceptionHandler), `global/rsdata`(공통 응답 `RsData<T>`), `global/jwt`(발급/검증/필터), `global/config`(SecurityConfig/AppProperties/Swagger), `global/openai`, `global/email`, `global/storage`(S3), `global/consent`(필터).

현재 도메인: `account`, `plan`, `recipient`, `confirmer`, `stage`, `handoffcheck`, `releasecase`, `evidence`, `partner`, `postaccess`, `audit`, `rolecheck`.

### 사망확인 파이프라인 (전체 개념 모델)

```
User 1 ─── Plan 1  (회원가입 시 AuthService.signup()이 자동 생성 — 이름/대기기간 없는 순수 앵커)
             │
             ├── Conversation N (대화 세션, 여러 개) ─── LifeAreaMessage (대화 로그)
             │        └── LifeArea(FAMILY/WORK_CONTINUITY/RELATIONSHIP) — 세션/턴마다 새로 생김
             │                └── Item (targetName, action, disclosureScope, status: PROPOSED/APPROVED/REJECTED)
             │
             └── death_confirmers / evidences / release_cases / objections / dispute_contacts / ...
                 (사망확인 → 증빙검토 → 대기 → 이의제기 → 발송 파이프라인, Plan을 FK 앵커로 공유)
```

- **Plan은 사용자가 만드는 게 아니라 회원가입 시 자동 생성되는 앵커**다. "계획 만들기" 폼/API는 없다 — `POST /api/plans` 같은 건 만들지 말 것. 사용자는 AI와 자유 채팅(`ConversationService.sendMessage`)만 하고, AI가 발화를 대상별/주제별로 나눠 `LifeArea`+`Item`으로 구조화한다.
- **AI 구조화 트리거는 `LifeAreaCategory`(FAMILY/WORK_CONTINUITY/RELATIONSHIP) 판단 결과지, 입력 필드가 아니다.** 한 문장에 여러 사람/주제가 섞여 있으면 각각 별도 `Item`으로, 카테고리가 다르면 별도 `LifeArea`로 나뉜다 (`global/openai/component/OpenAIClient` 프롬프트가 이를 강제).
- **PROPOSED 항목 교체 범위는 Plan 전체 단위.** AI가 새 턴에서 구조화 결과를 낼 때마다 이 Plan의 미검토(PROPOSED) 항목을 전부 지우고 새로 채운다. APPROVED/REJECTED는 건드리지 않는다.
- 대기기간(`releasecase`)은 7~30일 범위로 검증된다(최근 커밋 `[Fix/#50]` 참고).

### 계층 규칙 (Docs/spring-boot-rules.md 요약 — 상세는 원문 참고)

- 생성자 주입만 사용 (필드 `@Autowired` 금지). 의존 방향은 Controller → Service → Repository.
- Controller는 Entity를 직접 주고받지 않는다 — 항상 DTO. 비즈니스 로직 없이 검증·위임·응답 변환만.
- 트랜잭션 경계는 Service. 조회는 `@Transactional(readOnly = true)`.
- 새 예외는 `global/exception`의 `CustomException`/`ErrorCode` 패턴을 따르고, 필요 시 `GlobalExceptionHandler`에 핸들러를 추가한다. 공통 응답은 `RsData.success(data)` / `RsData.fail(errorCode)`.
- Entity의 기본 생성자는 `protected`, Setter 대신 의미 있는 상태 변경 메서드(`member.withdraw()` 등)를 둔다. Lombok `@Data` 금지.
- 연관관계 기본은 LAZY. `@ManyToOne`/`@OneToOne`엔 `fetch = FetchType.LAZY` 명시.

### 인증/인가

- JWT 기반, `JwtAuthenticationFilter` → `ConsentCheckFilter` 순서로 적용. Access 1시간 / Refresh 14일(둘 다 회전).
- `SecurityConfig`의 경로 그룹: `PERMIT_ALL_PATHS`(auth, swagger, 이메일 링크 기반 검증/수락 엔드포인트), `ADMIN_ONLY_PATHS`(`/api/admin/**`, `/api/release-cases/**`), `ADMIN_OR_EXTERNAL_PATHS`(`/api/evidence/**`, `/api/partner/**` — 운영자+외부 파트너 공용), 나머지는 인증 필요.
- CORS는 origin `*` 허용 상태(개발 단계, `allowCredentials=false`라 안전) — 배포 도메인이 정해지면 좁혀야 한다.

## Docs 참조

- `Docs/spring-boot-rules.md` — Spring 계층별 상세 규칙 (원문 우선, 위 요약은 참고용)
- `Docs/tech-stack.md`, `Docs/ieojuda_기능명세서.md`, `Docs/ieojuda_backend_build_order.md`
- `Docs/Issue_PR_Template.md` — 이슈/PR 작성 시 이 템플릿 형식(목적/범위(포함·제외)/작업 내용/완료 조건, 연관 Issue/변경 사항)을 따른다
- 커밋 메시지 컨벤션: `[유형/#이슈번호] 설명` (예: `[Fix/#58] Fix: 입력 크기 제한과 DB 스키마 정합성 개선`, 유형은 Feat/Fix/Setting/Test 등)
- `HANDOFF.md`, `REFACTOR_REPORT.md`는 과거 세션 인계 문서로 **작성 시점 스냅샷**이다. 현재 코드 상태와 다를 수 있으니(예: 두 문서 모두 SecurityConfig가 permitAll이라고 적혀 있지만 실제로는 JWT 인증이 구현돼 있음) 사실 확인은 항상 실제 코드 기준으로 한다. 다만 설계 결정의 배경(왜 Plan을 안 없앴는지 등)을 파악할 때는 참고할 가치가 있다.

## 반드시 지킬 것

- **Git 조작(commit, push, branch 생성/삭제 등)을 절대 하지 말 것** — 프로젝트 CLAUDE.md 규칙.
- 모든 기능은 `Docs/` 문서 기반. 명세에 없는 기능을 임의로 추가/삭제하지 않는다.
- 확실하지 않으면 구현 전에 먼저 물어볼 것.
- Controller/DTO 작업 시 Swagger 어노테이션을 함께 작성한다.
