# GitHub Actions 빌드 실패 수정: 테스트 컨텍스트 로딩 시 OAuth2 client-id 비어 있음

- 작업일: 2026-10-08
- 대상 파일: `src/test/java/com/onrender/homepick/HomepickApplicationTests.java`

## 증상

`.github/workflows/deploy.yml` 의 `./gradlew build` 단계에서 테스트 실패

```
HomepickApplicationTests > contextLoads() FAILED
...
Caused by: java.lang.IllegalStateException at OAuth2ClientProperties.java:71
```

상세 원인(테스트 결과 XML):

```
java.lang.IllegalStateException: Client id of registration 'google' must not be empty.
```

## 원인

- `application.properties` 의 소셜 로그인 설정은 환경변수가 없으면 빈 값이 됨
  `spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID:}`
- `deploy.yml` 은 `DB_*` Secrets 만 주입하고 `GOOGLE_/NAVER_/KAKAO_CLIENT_ID` 는 주입하지 않음
- Spring Security 는 client-id 가 비어 있으면 시작 시 예외 → `@SpringBootTest` 컨텍스트 로딩 실패
- Render·로컬은 실제 키가 있어 정상, **CI 에서만** 실패

## 해결

컨텍스트 로딩 테스트는 실제 소셜 로그인 호출을 하지 않으므로, **테스트에서만 더미 client-id/secret** 을 지정

```java
@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-google-client",
    ... naver / kakao 동일
})
```

- 운영 설정(`application.properties`)·배포 워크플로는 변경 없음
- 실제 키를 GitHub Secrets 에 추가할 필요 없음 (테스트용 키 노출 위험 없음)

## 검증

- CI 와 같은 조건(DB 환경변수만 있고 소셜 로그인 환경변수 없음)으로 로컬 재현
  - 수정 전: `Client id of registration 'google' must not be empty.` 로 실패
  - 수정 후: `./gradlew build` 성공, `contextLoads` 1건 통과 (실패 0)
