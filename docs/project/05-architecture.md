# 5. 시스템 아키텍처 설계서

[← 4. 프로세스 정의서](04-process.md) · [산출물 목차](README.md) · 다음: [6. 화면 설계서 →](06-screen-design.md)

---

## 5-1. 전체 구성도

```mermaid
flowchart LR
    subgraph Client["사용자"]
        U["웹 브라우저 (PC · 모바일)"]
    end

    subgraph Deploy["운영 서버 (같은 코드 · 같은 저장소)"]
        R["Render<br/>homepick.onrender.com<br/>Docker 컨테이너"]
        subgraph EC2["AWS EC2 (Ubuntu)"]
            N["nginx + Let's Encrypt SSL<br/>homepickkr.duckdns.org"]
            C["Docker 컨테이너<br/>Spring Boot :8080"]
            N --> C
        end
    end

    subgraph External["외부 서비스"]
        DB[("TiDB Cloud<br/>member · qna")]
        FB["Firebase Auth<br/>휴대폰 SMS 인증"]
        OA["OAuth2<br/>카카오 · 네이버 · 구글"]
    end

    subgraph GitHub["GitHub"]
        REPO["저장소 main<br/>소스 + apartments.json"]
        GA1["Actions: deploy.yml<br/>빌드 · 테스트 · EC2 배포"]
        GA2["Actions: ai-data-refresh.yml<br/>크롤링 · 학습"]
    end

    API["국토교통부<br/>아파트 매매 실거래가 API"]

    U -- "HTTPS" --> R
    U -- "HTTPS" --> N
    U -- "SMS 인증 (JS SDK)" --> FB
    U -- "소셜 로그인" --> OA
    R --> DB
    C --> DB
    R -- "재학습 실행 · 상태 · 최신 JSON" --> GA2
    C -- "재학습 실행 · 상태 · 최신 JSON" --> GA2
    GA2 --> API
    GA2 -- "apartments.json 커밋" --> REPO
    REPO -- "push" --> R
    REPO -- "push" --> GA1
    GA1 -- "SSH 배포" --> EC2
```

**핵심 포인트**
- Render와 EC2는 **같은 GitHub 저장소의 같은 코드**를 각각 배포합니다.
- AI 학습은 서버가 아니라 **GitHub Actions에서 한 번만** 실행되고, 결과 `apartments.json` 하나를 두 서버가 함께 씁니다.
- 학습 데이터만 바뀔 때는 재배포 없이, 서버가 GitHub에서 최신 JSON을 직접 받아 메모리에 보관합니다 (30분 캐시, 재학습 완료 시 즉시 갱신).

## 5-2. 애플리케이션 계층 구조 (Spring Boot)

```mermaid
flowchart TB
    subgraph View["View (Thymeleaf + 정적 리소스)"]
        T["templates/<br/>index · member/* · sub/ai-recommend · qna/list · common/*"]
        J["static/js · css<br/>register.js · login.js · ai-recommend.js · ai-insights.js"]
    end
    subgraph Controller["Controller"]
        HC["HomeController<br/>/ · /ai/recommend"]
        LC["LoginController<br/>/member/login · logout"]
        RC["RegisterController<br/>/member/register · admin"]
        MA["MemberApiController<br/>POST /api/members/register"]
        AC["AiDataController<br/>/api/ai-data/*"]
        QC["QnaController<br/>/qna"]
        HE["HealthController<br/>/healthz"]
    end
    subgraph Config["Config (Spring Security)"]
        SC["SecurityConfig<br/>BCrypt · OAuth2 로그인"]
        OH["OAuth2LoginSuccessHandler<br/>소셜 자동가입 · 세션"]
    end
    subgraph Service["Service"]
        AS["AiDataRefreshService<br/>Actions 실행 · 상태 · 데이터 캐시"]
        QS["QnaService"]
    end
    subgraph Repository["Repository (JdbcTemplate)"]
        MR["JdbcMemberRepository"]
        QR["JdbcQnaRepository"]
    end
    DB[("TiDB")]
    GH["GitHub REST API"]

    View --> Controller
    LC --> MR
    MA --> MR
    OH --> MR
    AC --> AS --> GH
    QC --> QS --> QR
    MR --> DB
    QR --> DB
```

