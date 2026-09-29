# 로그인 버튼 비활성화 · 비밀번호 보기 버튼 미동작 · 로그인 실패 수정

- 작업일: 2026-09-28
- 대상 파일
  - `src/main/resources/templates/member/login.html`
  - `src/main/java/com/onrender/homepick/controller/LoginController.java`
  - `src/main/java/com/onrender/homepick/dto/LoginRequest.java`
  - `src/main/java/com/onrender/homepick/dto/MemberSessionDto.java`
  - `src/main/java/com/onrender/homepick/repository/JdbcMemberRepository.java`
  - `src/main/resources/templates/index__.html`

## 1. 증상

1. 아이디(`hong`)와 비밀번호를 입력해도 **로그인 버튼이 활성화되지 않음**
2. 비밀번호 입력란의 **눈 아이콘(비밀번호 보기)** 을 눌러도 반응 없음

## 2. 원인

### 2-1. `login.js` 가 로드되지 않음 (두 증상의 공통 원인)

```html
<script src="../../static/js/login.js"></script>
```

- 로그인 버튼 활성화와 비밀번호 보기는 모두 `login.js` 가 처리한다.
- `src` 가 파일 시스템 기준 상대경로라서 브라우저는 `/member/login` 기준으로 `/static/js/login.js` 를 요청한다.
- Spring Boot는 `static` 폴더 내용을 루트(`/js/login.js`)로 제공하므로 **404** → 스크립트가 실행되지 않음.
- CSS는 `th:href="@{/css/login.css}"` 가 있어서 정상 로드되었지만, script에는 `th:src` 가 없었음.
- `register.html` 은 `th:src="@{/js/register.js}"` 를 쓰고 있어서 정상 동작했던 것.

### 2-2. 버튼이 활성화되어도 로그인이 안 되는 백엔드 문제 (함께 수정)

| 구분 | 문제 |
|---|---|
| 폼 ↔ DTO | 폼은 `username` 으로 전송하는데 `LoginRequest` 는 `email` 필드 → 값이 항상 `null` |
| DB 조회 | `findByEmail()` 이 구버전 컬럼(`email`, `password`) 조회 → 신버전 `member` 테이블에서 SQL 오류 |
| 에러 표시 | 컨트롤러가 `error` 를 넘겨도 `login.html` 에 표시하는 부분이 없음 |

## 3. 수정 내용

### `login.html`

- script 경로를 Thymeleaf 경로로 변경 (`register.html` 과 같은 방식)
  ```html
  <script th:src="@{/js/login.js}" src="../../static/js/login.js"></script>
  ```
- `<html>` 에 `xmlns:th` 추가
- 로그인 실패 알림 박스(`#loginAlert`)에 서버 `error` 메시지 표시 (`error` 가 없으면 숨김)
- 로그인 실패 시 입력했던 아이디를 다시 채워줌 (`th:value="${username}"`)

### `LoginRequest`

- `email` → `username` (폼 `name="username"` 과 일치)

### `JdbcMemberRepository`

- `findByUserId(userId)` 추가: 신버전 테이블에서 `user_id`, `password_hash`, `name` 조회

### `LoginController`

- `findByEmail` → `findByUserId` 로 변경
- `password_hash` 와 입력 비밀번호 비교 (현재 평문 저장 방식에 맞춤)
- 실패 메시지: `아이디 또는 비밀번호가 일치하지 않습니다.`
- 성공 시 세션 `loginUser` 에 `MemberSessionDto(userId, name)` 저장 → `/` 로 이동

### `MemberSessionDto`

- `email` → `userId` 필드로 변경 (헤더의 `${user.name}` 표시는 그대로 동작)
- `index__.html` 의 `${user.email}` → `${user.userId}` 로 수정

## 4. 주의 / 후속 작업

- **비밀번호 규칙이 서로 다름**
  - 회원가입(`register.js`): 8자 이상이면 통과
  - 로그인(`login.js`): 영문 + 숫자 포함 8~30자 (`[A-Za-z\d@$!%*#?&]` 만 허용)
  - 영문만 쓰거나 허용 목록에 없는 특수문자로 가입하면 **로그인 버튼이 활성화되지 않음**. 두 규칙을 하나로 맞추는 작업 필요.
- **관리자 화면 / 구 회원가입**: `RegisterController` 의 `POST /member/register`, `/member/admin` 과
  repository 의 `existsByEmail` / `save` / `findByEmail` / `findAll` 은 아직 구버전 컬럼을 사용하므로 동작하지 않음.
- **아이디 저장** 체크박스는 아직 기능이 구현되어 있지 않음 (UI만 존재).
- 비밀번호 평문 저장/비교 중 (운영 전 BCrypt 적용 권장).
