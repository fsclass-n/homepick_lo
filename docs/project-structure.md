# 홈픽(Homepick) 프로젝트 파일/폴더 구조

- 작성일: 2026-09-28
- 기술 스택: Spring Boot 4.1.1 (Java 21) · Spring MVC · Thymeleaf · Spring JDBC (JdbcTemplate) · TiDB(MySQL) · Bootstrap 5 · Firebase Phone Auth
- 빌드 산출물(`bin/`, `build/`, `.gradle/`)은 제외

```
homepick_lo/
├── .github/
│   └── workflows/
│       └── deploy.yml                     # GitHub Actions 배포 워크플로
├── .vscode/
│   ├── launch.json                        # VS Code 실행/디버그 설정
│   └── settings.json
├── docs/                                  # 작업 내역 정리 문서
│   ├── project-structure.md               # (현재 문서) 프로젝트 구조
│   ├── register-checkall-fix.md           # 약관 전체 동의 체크박스 수정
│   ├── firebase-phone-auth-fix.md         # firebase-config.js 로드 누락 수정
│   ├── firebase-sms-region-error.md       # SMS 리전 정책 / Spark 요금제 정리
│   ├── member-register-api.md             # 회원가입 API(/api/members/register) 신규
│   ├── member-table-migration.md          # member 테이블 전환 + 복수 아이디 가입 허용
│   ├── member-db-connection-check.md      # DB 연결 진단 로그 추가
│   ├── login-fix.md                       # 로그인 버튼/비밀번호 보기/로그인 처리 수정
│   ├── register-validation-sync.md        # 회원가입 유효성 규칙 로그인과 통일
│   └── index-scroll-fix.md                # 메인 가로 스크롤/푸터 하단 스크롤 수정
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/com/onrender/homepick/
│   │   │   ├── HomepickApplication.java           # Spring Boot 진입점
│   │   │   ├── basic/                             # (학습용) Java 기초 예제
│   │   │   │   ├── Car.java
│   │   │   │   └── Ex01_객체생성.java
│   │   │   ├── controller/
│   │   │   │   ├── HomeController.java            # GET /  (메인, 세션 사용자 전달)
│   │   │   │   ├── LoginController.java           # GET·POST /member/login, GET /member/logout
│   │   │   │   ├── MemberApiController.java       # POST /api/members/register (JSON 회원가입)
│   │   │   │   ├── QnaController.java             # /qna 게시판
│   │   │   │   └── RegisterController.java        # GET /member/register, /member/admin (구버전 폼 처리 포함)
│   │   │   ├── dto/
│   │   │   │   ├── LoginRequest.java              # 로그인 폼 (username, password)
│   │   │   │   ├── MemberJoinRequest.java         # 회원가입 JSON (name, birth, gender, phone, userId, password, firebaseUid)
│   │   │   │   ├── MemberSessionDto.java          # 세션 loginUser (userId, name)
│   │   │   │   ├── QnaResponse.java
│   │   │   │   └── RegisterRequest.java           # (구버전) email 기반 회원 DTO
│   │   │   ├── repository/
│   │   │   │   ├── InMemoryMemberRepository.java  # (구버전) 메모리 저장소
│   │   │   │   ├── JdbcMemberRepository.java      # member 테이블 JDBC 접근
│   │   │   │   └── JdbcQnaRepository.java         # qna 테이블 JDBC 접근
│   │   │   ├── service/
│   │   │   │   └── QnaService.java
│   │   │   └── th/                                # (학습용) Thymeleaf 예제 컨트롤러/DTO
│   │   │       ├── Ex01.java
│   │   │       ├── Ex02Controller.java · Ex02Dto.java
│   │   │       ├── Ex03Controller.java · Ex03Dto.java
│   │   │       ├── Ex04_Controller.java · Ex04_Dto.java
│   │   │       ├── Ex05_Controller.java
│   │   │       └── Ex06_Controller.java
│   │   └── resources/
│   │       ├── application.properties             # 서버/Thymeleaf/TiDB 접속 설정 (${TIDB_*} 환경변수)
│   │       ├── schema.sql                         # member, qna 테이블 DDL + 샘플 데이터 (수동 실행용)
│   │       ├── static/                            # 정적 리소스 → URL 루트(/css, /js)로 제공
│   │       │   ├── css/
│   │       │   │   ├── common.css                 # 디자인 토큰, 리셋, 커스텀 커서
│   │       │   │   ├── header.css
│   │       │   │   ├── footer.css
│   │       │   │   ├── main.css                   # 메인(index) 섹션
│   │       │   │   ├── login.css
│   │       │   │   └── register.css
│   │       │   └── js/
│   │       │       ├── header.js                  # GNB / 모바일 메뉴
│   │       │       ├── footer.js                  # 패밀리사이트, 투더탑
│   │       │       ├── cursor.js                  # 푸터 커스텀 커서
│   │       │       ├── main.js                    # 스크롤 등장 · 패럴랙스
│   │       │       ├── login.js                   # 로그인 유효성 · 비밀번호 보기
│   │       │       ├── register.js                # 회원가입 4단계 · Firebase 휴대폰 인증
│   │       │       └── firebase-config.js         # ⚠ .gitignore 대상 (로컬에서 직접 생성)
│   │       └── templates/                         # Thymeleaf 뷰
│   │           ├── index.html                     # 메인 페이지
│   │           ├── index__.html                   # (백업/테스트용) 이전 메인
│   │           ├── common/
│   │           │   ├── header.html                # th:fragment="headerFragment"
│   │           │   ├── footer.html                # th:fragment="footerFragment"
│   │           │   └── layout.html
│   │           ├── member/
│   │           │   ├── login.html
│   │           │   ├── register.html              # 약관 → 기본정보 → 휴대폰 인증 → 계정 생성
│   │           │   └── admin.html                 # 회원 목록 (구버전 컬럼 사용)
│   │           ├── qna/
│   │           │   └── list.html
│   │           └── th/                            # (학습용) Thymeleaf 예제 뷰
│   │               ├── ex01.html ~ ex03.html
│   │               ├── ex04.html · ex04_faq.html · ex04_join.html · ex04_login.html
│   │               └── ex05.html · ex06.html
│   └── test/
│       └── java/com/onrender/homepick/
│           └── HomepickApplicationTests.java
├── .env                                   # ⚠ .gitignore 대상 (TIDB_* 접속 정보)
├── .dockerignore
├── .gitattributes
├── .gitignore
├── Dockerfile
├── build.gradle                           # 의존성 (webmvc, thymeleaf, jdbc, mysql-connector, lombok, devtools)
├── settings.gradle
├── gradlew
└── gradlew.bat
```

## 주요 URL 매핑

| Method | URL | 처리 | 화면/응답 |
|---|---|---|---|
| GET | `/` | `HomeController.index` | `index.html` |
| GET | `/member/login` | `LoginController.form` | `member/login.html` |
| POST | `/member/login` | `LoginController.process` | 성공 시 `/` 리다이렉트 |
| GET | `/member/logout` | `LoginController.logout` | `/` 리다이렉트 |
| GET | `/member/register` | `RegisterController.form` | `member/register.html` |
| POST | `/api/members/register` | `MemberApiController.register` | JSON `{success, message}` |
| GET | `/member/admin` | `RegisterController.memberList` | `member/admin.html` (구버전 컬럼, 미동작) |
| - | `/qna` | `QnaController` | `qna/list.html` |

## 참고

- `static/` 아래 파일은 `/static` 없이 루트 경로로 제공됨 → 템플릿에서는 `th:href="@{/css/...}"`, `th:src="@{/js/...}"` 사용
- `basic/`, `th/` 패키지와 `templates/th/` 는 학습용 예제
- `.env`, `firebase-config.js` 는 저장소에 포함되지 않으므로 새 환경에서는 직접 만들어야 함
