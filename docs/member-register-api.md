# 회원가입 최종 단계(STEP 4) `No static resource api/members/register.` 에러 수정

- 작업일: 2026-09-28
- 대상 파일
  - (신규) `src/main/java/com/onrender/homepick/controller/MemberApiController.java`
  - (신규) `src/main/java/com/onrender/homepick/dto/MemberJoinRequest.java`
  - (수정) `src/main/java/com/onrender/homepick/repository/JdbcMemberRepository.java`

## 증상

휴대폰 본인인증 통과 후 STEP 4에서 아이디/비밀번호 입력 → "홈픽 시작하기" 클릭 시 알림 발생

```
No static resource api/members/register.
```

## 원인

- `register.js` 는 `POST /api/members/register` 로 JSON을 전송함
- 백엔드에는 해당 URL을 처리하는 컨트롤러가 **없었음** (`RegisterController` 는 `POST /member/register` 폼 전송용, email 기반)
- 매핑되는 컨트롤러가 없으면 Spring MVC가 정적 리소스로 찾으려다 실패하여 `NoResourceFoundException` → 위 메시지가 JSON `message` 로 내려와 alert에 표시됨

## 해결

### 1. `MemberJoinRequest` DTO 신규

`register.js` 의 `memberFormData` 와 필드명을 1:1로 맞춤.

| 필드 | 설명 |
|---|---|
| `name` | 이름 |
| `birth` | 생년월일 8자리 |
| `gender` | `M` / `F` |
| `phone` | 휴대폰 번호 (`01012345678`) |
| `userId` | 로그인 아이디 |
| `password` | 비밀번호 |
| `firebaseUid` | Firebase 본인인증 UID |

### 2. `MemberApiController` 신규 — `POST /api/members/register`

- `@RestController` + `@RequestBody` 로 JSON 수신
- 서버측 입력 검증 (이름 2자↑, 생년월일 8자리 숫자, 성별 M/F, 휴대폰 10~11자리, firebaseUid 필수, 아이디 4자↑, 비밀번호 8자↑)
- 중복 검사: 아이디 / 휴대폰 / firebaseUid (DB UNIQUE 위반 `DuplicateKeyException` 도 처리)
- 응답 형식 (`register.js` 가 `result.success`, `result.message` 를 사용)

| 상황 | HTTP | Body |
|---|---|---|
| 성공 | 200 | `{"success": true, "message": "회원가입이 완료되었습니다."}` |
| 입력 오류 | 400 | `{"success": false, "message": "..."}` |
| 중복 | 409 | `{"success": false, "message": "이미 사용 중인 아이디입니다." 등}` |

### 3. `JdbcMemberRepository` 메서드 추가

- `existsByUserId`, `existsByPhone`, `existsByFirebaseUid`
- `saveMember(MemberJoinRequest)`

```sql
INSERT INTO member (user_id, password_hash, name, birth, gender, phone, firebase_uid)
VALUES (?, ?, ?, ?, ?, ?, ?)
```

## 전제: DB 테이블 (신버전 member)

```sql
CREATE TABLE IF NOT EXISTS member (
    member_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(50) NOT NULL,
    birth           CHAR(8) NOT NULL,
    gender          CHAR(1) NOT NULL,
    phone           VARCHAR(20) NOT NULL UNIQUE,
    firebase_uid    VARCHAR(128) NOT NULL UNIQUE,
    role            VARCHAR(20) DEFAULT 'USER',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_ID_CACHE=1;
```

## 주의 / 후속 작업

- **비밀번호 평문 저장**: 요청에 따라 현재는 `password_hash` 컬럼에 평문 저장. 운영 전 BCrypt 등 해시 적용 권장
  (`spring-security-crypto` 의존성 + `BCryptPasswordEncoder`).
- **테스트 번호 재가입 시 중복 에러**: 테스트 번호(`01012345678`)는 항상 같은 `firebaseUid` 가 발급되므로,
  한 번 가입 후 다시 테스트하려면 DB에서 해당 회원 행을 삭제해야 함.
- **기존 로그인/관리자 기능 불일치**: `LoginController`, `RegisterController` 와 기존 repository 메서드
  (`existsByEmail`, `save`, `findByEmail`, `findAll`)는 구버전 `email/password` 컬럼을 사용하므로
  신버전 테이블에서는 동작하지 않음. 로그인을 `user_id` / `password_hash` 기준으로 변경하는 작업이 필요함.
- 서버 재시작(또는 DevTools 재시작) 후 테스트할 것.
