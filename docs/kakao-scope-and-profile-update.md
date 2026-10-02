# 카카오 scope 재추가 + 이름/이메일 갱신 처리

- 작업일: 2026-10-02
- 요청 내용:
  - 카카오 scope 재설정
  - `kakao_account.name` 파싱
  - 기존 SNS 사용자 로그인 시 `name/email` 자동 업데이트

## 변경 사항

1. `application.properties`
   - 카카오 scope를 현재 권한 상태에 맞게 설정:
     - `spring.security.oauth2.client.registration.kakao.scope=profile_nickname`

2. `OAuth2LoginSuccessHandler`
   - 카카오 표시 이름 파싱 순서에 `kakao_account.name` 추가
   - 기존 SNS 사용자 로그인 시 `updateSocialProfile(...)` 호출
     - 새 값이 있으면 `name/email` 갱신
     - `sns_type/sns_id`가 비어 있던 기존 행도 보강

3. `JdbcMemberRepository`
   - 기존 조회 SQL에 `email` 포함
   - `updateSocialProfile(userId, name, email, snsType, snsId)` 메서드 추가

## 동작 요약

- 신규 카카오 로그인:
  - 최초 1회 회원 생성
- 기존 카카오 로그인:
  - 제공자에서 name/email이 내려오면 DB 프로필 자동 업데이트
  - name 미제공이면 기존 DB 이름 유지

## 주의

- 현재 `account_email`이 카카오 콘솔에서 `권한 없음`이면 scope에 넣으면 로그인 에러가 발생한다.
- 이메일 권한 승인이 완료되면 `account_email`을 scope에 다시 추가하면 된다.
