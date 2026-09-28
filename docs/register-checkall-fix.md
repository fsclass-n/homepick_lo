# 회원가입 약관 "전체 동의" 체크박스 미동작 수정

- 작업일: 2026-09-28
- 대상 파일
  - `src/main/resources/templates/member/register.html`
  - `src/main/resources/static/js/register.js`

## 증상

회원가입(STEP 1) 화면에서 **"전체 약관에 동의합니다"** 를 체크해도 아래 4개 약관
(`termService`, `termPrivacy`, `termLocation`, `termMarketing`)이 함께 체크되지 않음.
개별 약관 체크 시 "다음" 버튼 활성화도 동작하지 않음.

## 원인

체크박스 연동 로직 자체(`checkAll` change 이벤트 → `.term-item` 전체 체크)는 정상이었음.

문제는 `register.js`의 `DOMContentLoaded` 핸들러 **최상단의 Firebase 초기화 코드**였음.

```js
const firebaseConfig = window.__FIREBASE_CONFIG__;   // 프로젝트 어디에서도 정의되지 않음 → undefined
if (!firebase.apps.length) {
  firebase.initializeApp(firebaseConfig);            // undefined 전달 → 예외 발생
}
```

- `window.__FIREBASE_CONFIG__` 를 주입하는 코드가 HTML/서버 어디에도 없음
- `firebase.initializeApp(undefined)` 에서 예외가 발생하여 핸들러 실행이 중단됨
- 그 결과 **이후에 등록되어야 할 모든 이벤트 리스너**(약관 체크박스, STEP 2~4 입력 검증 등)가 등록되지 않음

## 수정 내용 (`register.js`)

1. **Firebase 초기화를 `try/catch`로 감쌈**
   - SDK 미로드 / 설정 미정의 / 초기화 실패 시 콘솔에 에러만 남기고 나머지 스크립트는 계속 실행
   - 초기화 성공 여부를 `isFirebaseReady` 플래그로 관리
2. **`setupRecaptcha()`**: `isFirebaseReady` 가 `true` 일 때만 reCAPTCHA 생성
3. **인증문자 발송 버튼**: Firebase 미설정 시 안내 alert 후 중단 (추가 예외 방지)

## 결과

- "전체 약관에 동의합니다" 체크/해제 시 4개 약관이 모두 함께 체크/해제됨
- 개별 약관을 모두 체크하면 전체 동의도 자동 체크됨
- 필수 약관 2개 체크 시 "다음" 버튼 활성화
- STEP 2, STEP 4 입력 검증도 정상 동작

## 후속 작업 필요

휴대폰 본인인증(STEP 3)을 실제로 사용하려면 `window.__FIREBASE_CONFIG__` 를 주입해야 함.
예) `register.html` 에서 `register.js` 로드 **이전**에 서버 설정값을 Thymeleaf로 주입:

```html
<script th:inline="javascript">
  window.__FIREBASE_CONFIG__ = /*[[${firebaseConfig}]]*/ null;
</script>
```

(컨트롤러에서 `application.properties`/환경변수 기반 설정값을 `firebaseConfig` 모델 속성으로 전달)
