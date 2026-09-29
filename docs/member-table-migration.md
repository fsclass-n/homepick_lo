# 회원가입 `bad SQL grammar` 에러 수정 + 동일 본인인증으로 복수 아이디 가입 허용

- 작업일: 2026-09-28
- 대상 파일
  - `src/main/java/com/onrender/homepick/controller/MemberApiController.java`
  - `src/main/java/com/onrender/homepick/repository/JdbcMemberRepository.java`
  - `src/main/resources/schema.sql`

## 1. 증상

STEP 4 "홈픽 시작하기" 클릭 시 알림

```
PreparedStatementCallback; bad SQL grammar [SELECT COUNT(*) FROM member WHERE user_id = ?]
```

## 2. 원인

**실제 TiDB의 `member` 테이블이 구버전(email 기반) 구조**이기 때문 → `user_id` 컬럼이 없음.

`schema.sql` 에 같은 이름의 `member` 테이블이 두 번 정의되어 있었음.

```sql
CREATE TABLE IF NOT EXISTS member (email ..., password ..., name ...);          -- ① 구버전
CREATE TABLE IF NOT EXISTS member (member_id ..., user_id ..., phone ...);      -- ② 신버전
```

순서대로 실행하면 ①이 먼저 생성되고, ②는 `IF NOT EXISTS` 때문에 **아무 에러 없이 건너뛰어짐**.
그래서 신버전 테이블을 만들었다고 생각했지만 실제로는 구버전 테이블만 존재함.

부가적으로, DB 오류 메시지(SQL 원문)가 그대로 사용자 알림에 노출되고 있었음.

## 3. 수정 내용

### 3-1. 동일 휴대폰 / 본인인증으로 여러 아이디 가입 허용

| 구분 | 변경 전 | 변경 후 |
|---|---|---|
| 중복 검사 (Controller) | 아이디 + 휴대폰 + firebaseUid | **아이디만** |
| Repository | `existsByPhone`, `existsByFirebaseUid` 존재 | 삭제 (미사용) |
| DB 제약 (`schema.sql`) | `phone UNIQUE`, `firebase_uid UNIQUE` | UNIQUE 제거, 조회용 일반 INDEX 로 변경 |

→ 테스트 번호 `01012345678` 로 인증 후 **아이디만 다르면 계속 가입 가능**.

### 3-2. DB 오류 메시지 노출 방지

`MemberApiController` 에서 `DataAccessException` 을 잡아
- 서버 로그: 실제 오류 메시지 출력
- 사용자 응답(500): `회원가입 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.`

아이디 UNIQUE 위반(`DuplicateKeyException`, 동시 가입 경합)은 409 `이미 사용 중인 아이디입니다.` 로 응답.

### 3-3. `schema.sql` 정리

- 중복 정의된 구버전 `member` 정의 삭제 → 신버전 하나만 유지
- `phone`, `firebase_uid` UNIQUE 제거 + `INDEX idx_member_phone`, `INDEX idx_member_firebase_uid` 추가
- (참고) `spring.sql.init` 설정이 없으므로 이 파일은 앱 실행 시 자동 실행되지 않음. DB 툴에서 직접 실행하는 용도.

## 4. DB 전환 (직접 실행 필요)

> 코드만 바꿔서는 해결되지 않음. **TiDB 에서 아래 SQL을 직접 실행**해야 함.
> 아래 방법은 기존 데이터를 삭제하지 않고 `member_legacy` 로 이름만 바꿔 보관함.

```sql
USE homepick_db;

-- (1) 현재 구조 확인: email / password / name / created_at 이면 구버전
DESC member;

-- (2) 구버전 테이블 보관 (데이터 유지)
RENAME TABLE member TO member_legacy;

-- (3) 신버전 테이블 생성
CREATE TABLE IF NOT EXISTS member (
    member_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(50) NOT NULL,
    birth           CHAR(8) NOT NULL,
    gender          CHAR(1) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    firebase_uid    VARCHAR(128) NOT NULL,
    role            VARCHAR(20) DEFAULT 'USER',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member_phone (phone),
    INDEX idx_member_firebase_uid (firebase_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_ID_CACHE=1;

-- (4) 확인
DESC member;
```

### (참고) 이미 신버전 테이블이 UNIQUE 제약과 함께 만들어져 있는 경우

```sql
SHOW INDEX FROM member;                 -- phone / firebase_uid 의 Key_name 확인
ALTER TABLE member DROP INDEX phone;
ALTER TABLE member DROP INDEX firebase_uid;
CREATE INDEX idx_member_phone ON member (phone);
CREATE INDEX idx_member_firebase_uid ON member (firebase_uid);
```

## 5. 주의 / 후속 작업

- **로그인 · 관리자 화면**: `LoginController`, `RegisterController`, repository 의 `existsByEmail` / `save` / `findByEmail` / `findAll` 은
  구버전 컬럼(`email`, `password`)을 사용 → 테이블 전환 후에는 동작하지 않음.
  `user_id` / `password_hash` 기준으로 변경 필요.
- 비밀번호는 요청에 따라 평문 저장 중 (운영 전 BCrypt 해시 적용 권장).
- DB 전환 후 서버 재시작하여 테스트.
