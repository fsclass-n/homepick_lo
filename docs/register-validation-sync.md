# 회원가입 아이디/비밀번호 유효성 규칙을 로그인과 동일하게 통일

- 작업일: 2026-09-28
- 대상 파일
  - `src/main/resources/static/js/register.js`
  - `src/main/resources/templates/member/register.html`
  - `src/main/java/com/onrender/homepick/controller/MemberApiController.java`

## 배경

회원가입과 로그인의 입력 규칙이 달라서, 가입은 되는데 로그인 버튼이 활성화되지 않는 계정이 생길 수 있었음.

| 항목 | 회원가입 (변경 전) | 로그인 (`login.js`) |
|---|---|---|
| 아이디 | 4자 이상 (문자 종류 제한 없음) | 영문/숫자/`_` 4~20자 |
| 비밀번호 | 8자 이상 (문자 종류 제한 없음) | 8~30자, 영문+숫자 필수, 특수문자는 `@$!%*#?&` 만 허용 |

예) 비밀번호 `password` (숫자 없음), 아이디 `홍길동1` (한글) → 가입은 되지만 로그인 불가.

## 적용한 규칙 (로그인과 동일)

```js
// 아이디: 영문 소문자/대문자, 숫자, 언더스코어(_) 포함 4~20자
const USERNAME_REGEX = /^[a-zA-Z0-9_]{4,20}$/;
// 비밀번호: 8~30자, 영문 및 숫자 필수 포함, 특수문자 선택 허용
const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{8,30}$/;
```

## 수정 내용

### 1. `register.js` (STEP 4)

- `validateStep4()` 가 위 정규식으로 아이디/비밀번호를 검사 → 조건을 모두 만족하고 비밀번호 확인이 일치해야 "홈픽 시작하기" 버튼 활성화
- 로그인 화면과 같은 방식의 입력 피드백 추가
  - 아이디 / 비밀번호: 입력칸을 벗어날 때(blur) 규칙 위반이면 빨간 테두리 + 안내 문구
  - 비밀번호 확인: 입력 중 실시간으로 일치 여부 표시
- 안내 문구는 `login.js` 와 동일
  - `아이디는 4~20자의 영문, 숫자 조합이어야 합니다.`
  - `비밀번호는 영문, 숫자를 포함하여 8자 이상이어야 합니다.`
  - `비밀번호가 일치하지 않습니다.` (회원가입 전용)

### 2. `register.html`

- 아이디: `minlength="4" maxlength="20"`, 안내 placeholder, 피드백 영역 `#userIdFeedback`
- 비밀번호: `minlength="8" maxlength="30"`, 안내 placeholder, 피드백 영역 `#userPasswordFeedback`
- 비밀번호 확인: `maxlength="30"`, 피드백 영역 `#userPasswordConfirmFeedback`
- 브라우저 자동완성 속성 추가 (`autocomplete="username"`, `new-password`)

### 3. `MemberApiController` (서버 검증)

- 프런트를 우회한 요청도 막도록 서버에도 같은 정규식 적용

```java
private static final String USERNAME_REGEX = "^[a-zA-Z0-9_]{4,20}$";
private static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,30}$";
```

## 참고

- 규칙을 바꿀 때는 **3곳을 같이** 수정해야 함: `login.js`, `register.js`, `MemberApiController.java`
- 이번 변경 전에 새 규칙에 맞지 않게 가입한 계정은 로그인 화면에서 버튼이 활성화되지 않음 → DB에서 삭제 후 다시 가입 필요
