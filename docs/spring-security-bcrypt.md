# Spring Security 추가 및 BCrypt 비밀번호 암호화 적용

- 작업일: 2026-09-29
- 대상 파일
  - `build.gradle`
  - `src/main/java/com/onrender/homepick/config/SecurityConfig.java` (신규)
  - `src/main/java/com/onrender/homepick/controller/RegisterController.java`
  - `src/main/java/com/onrender/homepick/controller/LoginController.java`

## 배경

기존에는 회원가입 시 비밀번호가 **평문 그대로** DB(`member.password`)에 저장되고,
로그인 시 `String.equals()` 로 평문끼리 비교하고 있었음.
DB가 유출되면 비밀번호가 그대로 노출되므로 BCrypt 해시로 저장하도록 변경함.

## 수정 내용

### 1. 의존성 추가 (`build.gradle`)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
```

### 2. 보안 설정 추가 (`config/SecurityConfig.java`)

| Bean | 역할 |
|------|------|
| `PasswordEncoder` | `BCryptPasswordEncoder` 를 등록. 컨트롤러에서 주입받아 암호화/비교에 사용 |
| `SecurityFilterChain` | 모든 요청 허용(`permitAll`), 기본 로그인 폼·HTTP Basic·CSRF 비활성화 |

> Spring Security는 의존성만 추가해도 **모든 페이지를 막고 기본 로그인 화면을 띄움**.
> 이 프로젝트는 로그인을 `LoginController` + `HttpSession` 방식으로 직접 처리하므로,
> 기존 동작이 바뀌지 않도록 Security의 기본 기능은 끄고 **BCrypt 암호화 기능만** 사용함.
> (`login.html` 폼에 CSRF 토큰이 없어서 CSRF도 끔)

### 3. 회원가입 시 암호화 (`RegisterController`)

```java
req.setPassword(passwordEncoder.encode(req.getPassword())); // 저장 전에 암호화
repository.save(req);
```

DB에는 `$2a$10$...` 형태의 60자 해시가 저장됨 (`password VARCHAR(100)` 이라 컬럼 변경 불필요).

### 4. 로그인 시 비교 (`LoginController`)

```java
// 변경 전
!member.getPassword().equals(req.getPassword())
// 변경 후
!passwordEncoder.matches(req.getPassword(), member.getPassword())
```

BCrypt는 같은 비밀번호라도 매번 다른 해시(salt 포함)가 나오므로 `equals` 로는 비교 불가.
반드시 `matches(입력한 평문, DB 해시)` 로 비교해야 함.

## 주의 사항

- **기존에 평문으로 가입된 회원은 로그인되지 않음** (DB 값이 해시가 아니므로 `matches` 가 false).
  테스트 데이터라면 `DELETE FROM member;` 후 다시 가입하면 됨.
- 회원 목록(`/member/admin`)의 비밀번호 칸에는 이제 해시 값이 보임.

## 확인 방법

1. 서버 실행 후 `/member/register` 에서 회원가입
2. DB에서 `SELECT email, password FROM member;` → password가 `$2a$10$...` 인지 확인
3. `/member/login` 에서 같은 비밀번호로 로그인 성공, 틀린 비밀번호는 실패 확인
