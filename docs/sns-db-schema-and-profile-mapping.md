# SNS 로그인 시 DB 컬럼(`sns_type`, `sns_id`)과 프로필 저장 정리

- 작업일: 2026-10-01
- 배경: SNS 로그인은 동작했지만, 제공자 식별값과 프로필(이메일/성별/휴대폰)을 명시적으로 저장하지 않던 구조를 개선

## 질문에 대한 결론

1. `sns_type`, `sns_id` 컬럼이 없어도 로그인 자체는 가능하지만, **운영에서는 추가가 권장**된다.
   - 이유: provider별 고유 계정 식별, 중복 가입 방지, 계정 연동 관리가 쉬워짐
2. 네이버 콘솔의 필수 체크(이름/이메일/성별/휴대폰)는
   - **네이버가 우리 앱에 전달 가능한 동의 항목**을 의미
   - DB 자동 저장이 아님. 애플리케이션 코드에서 직접 매핑/저장해야 반영됨

## 적용한 코드 변경

### 1) 스키마 확장

- `member` 테이블에 아래 컬럼 추가 반영:
  - `email VARCHAR(100) NULL`
  - `sns_type VARCHAR(20) NULL`
  - `sns_id VARCHAR(100) NULL`
  - `UNIQUE KEY uq_member_sns (sns_type, sns_id)`
- `schema.sql`의 구버전 `member(email PK)` 중복 정의는 제거하고 현재 버전 기준으로 정리

### 2) DTO 확장

- `MemberJoinRequest`에 SNS 저장용 필드 추가:
  - `email`, `snsType`, `snsId`

### 3) Repository 확장

- `findBySnsIdentity(snsType, snsId)` 추가
- `saveSocialMember(...)` 추가
  - SNS 로그인 최초 1회 시 `email/sns_type/sns_id`까지 저장

### 4) OAuth 성공 핸들러 개선

- `OAuth2LoginSuccessHandler`에서:
  - 우선 `(sns_type, sns_id)`로 기존 회원 조회
  - 없으면 SNS 응답값을 파싱하여 신규 생성
  - 네이버의 `mobile`, `gender`, `email` 값 매핑 저장
  - 구글/카카오도 가능한 범위에서 이메일/프로필 저장
- `application.properties`의 Naver scope를 `name,email,gender,mobile`로 확장

## 실제 저장되는 데이터(최초 SNS 로그인 시)

- `user_id`: `sns_{provider}_{providerId}` 형식
- `password_hash`: 랜덤 UUID를 BCrypt 인코딩
- `name`: 제공자 프로필 이름(없으면 user_id)
- `email`: 제공자 이메일(없으면 null)
- `gender`: 제공자 값을 `M/F`로 정규화 (없으면 `M`)
- `phone`: 숫자만 정규화 (없으면 `00000000000`)
- `firebase_uid`: `oauth:{provider}:{providerId}`
- `sns_type`: `google|naver|kakao`
- `sns_id`: 제공자 고유 사용자 ID

## 기존 DB를 위한 마이그레이션 SQL

> 이미 운영/개발 DB가 만들어져 있으면 아래 SQL을 직접 실행해야 함.

```sql
ALTER TABLE member
  ADD COLUMN email VARCHAR(100) NULL AFTER phone,
  ADD COLUMN sns_type VARCHAR(20) NULL AFTER firebase_uid,
  ADD COLUMN sns_id VARCHAR(100) NULL AFTER sns_type;

ALTER TABLE member
  ADD UNIQUE KEY uq_member_sns (sns_type, sns_id);
```

## 주의사항

- 현재 `RegisterController`의 구버전 `/member/register`(email 기반) 경로는 기존 호환 코드가 남아 있음.
- SNS 가입/로그인은 `POST /api/members/register`와 별도 흐름이며, `OAuth2LoginSuccessHandler`에서 처리됨.
- Render/로컬 모두 OAuth 환경변수(`GOOGLE_*`, `NAVER_*`, `KAKAO_*`)와 콜백 URI 일치 여부가 필수.
