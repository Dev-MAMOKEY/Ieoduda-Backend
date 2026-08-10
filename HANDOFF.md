# 이어주다(늑대와 함께 춤을) 백엔드 — 세션 인계 문서

작성 기준: 2026-08-09. 이전 세션에서 진행한 작업과 결정사항, 남은 일을 정리합니다.

## 1. 프로젝트가 뭔지

사용자가 생전에 가족/업무/관계 처리 의도를 계획으로 남겨두면, 사망이 안전하게 확인된 뒤(확인자 2명 독립 신고 + 외부 파트너 승인 + 7~30일 대기 + 이의 제기 없음, 4중 검증) 관련자들에게 정해진 순서로 이메일을 보내는 서비스. Next.js + Spring Boot + PostgreSQL + OpenAI. 기능명세서는 Notion에 있음(30개 페이지, 팀에서 계속 갱신 중이니 재확인 필요하면 다시 읽을 것).

## 2. 지금 실제로 동작하는 API (라이브로 검증 완료)

**Plan / LifeArea / AI 구조화 / Item 검토** (`domain/plan`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/plans` | 계획 생성. `familyMessage`(자유텍스트) + `snsAction`/`snsOtherDetail`/`obituaryDelivery`/`workAccountAction`/`ongoingWorkHandover`(버튼값)를 한 번에 받아서, **AI가 내용을 읽고** 가족/관계정리/업무연속성 3구역에 자동 분류 + Item(PROPOSED)까지 생성. 응답에 `lifeAreas[].items[]`까지 포함 |
| GET | `/api/plans/{planId}` | 계획 조회 |
| PUT | `/api/plans/{planId}` | 계획 수정(이름/대기기간) |
| POST | `/api/plans/{planId}/deactivate` | 계획 비활성화 |
| GET | `/api/plans/{planId}/life-areas` | 구역 목록 (⚠️ 버그 있음, 아래 5번 참고) |
| GET | `/api/plans/{planId}/life-areas/{lifeAreaId}` | 구역 단건 (⚠️ 동일 버그) |
| GET | `.../life-areas/{lifeAreaId}/messages` | AI 대화 이력 조회 |
| POST | `.../life-areas/{lifeAreaId}/send/message` | 사용자 발화 → AI 구조화(QUESTION/RESULT) |
| POST | `/api/plans/{planId}/items/review` | 항목 승인/기각 (`{"itemId":1,"decision":"APPROVE"}`) |
| PUT | `/api/plans/{planId}/items/{itemId}` | 항목 인라인 수정 |

**Auth** (`domain/account`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/auth/signup` | 회원가입 (email/password/passwordConfirm/name) |
| POST | `/auth/login` | 로그인 → AT(1시간)/RT(14일) 발급 |
| POST | `/auth/refresh` | RT로 AT+RT 재발급 (RT도 매번 회전) |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 3. 핵심 설계 결정 (왜 이렇게 만들었는지)

- **계획 생성 = AI 분류 방식.** 처음엔 "어느 입력 필드에 썼는지"로 구역을 고정 배정했는데(`familyMessage`→FAMILY 등), 가족 칸에 업무 얘기를 써도 무조건 FAMILY로 들어가는 문제가 있어서, **AI가 실제 내용을 읽고 판단**하는 방식으로 바꿈. `OpenAIClient.getInitialStructure()` + `PlanService.createInitialItems()`. 라이브 테스트로 검증 완료(가족 칸에 업무 얘기 섞어 써도 정확히 WORK_CONTINUITY로 분류됨).
- **LifeArea.rawText**는 새 컬럼 안 만들고 기존 필드를 재사용 — 3구역 모두에 입력값 전체(JSON)를 동일하게 저장해서, 나중에 "삶의 구역 생성" 채팅에서도 AI가 전체 맥락을 계속 참고하게 함(`OpenAIClient.getChatCompletion(history, seedContext)`의 seedContext).
- **PROPOSED 항목 교체 로직**: 화면 전환 없이 같은 대화창에서 계속 대화하는 구조라, AI가 계획을 다시 짤 때마다 이전 PROPOSED 항목은 지우고 새로 교체함(`LifeAreaConversationService.createItems()`). APPROVED/REJECTED는 안 건드림.
- **Item.targetName**: AI 구조화 결과 검토 화면에 "대상 이름"(예: 김민수, 아내)을 보여주려고 추가한 필드. AI 프롬프트에서 항상 같이 뽑도록 지시함.
- **PlanCreateRequest.userId는 임시 필드.** 로그인 기능이 없어서 어쩔 수 없이 직접 받고 있음. **나중에 SecurityConfig 손볼 때 이 필드를 로그인한 사용자로 대체하기로 사용자와 합의됨.**
- **SecurityConfig는 의도적으로 전체 permitAll 유지.** JWT는 발급/검증 로직(`JwtTokenProvider`)과 회원가입/로그인 API까지만 만들었고, `JwtAuthenticationFilter`로 실제 요청을 막는 건 아직 안 함 — 나중에 한 번에 정리하기로 함(위 userId 이슈랑 같이).

## 4. 알려진 문제 / 미해결 이슈

1. **`GET .../life-areas`, `GET .../life-areas/{id}`가 항상 `items: []`를 반환함.** `LifeAreaService.getLifeArea()`가 `LifeAreaResponse.from(lifeArea)`(items 없는 오버로드)만 써서 그럼. 계획 생성 직후 응답에서만 items가 채워지고, 그 뒤 재조회하면 못 봄. **고치기로 얘기만 하고 아직 안 고침.**
2. **관계정리/업무정리 구역 버튼값만 있고 텍스트가 없으면 AI가 항목을 아예 안 만드는 경우가 있음.** 예: `snsAction: "PRIVATE"`만 있으면 "누가 이걸 해야 하는지(targetName)" 정보가 없어서 AI가 항목화를 포기함. `PlanCreateRequest`에 담당자 이름 받는 필드 자체가 없음(그건 "역할 담당자 등록" 화면 몫인데 미구현). **결론 안 남, 사용자한테 다시 물어봐야 함.**
3. **`snsOtherDetail` 필드가 사실상 안 쓰임.** "기타" 선택 옵션은 안 쓰기로 했는데 DTO 필드는 아직 안 지움.
4. **AI 질문에 빠른 답변 버튼(quick-reply, 예: 삭제/비공개) 기능 없음.** `LifeAreaTurnResponse`에 `options` 필드 없음. **사용자가 명시적으로 "나중에 하자"고 보류함.**
5. **충돌 점검/발송 순서(AI 순서 제안) 기능은 한 번 만들었다가 전부 롤백함.** 사용자가 "이거 피그마에 있던 화면이었어?"라고 물었는데 없었다고 확인되자 삭제 요청. `domain/stage`의 `Dependency`/`HandoverStage` 엔티티만 남아있고, 관련 Service/Controller/DTO는 없음.
6. **OpenAI(`gpt-3.5-turbo`)가 간헐적으로 500 에러를 냄** (테스트 중 3번에 1번꼴 확인). 재시도 로직 없음.
7. **로그인(`LoginRequest`)엔 `@Email` 형식 검증이 없고, 회원가입(`SignupRequest`)엔 있음.** 일관성 맞출지 논의만 하고 결론 안 남.
8. **Repository가 5개뿐**(`User`, `Plan`, `LifeArea`, `LifeAreaMessage`, `Item`). 나머지 12개 엔티티(`Recipient`, `Confirmer`, `DisputeContact`, `Evidence`, `ExternalPartner`, `PartnerReviewer`, `ReleaseCase`, `Objection`, `Dependency`, `HandoverStage`, `AccessToken`, `PackageIssue`, `EmailLog`, `HandoffCheck`, `HandoffCheckResponse`, `Consent`)는 JPA 엔티티만 있고 Repository/Service/Controller가 아예 없음.
9. **팀원이 만든 인프라 유틸리티는 완성도 있는데 아무데서도 안 씀**: `global/email`(GmailSender, RetryPolicy, FailureAnalyzer, EmailBuilder — 실제 SMTP 발송·반송분류·재시도판단 로직 다 있음), `global/email/token`(TokenProvider/TokenValidator — 초대 링크용, 내가 만든 `global/jwt`와는 다른 것, 이름 헷갈리지 말 것), `global/storage`(S3EvidenceStorageClient — 실제 S3 업로드/다운로드 구현체). 이걸 실제로 호출하는 도메인 서비스가 없음.
10. **`.env`에 `AWS_S3_BUCKET`/`AWS_S3_REGION`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`MAIL_USERNAME`/`MAIL_PASSWORD`가 원래 없어서 앱 부팅 자체가 실패했었음.** AWS는 사용자가 실제 IAM 액세스 키를 발급받아 `.env`에 넣어서 **진짜 값으로 채워짐**(버킷 `ieoduda2026-311752058218-ap-northeast-2-an`, 리전 `ap-northeast-2`). 단, `EvidenceStorageClient`를 실제로 호출하는 도메인 서비스가 아직 없어서 저장/조회 기능 자체는 미구현 상태(키만 준비됨). **`MAIL_USERNAME`/`MAIL_PASSWORD`는 아직 더미값** — 실제 메일 발송 기능 붙일 때 교체 필요.
11. **보안 유의**: 사용자가 대화 중 AWS 콘솔 로그인 비밀번호를 실수로 채팅에 붙여넣은 적 있음 → 비밀번호 재설정을 권장드렸음(대화 로그에 남으니). 이 문서엔 실제 값 안 적음.

## 5. 다음에 하면 좋을 것 (우선순위 제안)

1. 위 4-1 (`GET life-areas` items 버그) — 작은 수정
2. 담당자 등록(`Recipient`) / 확인자 등록(`Confirmer`) API — Repository/Service/Controller부터 신설. 이게 있어야 4-2 문제(버튼값만 있으면 항목 안 만들어지는 것)도 자연스럽게 풀릴 가능성 있음 (담당자를 먼저 등록해두면 AI가 targetName을 거기서 가져올 수 있음)
3. `JwtAuthenticationFilter` 신설 + `SecurityConfig` 잠그기 + `PlanCreateRequest.userId` 제거하고 로그인 사용자로 대체 (3번 항목에서 이미 이렇게 하기로 합의됨)
4. 이의제기 연락처(`DisputeContact`) API
5. 그 외 명세서 52개 API 중 나머지 (충돌탐지/발송순서, 사망확인~발송 전체 플로우 등)

## 6. 개발 환경

- **DB**: PostgreSQL 18 (Homebrew 아님, `/Library/PostgreSQL/18/bin`), `localhost:5432/ieoduda`, 계정정보는 `.env`
- **컴파일 확인 시 JDK 21 경로를 명시해야 함** (로컬 gradle이 기본으로 못 찾음):
  ```bash
  JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.7/libexec/openjdk.jdk/Contents/Home \
    bash gradlew clean compileJava \
    -Dorg.gradle.java.home=/opt/homebrew/Cellar/openjdk@21/21.0.7/libexec/openjdk.jdk/Contents/Home
  ```
- **서버 실행**: 사용자가 IntelliJ로 직접 실행/재시작함 (Run 버튼). 포트 8080.
- **Postman 컬렉션**: 대화 중 2개(Plan/LifeArea/AI용, Auth용) 만들어서 전달했으나 세션 스크래치패드에 있어 다음 세션에선 접근 불가 — 필요하면 다시 요청받아 만들 것.

## 7. 참고

- Notion 기능명세서: 워크스페이스 내 "늑대와 함께 춤을" 프로젝트 페이지 하위 "기능 명세서" 데이터베이스 (30개 항목). Notion MCP 연결 필요.
- Figma: 사용자가 스크린샷 5장(계획 홈/새 계획 만들기/새 계획 만들기(기타)/삶의 구역 생성/AI 구조화 결과 검토) 공유함. 실제 Figma 파일 링크는 아직 못 받음 — "충돌 점검/발송 순서" 같은 다른 화면들은 본 적 없음.
