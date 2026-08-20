# 이어두다 (Ieoduda) Backend

늑대와 함께 춤을 팀의 디지털 유산 인계 서비스 백엔드입니다. 사용자가 생전에 계획을 작성해 봉인해 두면,
지정 확인자의 사망 신고와 증빙 검토를 거쳐 담당자에게 항목이 인계되는 흐름을 처리합니다.

## 기술 스택

- Java 21 (Gradle 툴체인)
- Spring Boot 4.1.0 / Spring Security 7.x / Spring Data JPA
- PostgreSQL
- JWT 기반 무상태 인증
- Resilience4j (이메일 발송, S3 재시도/회로차단)
- Gradle 9.5.1 (wrapper 포함)

## 빠른 시작 (Docker)

1. 저장소를 클론하고 `.env.example`을 `.env`로 복사한 뒤, [필수 환경 변수](#필수-환경-변수)를 채웁니다.

   ```bash
   cp .env.example .env
   ```

2. DB와 애플리케이션을 함께 띄웁니다.

   ```bash
   docker compose up --build
   ```

   `db`(PostgreSQL) 서비스가 정상 기동(healthy)된 뒤 `app` 서비스가 시작되며, `http://localhost:8080`에서 API에 접근할 수 있습니다.
   Swagger UI는 `http://localhost:8080/swagger-ui/index.html`에서 확인할 수 있습니다.

3. 종료 시:

   ```bash
   docker compose down
   ```

   DB 데이터를 초기화하려면 볼륨까지 함께 제거합니다: `docker compose down -v`

### 로컬(비Docker) 실행

로컬에 PostgreSQL을 직접 띄워 실행할 수도 있습니다. `.env`의 `DB_URL`이 로컬 DB를 가리키도록 맞춘 뒤:

```bash
./gradlew bootRun
```

## 필수 환경 변수

`.env.example`에 전체 목록과 기본값이 있습니다. 애플리케이션은 `spring.config.import`로 `.env` 파일을 읽으므로,
로컬 개발에서는 저장소 루트에 `.env` 파일을 두면 됩니다(`.gitignore`에 포함되어 커밋되지 않습니다).

주요 항목:

| 구분 | 변수 | 설명 |
|---|---|---|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `POSTGRES_DB` | PostgreSQL 연결 정보 |
| AI | `OPENAI_API_KEY`, `OPENAI_API_URL`, `OPENAI_MODEL` | 대화 기반 계획 구조화에 사용하는 OpenAI 설정 |
| 메일 | `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_HOST`, `MAIL_PORT` | Gmail SMTP 발송 계정 (앱 비밀번호 사용) |
| 저장소 | `AWS_S3_BUCKET`, `AWS_S3_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | 증빙 파일 저장용 S3 자격 증명 |
| 인증 | `JWT_SECRET`(32자 이상), `JWT_ACCESS_TOKEN_EXPIRATION_MS`, `JWT_REFRESH_TOKEN_EXPIRATION_MS` | JWT 서명 키와 만료 시간 |
| 앱 | `APP_BASE_URL` | 이메일 링크가 가리킬 프론트엔드 주소 |

**비밀값 관리**: `.env`는 절대 커밋하지 않습니다. 실제 자격 증명(메일 앱 비밀번호, AWS 키, OpenAI 키, JWT 시크릿)은 팀 내부
채널로만 공유하고, 커밋 전 `git status`로 `.env`가 스테이징되지 않았는지 항상 확인합니다.

## 테스트

전체 테스트 실행 (실제 로컬/Docker PostgreSQL 필요, 외부 서비스는 목 처리되어 있어 `.env`의 값이 더미여도 통과합니다):

```bash
./gradlew test
```

특정 테스트 클래스만 실행:

```bash
./gradlew test --tests "com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReaderTest"
```

PR을 올리면 GitHub Actions([`test.yml`](.github/workflows/test.yml))가 별도 Postgres 컨테이너와 더미 자격 증명으로 전체
테스트를 자동 실행합니다.

## Docker 기반 빌드

이미지만 별도로 빌드하려면:

```bash
docker build -t ieoduda-backend .
```

멀티스테이지 빌드로 `eclipse-temurin:21-jdk-jammy`에서 `bootJar`를 만들고, `eclipse-temurin:21-jre-jammy` 런타임 이미지에
결과 jar만 담아 non-root(`app`) 사용자로 실행합니다.

## 프로젝트 구조

도메인별로 `controller / service / repository / entity / dto`를 두는 구조입니다.

```
src/main/java/com/mamoki/ieojuda/
├── domain/
│   ├── account/        # 회원가입·로그인, 필수 동의(Consent), 관리자 사용자 관리
│   ├── plan/            # 계획 생성, 삶의 구역/항목, AI 대화 기반 구조화, 항목 순서, 봉인(패키지)
│   ├── recipient/       # 역할 담당자 등록·초대·수락
│   ├── confirmer/       # 지정 확인자, 사망 신고, 이의 제기 연락처
│   ├── rolecheck/       # 역할 점검(담당자·확인자 현황) 화면
│   ├── stage/            # 인계 단계·의존성(발송 순서, 충돌 점검)
│   ├── handoffcheck/    # 생전 인계 점검(선택 기능)
│   ├── releasecase/     # 사망 확인 사건, 대기 기간, 사건 취소, 이의 제기
│   ├── evidence/        # 공식 증빙 제출·검토·삭제(관리자 감사 포함)
│   ├── partner/         # 외부 법무·장례 파트너의 증빙 검토
│   ├── postaccess/      # 사후 인계 접근 인증, 패키지 문제 신고
│   ├── securitytoken/   # 공개 링크(이메일 진입)용 목적별 단일 사용 토큰
│   └── audit/            # 이메일·인증·관리자 액션 감사 로그
│
├── global/
│   ├── config/           # SecurityConfig, AppProperties, SwaggerConfig 등
│   ├── consent/          # 필수 동의 미완료 사용자 접근 제한 필터
│   ├── email/            # 이메일 발송(outbox 큐, 발신자, 템플릿, 토큰)
│   ├── exception/        # CustomException, ErrorCode, GlobalExceptionHandler
│   ├── idempotency/      # 멱등성 키 기반 중복 요청 방지
│   ├── jwt/               # JWT 발급·검증 필터
│   ├── openai/           # OpenAI API 클라이언트
│   ├── ratelimit/        # 공개 링크(토큰) 조회 레이트 리밋·감사
│   ├── rsdata/           # 공통 API 응답 포맷(RsData)
│   ├── scan/              # 업로드 파일 악성코드/시그니처 스캔
│   ├── security/         # 권한·재인증 가드
│   ├── storage/          # 증빙 파일 S3 저장소 클라이언트
│   ├── util/              # 공통 유틸(IP 추출, 이메일 마스킹 등)
│   └── validation/       # 계획 내용 내 자격증명 노출 탐지 등 커스텀 검증
│
└── IeojudaApplication.java
```

## 브랜치 · Issue · 커밋 · PR 컨벤션

- **기본 브랜치**: `main`(배포), `develop`(통합) — 작업은 항상 `develop`에서 분기합니다.
- **작업 브랜치명**: `<Type>/#<이슈번호>-<작성자>` (예: `Fix/#150-mingu`, `Feat/#139-mingu`). 이슈에 딸리지 않은
  작업은 이슈 번호 없이 `<Type>/<설명>-<작성자>` 형태를 씁니다(예: `Setting/cors-frontend-origin-sujong`).
  `Type`은 `Feat`/`Fix`/`Refactor`/`Test`/`Docs`/`Setting`/`Chore` 등 변경 성격을 나타냅니다.
- **커밋 메시지**: `[Type/#이슈번호]: 설명` 형식, 한국어 서술형으로 무엇을 왜 바꿨는지 간결히 씁니다.
  예: `[Fix/#150]: 항목 삭제 시 배정된 담당자가 함께 정리되지 않던 문제 수정`
- **PR**: `develop`을 베이스로 생성하고, 병합 전 `./gradlew test` 전체 통과를 확인합니다. 병합은 Merge commit 방식을
  사용하고, 병합 후 작업 브랜치는 삭제하지 않는 것이 최근 관행입니다.
- **Issue**: 목적·범위(포함/제외)·작업 내용 체크리스트·완료 조건을 명시하는 형식을 따릅니다.
