# 카카오 scope 재추가 + 이름/이메일 갱신 처리

- 작업일: 2026-10-02
- 요청 내용:
  - 카카오 scope 재설정
  - `kakao_account.name` 파싱
  - 기존 SNS 사용자 로그인 시 `name/email` 자동 업데이트

## 변경 사항

1. `application.properties`
   - 카카오 scope 설정 제거 (KOE205: 카카오 콘솔에 설정하지 않은 동의 항목을 요청하면 로그인 불가)
     - scope를 보내지 않으면 회원번호(`id`)만으로 로그인되고, 이름은 `userId`로 대체된다.

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

- 카카오 콘솔 [카카오 로그인] > [동의항목]에서 설정하지 않은 항목을 scope에 넣으면 KOE205 에러가 발생한다.
- 닉네임/이메일이 필요하면 콘솔에서 해당 동의항목(`profile_nickname`, `account_email`)을 먼저 설정한 뒤 scope에 다시 추가한다.