## 5-3. AI 데이터 파이프라인

```mermaid
flowchart LR
    A["01_crawl.py<br/>83개 지역 x 6개월<br/>8건 병렬 · 재시도"] --> B["data/raw/*.json"]
    B --> C["02_preprocess.py<br/>정제 · 이상치 제거<br/>평당가 · 연식 · 방 수 추정"]
    C --> D["data/processed/apartments.csv"]
    D --> E["04_train.py<br/>RandomForest 적정가<br/>KMeans 단지 유형<br/>인사이트 · Plotly 그래프"]
    E --> F["static/data/apartments.json"]
    D -. "선택 (로컬 분석)" .-> G["03_eda.py<br/>data/reports"]
```

| 단계 | 파일 | 내용 |
|---|---|---|
| 공통 설정 | `ml-pipeline/common.py` | 경로, 수도권 83개 법정동 코드, 방 수 추정 규칙 |
| 수집 | `01_crawl.py` | `apis.data.go.kr` 호출, 실패 비율이 높으면 중단 · 원인 출력 |
| 전처리 | `02_preprocess.py` | 금액 · 면적 숫자화, 이상치 제거, 파생 변수 |
| 학습 | `04_train.py` | 특성: 면적 · 층 · 연식 + 지역(One-Hot). RandomForest(200 trees) → valueScore, KMeans(최대 8개) → 단지 유형 |
| 결과 | `apartments.json` | `items`(단지), `insights`(시장 분석), `charts`(Plotly), `model`(R² · MAE) |
| 실행 | `.github/workflows/ai-data-refresh.yml` | 크롤링 20분 · 전체 30분 제한, 결과 커밋 `[skip render]` |

`data/` 폴더는 실행할 때 생기는 작업 폴더라 Git 에 올리지 않습니다 (`.gitignore`). 배포 이미지에도 `ml-pipeline/` 은 넣지 않습니다 (`.dockerignore`).

## 5-4. 데이터베이스 설계 (TiDB)

```mermaid
erDiagram
    MEMBER {
        BIGINT member_id PK "자동 증가"
        VARCHAR user_id UK "로그인 아이디"
        VARCHAR password_hash "BCrypt"
        VARCHAR name "이름"
        CHAR birth "YYYYMMDD"
        CHAR gender "M F"
        VARCHAR phone "휴대폰"
        VARCHAR email "NULL 허용"
        VARCHAR firebase_uid "본인인증 UID"
        VARCHAR sns_type "kakao naver google"
        VARCHAR sns_id "제공자 고유번호"
        VARCHAR role "USER"
        DATETIME created_at "가입일"
    }
    QNA {
        BIGINT id PK "자동 증가"
        VARCHAR title "제목"
        TEXT content "내용"
        VARCHAR writer "작성자"
        VARCHAR status "대기중"
        DATETIME created_at "작성일"
    }
```

| 테이블 | 인덱스 · 제약 |
|---|---|
| `member` | `user_id` UNIQUE, (`sns_type`, `sns_id`) UNIQUE, `phone` · `firebase_uid` INDEX |
| `qna` | `id` PK |

스키마 원본: [`src/main/resources/schema.sql`](../../src/main/resources/schema.sql)

## 5-5. API 명세

