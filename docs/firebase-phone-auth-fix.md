# Firebase 휴대폰 본인인증(STEP 3) 미동작 수정

- 작업일: 2026-09-28
- 테스트 조건: 테스트 전화번호 `01012345678` / 인증코드 `123456`
- 대상 파일
  - `src/main/resources/templates/member/register.html`
  - `src/main/resources/static/js/register.js`
  - (참고) `src/main/resources/static/js/firebase-config.js` — `.gitignore` 대상, 로컬 전용

## 증상

`firebase-config.js` 에 Firebase 설정값을 넣었는데도 "인증문자 받기" 클릭 시
인증이 진행되지 않음 ("본인인증 서비스(Firebase) 설정이 되어 있지 않습니다" 알림 또는 콘솔에 `Firebase 초기화 실패`).

## 원인

### 1. `register.html` 에서 `firebase-config.js` 를 로드하지 않음 (핵심 원인)

파일은 `static/js/firebase-config.js` 에 존재하지만 `register.html` 에 `<script>` 태그가 없어
브라우저가 설정값을 전혀 받지 못했음. → `register.js` 에서 설정이 `undefined` → Firebase 초기화 실패.

### 2. `register.js` 가 `window.__FIREBASE_CONFIG__` 한 가지 형태만 인식

`firebase-config.js` 를 일반적인 형태(`const firebaseConfig = {...}`)로 작성하면
전역 변수이긴 하지만 `window` 속성이 아니므로 `window.__FIREBASE_CONFIG__` 로는 읽을 수 없음.
또한 기존 코드의 지역 변수명도 `firebaseConfig` 여서 전역 `firebaseConfig` 를 가리는(shadowing) 문제가 있었음.

## 수정 내용

### `register.html`

Firebase SDK 로드 **후**, `register.js` 로드 **전**에 설정 파일을 로드하도록 추가.

```html
<script src="https://www.gstatic.com/firebasejs/9.23.0/firebase-app-compat.js"></script>
<script src="https://www.gstatic.com/firebasejs/9.23.0/firebase-auth-compat.js"></script>

<!-- Firebase Config (gitignore 대상, 로컬에서 직접 생성) -->
<script th:src="@{/js/firebase-config.js}" src="../../static/js/firebase-config.js"></script>

<script th:src="@{/js/register.js}" src="../../static/js/register.js"></script>
```

### `register.js`

- 설정값을 아래 세 가지 형태 중 어느 것이든 인식하도록 변경 (지역 변수명은 `resolvedFirebaseConfig` 로 변경해 shadowing 제거)
  - `window.__FIREBASE_CONFIG__ = {...}`
  - `window.firebaseConfig = {...}`
  - `const firebaseConfig = {...}`
- `firebase-config.js` 안에서 이미 `firebase.initializeApp()` 을 호출한 경우도 정상 처리
  (`firebase.apps.length` 가 있으면 설정값 없이도 준비 완료로 판단)
- `firebase.auth().languageCode = 'ko'` 설정 (reCAPTCHA/SMS 한국어)

## 테스트 전화번호 사용 시 확인 사항 (Firebase 콘솔)

1. **Authentication → Sign-in method → 전화** 가 *사용 설정* 되어 있어야 함
2. **전화 → 테스트용 전화번호** 에 국제 형식으로 등록
   - 번호: `+82 10-1234-5678` (코드에서 `01012345678` → `+821012345678` 로 변환됨)
   - 코드: `123456`
3. **Authentication → 설정 → 승인된 도메인** 에 접속 도메인(`localhost` 등) 포함 여부
4. 테스트 번호는 실제 SMS가 발송되지 않음. 모달에서 `123456` 입력하면 인증 완료

## 참고 (별도 이슈)

STEP 4의 최종 가입 요청은 `POST /api/members/join` 을 호출하지만,
현재 백엔드(`RegisterController`)에는 해당 엔드포인트가 없음 (`POST /member/register` 만 존재, 필드도 email 기반).
본인인증 이후 가입 완료까지 동작시키려면 API 엔드포인트 추가 또는 JS 요청 경로/필드 정합이 필요함.
