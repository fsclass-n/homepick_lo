# SNS 로그인/회원가입 추가 (Google · Naver · Kakao)

- 작업일: 2026-10-01
- 목표: 로그인 페이지의 SNS 버튼 클릭 시 OAuth 로그인 후 자동 회원가입(최초 1회) + 세션 로그인 처리

## 적용 범위 (최소 변경)

1. OAuth2 Client 의존성 추가
   - `build.gradle`
2. Security 설정에 OAuth2 로그인 흐름 추가
   - `SecurityConfig`
3. 로그인 성공 시 공통 처리 핸들러 추가 (자동 회원가입 + 세션 저장)
   - `OAuth2LoginSuccessHandler` (신규)
4. 로그인 페이지 SNS 버튼 href 연결
   - `member/login.html`
5. OAuth2 환경변수/프로퍼티 추가
   - `application.properties`, `.env.example`

## 동작 방식

1. 사용자가 로그인 페이지에서 SNS 버튼 클릭
   - `/oauth2/authorization/{kakao|naver|google}`
2. Spring Security OAuth2가 각 플랫폼 로그인 페이지로 리다이렉트
3. 승인 후 콜백 URI(`/sns/*`)로 복귀
4. `OAuth2LoginSuccessHandler` 실행
   - provider별 사용자 식별자 추출
   - 내부 userId 생성: `sns_{provider}_{providerId}`
   - DB에 없으면 `member` 테이블에 자동 생성(회원가입)
   - 세션 `loginUser` 저장 후 `/`로 이동

## provider별 주요 매핑

- Google: `sub`, `name`, `email`
- Naver: `response.id`, `response.name/nickname/email`
- Kakao: `id`, `kakao_account.profile.nickname` (fallback: `properties.nickname`)

## 생성되는 회원 데이터(최초 로그인 시)

- `user_id`: `sns_google_xxx`, `sns_naver_xxx`, `sns_kakao_xxx`
- `password_hash`: 무작위 UUID를 BCrypt 인코딩하여 저장
- `name`: SNS 프로필 이름(없으면 user_id)
- `birth/gender/phone/firebase_uid`: 컬럼 NOT NULL 충족용 기본값 저장
  - `birth=19000101`
  - `gender=M`
  - `phone=00000000000`
  - `firebase_uid=oauth:{provider}:{providerId}`

> 참고: 현재 스키마 제약에 맞춘 최소 구현이다. 추후 SNS 전용 컬럼(예: provider/provider_id) 분리 권장.

## 필수 환경변수

- Google
  - `GOOGLE_CLIENT_ID`
  - `GOOGLE_CLIENT_SECRET`
- Naver
  - `NAVER_CLIENT_ID`
  - `NAVER_CLIENT_SECRET`
- Kakao
  - `KAKAO_CLIENT_ID` (REST API 키)
  - `KAKAO_CLIENT_SECRET`

## 콜백 URI 규칙

- 코드 기준 redirect-uri:
  - `{baseUrl}/sns/google-callback`
  - `{baseUrl}/sns/naver-callback`
  - `{baseUrl}/sns/kakao-callback`

플랫폼 콘솔에 등록한 URI와 정확히 일치해야 한다.

## 수정 파일 목록

- `build.gradle`
- `src/main/java/com/onrender/homepick/config/SecurityConfig.java`
- `src/main/java/com/onrender/homepick/config/OAuth2LoginSuccessHandler.java` (new)
- `src/main/resources/templates/member/login.html`
- `src/main/resources/application.properties`
- `.env.example`

## 확인 체크리스트

1. 서버 환경변수 6개 등록 후 재배포
2. `https://도메인/member/login` 접속
3. 카카오/네이버/구글 버튼 클릭 시 각 로그인 화면 이동 확인
4. 동의 후 홈(`/`)으로 이동되는지 확인
5. DB `member`에 `sns_*` 계정이 생성되는지 확인