| Method | URL | 설명 | 인증 | 응답 |
|---|---|---|---|---|
| GET | `/` | 메인 페이지 | - | HTML |
| GET | `/member/login` | 로그인 화면 | - | HTML |
| POST | `/member/login` | 아이디 로그인 | - | 메인 리다이렉트 / 오류 메시지 |
| GET | `/member/logout` | 로그아웃 | 세션 | 메인 리다이렉트 |
| GET | `/member/register` | 회원가입 화면 | - | HTML |
| POST | `/api/members/register` | 회원가입 (JSON) | Firebase 인증 | 200 · 400 · 409 · 500 |
| GET | `/oauth2/authorization/{provider}` | 소셜 로그인 시작 | - | 제공자로 이동 |
| GET | `/sns/{provider}-callback` | 소셜 로그인 콜백 | - | 메인 리다이렉트 |
| GET | `/ai/recommend` | AI 실거래 단지 추천 화면 | - | HTML |
| GET | `/api/ai-data/apartments` | 학습 데이터 (gzip) | - | JSON |
| GET | `/api/ai-data/status` | 재학습 진행 상태 | - | JSON (`state`, `step`, `message`) |
| POST | `/api/ai-data/refresh` | AI 재학습 실행 | **로그인** | 202 · 401 · 409 · 502 · 503 |
| GET | `/qna` | Q&A 목록 | - | HTML |
| GET | `/healthz` | 헬스 체크 | - | `OK` |

## 5-6. 배포 · 환경 구성

| 항목 | Render | AWS EC2 |
|---|---|---|
| 주소 | `homepick.onrender.com` | `homepickkr.duckdns.org` |
| 배포 방식 | main push → Render 가 Dockerfile 로 빌드 | main push → Actions 빌드 · 테스트 → SSH 로 EC2 에서 docker build |
| HTTPS | Render 제공 | nginx + Let's Encrypt, DuckDNS 도메인 |
| 환경변수 | Render 대시보드 | GitHub Secrets → 배포 시 `~/homepick.env` 생성 |
| 컨테이너 포트 | `$PORT` | `127.0.0.1:8080` (nginx 통해서만 접근) |

**환경변수**

| 구분 | 이름 |
|---|---|
| DB | `DB_HOST` `DB_PORT` `DB_DATABASE` `DB_USERNAME` `DB_PASSWORD` |
| 소셜 로그인 | `GOOGLE_CLIENT_ID/SECRET` `NAVER_CLIENT_ID/SECRET` `KAKAO_CLIENT_ID/SECRET` |
| Firebase | `FIREBASE_WEB_API_KEY` `_AUTH_DOMAIN` `_PROJECT_ID` `_STORAGE_BUCKET` `_MESSAGING_SENDER_ID` `_APP_ID` |
| AI 재학습 | `GITHUB_ACTIONS_TOKEN` (Secrets 이름은 `GH_ACTIONS_TOKEN`), `GITHUB_REPO` |
| 크롤링 | `DATA_GO_KR_API_KEY`, `CRAWL_MONTHS` (GitHub Secrets) |

## 5-7. 폴더 구조

```
homepick_lo/
├── .github/workflows/      deploy.yml (CI/CD · EC2) · ai-data-refresh.yml (AI 재학습)
├── ml-pipeline/            Python 크롤링 · 전처리 · 학습
├── src/main/java/com/onrender/homepick/
│   ├── config/             SecurityConfig · OAuth2LoginSuccessHandler
│   ├── controller/         Home · Login · Register · MemberApi · AiData · Qna · Health
│   ├── service/            AiDataRefreshService · QnaService
│   ├── repository/         JdbcMemberRepository · JdbcQnaRepository
│   └── dto/                LoginRequest · MemberJoinRequest · MemberSessionDto · QnaResponse
├── src/main/resources/
│   ├── templates/          index · common/ · member/ · sub/ai-recommend · qna/list
│   ├── static/             css · js · data/apartments.json · favicon
│   ├── application.properties
│   └── schema.sql
├── Dockerfile              Gradle 빌드 → JRE 21 실행 (2단계)
└── docs/                   작업 기록 · 산출물 문서
```
