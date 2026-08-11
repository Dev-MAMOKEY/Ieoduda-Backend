# "계획(Plan) 도메인 → AI 채팅 기반" 리팩터링 보고서

작성 기준: 2026-08-10. 브랜치 `fix#12-sujong`.

## 1. 배경 (왜 바꿨는지)

기존 흐름은 "새 계획 만들기" 화면에서 계획 이름·대기기간·구조화된 선택값(SNS 처리, 부고 전달 방식 등)을
한 번에 입력받아 계획(Plan)을 생성하고, 그 밑에 3개 구역(LifeArea)을 미리 만든 뒤 항목(Item)을 채우는 방식이었다.

방향이 바뀌어서, 이제는 **사용자가 그냥 AI와 자유롭게 채팅**하면("민수한테 SNS 정리 부탁하고 아내한테는
사진첩 위치 알려주고 싶어요") AI가 그 문장을 읽고 대상별·주제별로 알아서 나눠 항목화한다. "계획 만들기"라는
화면/입력 폼 자체가 없어진다.

## 2. 최종 확정된 모델

| 개념 | 단위 | 비고 |
|---|---|---|
| 사망확인·증빙검토·발송 파이프라인 | **사용자(Plan) 1개** | death_confirmers/evidences/release_cases 등 기존 11개 테이블 그대로 유지 |
| 대화 세션 (Conversation) | **여러 개** | "처음 계획 채팅" vs "나중에 수정하러 들어온 채팅"을 구분해서 기록 |
| 구역 분류 (LifeArea) | **카테고리당 여러 개 가능** | 세션/턴마다 새로 생길 수 있음 (find-or-reuse 아님) |
| 항목 (Item) | **Plan 전체에 계속 누적** | 조회는 항상 Plan+카테고리 기준으로 집계 |

```
User 1 ─── Plan 1  (회원가입 시 자동 생성, 이름/대기기간 없음 - 순수 앵커)
             │
             ├── Conversation 1 (세션 1) ─── LifeAreaMessage (대화 로그)
             │        └── LifeArea(FAMILY) ─┐
             │        └── LifeArea(WORK)   ─┼─ Item (targetName, action, disclosureScope, status...)
             │                              │
             ├── Conversation 2 (세션 2) ─── LifeAreaMessage (대화 로그)
             │        └── LifeArea(FAMILY) ─┘  ← 같은 카테고리라도 세션마다 새 행
             │
             └── (death_confirmers / evidences / release_cases / ... 기존 구조 그대로)
```

## 3. 왜 "Plan 테이블"은 완전히 없애지 않았나

처음엔 `users` + `items` + `ai_history`만 남기고 `plans`를 통째로 없애는 방향까지 검토했다.
하지만 실제 ERD를 보면 `plans.plan_id`를 FK로 물고 있는 테이블이 11개
(`death_confirmers`, `release_cases`, `evidences`, `objections`, `dispute_contacts`, `handoff_checks`,
`email_logs`, `item_dependencies`, `handover_stages`, `plan_versions`, `role_assigness`)나 있었다.
이건 "사망확인 → 증빙검토 → 대기 → 이의제기 → 발송"으로 이어지는 전체 파이프라인의 앵커라서,
없애면 이 11개 테이블 전부를 뜯어고쳐야 했다. 그래서 **Plan은 남기되 "사용자가 만드는 것"에서
"회원가입 시 자동 생성되는, 아무 입력값 없는 순수 연결 다리"로 성격만 바꿨다.**

## 4. 파일별 변경 내역

### 4.1 삭제한 파일

| 파일 | 삭제 이유 |
|---|---|
| `dto/PlanCreateRequest.java` | "계획 만들기" 폼 자체가 없어짐 |
| `dto/PlanUpdateRequest.java` | Plan에 수정할 필드(이름/대기기간)가 안 남음 |
| `dto/SnsAction.java`, `ObituaryDelivery.java`, `WorkAccountAction.java`, `OngoingWorkHandover.java` | 구조화된 선택값 입력 자체가 없어짐 (전부 자유 텍스트 채팅으로 대체) |
| `dto/CompilationResponse.java` | `LifeArea.aiStructuredResult` 캐시가 없어져서 재조회할 대상이 없음 |
| `dto/InitialStructureResult.java` | 1회성 초기 구조화 API(`getInitialStructure`)가 없어짐 |
| `service/LifeAreaConversationService.java`, `controller/LifeAreaConversationController.java` | `ConversationService`/`ConversationController`로 대체 (세션 개념 반영) |

### 4.2 새로 만든 파일

| 파일 | 역할 |
|---|---|
| `repository/ConversationRepository.java` | 대화 세션 저장소 |
| `service/ConversationService.java` | 세션 시작 + 채팅 턴 처리 (아래 5번에서 상세 설명) |
| `controller/ConversationController.java` | `/api/plans/{planId}/conversations/**` |
| `dto/ConversationResponse.java` | 세션 응답(id, 시작 시각) |
| `dto/ItemResponse.java` | 기존 `LifeAreaTurnResponse` 내부에 중첩돼 있던 걸 독립 DTO로 승격 (여러 곳에서 재사용하기 위함) |

### 4.3 수정한 파일

**`entity/*`** — 이번 세션 전에 이미 팀원이 바꿔둔 상태(내가 손대지 않음):
`Plan`(name/waitingDays 제거), `LifeArea`(rawText/aiStructuredResult/reviewedAt 제거, conversation FK 추가),
`Conversation`(신규), `LifeAreaMessage`(lifeArea FK → conversation FK), `Item`(sortOrder 추가).

**`repository/`**
- `ItemRepository`: `findByLifeArea_LifeIdAndStatus` → `findByLifeArea_Plan_PlanIdOrderByItemIdAsc` / `findByLifeArea_Plan_PlanIdAndStatus` (plan 전체 기준 조회로 변경)
- `LifeAreaMessageRepository`: 전부 `LifeArea` 기준 → `Conversation` 기준으로 교체
- `LifeAreaRepository`: 커스텀 조회 메서드 전부 제거 (이제 Item을 통해서만 조회하므로 `save()`만 필요)
- `PlanRepository`: `findByUser_UserId` 추가 (아래 4.4 참고)

**`service/`**
- `PlanService`: `create()`/`update()`/`createInitialItems()` 등 전부 제거. `getPlan()`/`getMyPlan()`/`deactivate()`만 남김
- `LifeAreaService`: `getLifeAreas()`를 "Item을 plan_id로 전부 가져와서 `disclosureScope` 기준으로 Java에서 그룹핑" 방식으로 재작성. `getLifeArea(단건)`/`getCompilation()` 제거
- `ItemReviewService`: 반환 타입만 `LifeAreaTurnResponse.ItemResponse` → `ItemResponse`로 교체 (로직 변경 없음)

**`controller/`**
- `PlanController`: `POST /api/plans`(생성), `PUT /api/plans/{planId}`(수정), `GET .../compilations/{lifeAreaId}` 제거. **`GET /api/plans/me` 신규 추가** (아래 4.4)
- `LifeAreaController`: `GET .../life-areas/{lifeAreaId}`(단건 조회) 제거 — 카테고리당 행이 여러 개일 수 있어서 단건 조회 자체가 의미 없어짐
- `ItemReviewController`: `ItemResponse` 타입 교체만

**`global/openai/component/OpenAIClient.java`**
- `INITIAL_STRUCTURE_PROMPT` / `getInitialStructure()` 삭제
- `COMPILE_POLICY_PROMPT` 재작성: "이 구역의 초기 선택값" 언급 제거, 대신 "한 발화에 여러 사람/주제가 섞여 있으면 절대 한 항목으로 뭉치지 말고 각각 나눠서 카테고리 판단"을 명시
- `getChatCompletion(history, seedContext)` → `getChatCompletion(history)` (seedContext 파라미터 제거 — 더 이상 구역별 초기 선택값이 없음)

**`domain/account/service/AuthService.java`**
- `signup()` 안에서 `User` 저장 직후 `Plan` 1행을 자동 생성하도록 추가 (아래 4.4)

**`global/exception/ErrorCode.java`**
- `CONVERSATION_NOT_FOUND(404)` 추가

### 4.4 새로 생긴 동작 (기존에 없던 결정)

1. **회원가입 시 Plan 자동 생성**: `POST /auth/signup` 성공 시 `AuthService.signup()`이 `Plan.builder().user(user).build()`를 바로 저장한다. 사용자는 "계획 만들기"를 몰라도 되고, 서버가 알아서 사후 인계 케이스 1개를 붙여준다.
2. **`GET /api/plans/me` 신규 추가**: 프론트가 로그인 직후 자기 `planId`를 알 방법이 없어져서(예전엔 `POST /api/plans` 응답으로 받았음) 추가한 엔드포인트. `@AuthenticationPrincipal`로 식별한 유저의 Plan을 바로 돌려준다.
3. **대화 세션(Conversation) 시작 API**: `POST /api/plans/{planId}/conversations`. 프론트에서 "처음 채팅 시작" 또는 "나중에 수정하러 들어옴" 시점마다 호출해서 새 세션을 만들어야 한다.
4. **PROPOSED 항목 교체 범위가 LifeArea 단위 → Plan 전체 단위로 변경**: 예전엔 같은 구역(LifeArea) 안에서만 미승인 항목을 지우고 새로 채웠는데, 이제 LifeArea가 턴마다 새로 생기기 때문에 "이 Plan 전체에서 아직 검토 안 된(PROPOSED) 항목"을 지우고 이번 턴 결과로 교체하는 방식으로 바뀌었다. 이미 승인/기각한 항목은 그대로 둔다.

## 5. AI 채팅 처리 흐름 (`ConversationService.sendMessage`)

1. `planId` + `conversationId`로 세션을 찾는다 (세션이 이 Plan 소유가 아니면 404)
2. 이 세션 안의 대화 이력 전체를 시간순으로 불러온다 (다른 세션 이력은 안 섞임)
3. 이번 사용자 발화를 저장
4. `OpenAIClient.getChatCompletion(이력 전체)` 호출 — system 프롬프트 하나로 "되묻기(QUESTION)" 또는 "구조화 완료(RESULT)" JSON을 강제
5. AI 응답을 원문 그대로 저장 (다음 턴에도 이력으로 다시 전달됨)
6. 파싱해서 `type`이 `RESULT`면:
   - 이 Plan에서 아직 PROPOSED 상태인 이전 항목을 전부 삭제
   - 응답에 담긴 항목들을 `disclosureScope`(FAMILY/WORK/RELATIONSHIP) 기준으로 그룹핑하면서, 그룹당 이번 턴 전용 `LifeArea` 행을 하나씩 새로 만들고 그 밑에 `Item`을 저장
   - 한 발화에 여러 사람이 섞여 있어도(민수-SNS, 아내-사진첩) 각각 별도 `Item`으로, 카테고리가 다르면 서로 다른 `LifeArea`로 나뉜다
7. `QUESTION`이면 질문만 반환하고 아무것도 저장하지 않음

## 6. API 변경 요약

| 이전 | 이후 | 비고 |
|---|---|---|
| `POST /api/plans` (구조화 폼 제출) | ❌ 삭제 | 회원가입 시 자동 생성으로 대체 |
| — | `GET /api/plans/me` (신규) | 로그인 직후 내 planId 조회 |
| `PUT /api/plans/{planId}` | ❌ 삭제 | Plan에 수정할 값이 없음 |
| `GET /api/plans/{planId}/compilations/{lifeAreaId}` | ❌ 삭제 | 캐시 필드 제거로 무의미해짐 |
| `GET /api/plans/{planId}/life-areas/{lifeAreaId}` | ❌ 삭제 | 카테고리당 행이 여러 개라 단건 조회 무의미 |
| `GET /api/plans/{planId}/life-areas` | 유지, **응답 의미 변경** | 이제 항상 최신 집계(카테고리별 전체 항목)를 반환 — 예전의 "items: [] 버그"가 구조적으로 해결됨 |
| `.../life-areas/{lifeAreaId}/messages` (GET/POST) | `.../conversations/{conversationId}/messages` (GET/POST) | 구역별 대화 → 세션별 대화로 전환 |
| — | `POST /api/plans/{planId}/conversations` (신규) | 새 대화 세션 시작 |
| `POST /api/plans/{planId}/items/review` | 그대로 | 응답 DTO 이름만 `ItemResponse`로 정리 |
| `PUT /api/plans/{planId}/items/{itemId}` | 그대로 | 응답 DTO 이름만 `ItemResponse`로 정리 |

## 7. 검증한 것

- `./gradlew compileJava` / `compileTestJava` 성공 (리팩터링 시작 시점엔 13개 컴파일 에러가 있었음)
- `./gradlew bootRun`으로 실제 기동 확인: Hibernate `ddl-auto=update`가 기존 로컬 PostgreSQL(`ieoduda` DB)에 `conversation` 테이블 신규 생성, `items.sort_order`/`life_area_messages.conversation_id`/`life_areas.conversation_id` 컬럼 추가, `plans` 유니크 제약 재적용까지 에러 없이 마쳤고 정상 기동됨

## 8. 아직 안 건드린 것 (다음에 결정/작업 필요)

- **대기기간(waiting_days)을 어디에 저장할지 미확정.** 이전 대화에서 "대기기간·상태는 아이템마다 다르다"고 하셨는데, 이번 리팩터링 범위에서는 반영하지 않았다. `Item`에 컬럼을 추가할지, 또는 다른 개념(발송 스케줄)으로 분리할지 다음 논의가 필요하다.
- **`Item.status`가 검토 상태(PROPOSED/APPROVED/REJECTED)와 발송 상태를 같이 표현할지도 미정.** 지난 대화에서 "같은 status 컬럼에 검토상태랑 발송상태를 같이 넣을 거냐, 별도 컬럼으로 분리할 거냐"를 여쭤봤는데 아직 답을 못 받아서 손대지 않았다.
- **`Recipient`(담당자) 도메인은 여전히 Repository/Service/Controller가 없다.** 엔티티(`Recipient`, `RoleType`, `AcceptanceStatus`)만 있고 실제 등록 API는 미구현 상태 — 화면 캡처에 나온 "역할별 담당자 등록하기" 기능은 이번 범위 밖.
- **JWT 인증 필터(`JwtAuthenticationFilter`) 미구현, `SecurityConfig` 전체 permitAll** — 기존부터 알려진 이슈, 이번에도 안 건드림.
