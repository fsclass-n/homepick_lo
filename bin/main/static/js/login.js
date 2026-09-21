/**
 * static/js/login.js
 * 홈픽 로그인 유효성 검사 및 UI 인터랙션 스크립트
 */
document.addEventListener("DOMContentLoaded", () => {
    // 1. DOM 요소 취득
    const loginForm = document.getElementById("loginForm");
    const usernameInput = document.getElementById("username");
    const passwordInput = document.getElementById("password");
    const usernameFeedback = document.getElementById("usernameFeedback");
    const passwordFeedback = document.getElementById("passwordFeedback");
    const btnLogin = document.getElementById("btnLogin");
    const toggleBtn = document.getElementById("togglePassword");
    const eyeIcon = document.getElementById("eyeIcon");

    // 2. 실무 표준 정규표현식 정의
    // 아이디: 영문 소문자/대문자, 숫자, 언더스코어(_) 포함 4~20자
    const USERNAME_REGEX = /^[a-zA-Z0-9_]{4,20}$/;
    // 비밀번호: 8~30자, 영문 및 숫자 필수 포함, 특수문자 선택 허용
    const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{8,30}$/;

    // 3. 비밀번호 보이기/숨김 토글
    if (toggleBtn && passwordInput && eyeIcon) {
        toggleBtn.addEventListener("click", () => {
            const isPassword = passwordInput.getAttribute("type") === "password";
            passwordInput.setAttribute("type", isPassword ? "text" : "password");
            eyeIcon.classList.toggle("bi-eye-slash");
            eyeIcon.classList.toggle("bi-eye");
        });
    }

    // 4. 단일 입력 유효성 검사 함수
    function validateField(input, feedbackEl, regex, emptyMsg, invalidMsg) {
        const val = input.value.trim();

        if (val === "") {
            input.classList.remove("is-valid", "is-invalid");
            feedbackEl.textContent = "";
            return false;
        }

        if (!regex.test(val)) {
            input.classList.add("is-invalid");
            input.classList.remove("is-valid");
            feedbackEl.textContent = invalidMsg;
            return false;
        }

        input.classList.remove("is-invalid");
        input.classList.add("is-valid");
        feedbackEl.textContent = "";
        return true;
    }

    // 5. 전체 폼 상태 평가 및 로그인 버튼 활성화 토글
    function evaluateFormState() {
        const isUsernameValid = USERNAME_REGEX.test(usernameInput.value.trim());
        const isPasswordValid = PASSWORD_REGEX.test(passwordInput.value.trim());

        if (isUsernameValid && isPasswordValid) {
            btnLogin.disabled = false;
            btnLogin.classList.add("active");
        } else {
            btnLogin.disabled = true;
            btnLogin.classList.remove("active");
        }
    }

    // 6. 실시간 입력(Input) 및 포커스 아웃(Blur) 이벤트 바인딩
    usernameInput.addEventListener("input", () => {
        evaluateFormState();
    });

    usernameInput.addEventListener("blur", () => {
        validateField(
            usernameInput,
            usernameFeedback,
            USERNAME_REGEX,
            "아이디를 입력해 주세요.",
            "아이디는 4~20자의 영문, 숫자 조합이어야 합니다."
        );
        evaluateFormState();
    });

    passwordInput.addEventListener("input", () => {
        evaluateFormState();
    });

    passwordInput.addEventListener("blur", () => {
        validateField(
            passwordInput,
            passwordFeedback,
            PASSWORD_REGEX,
            "비밀번호를 입력해 주세요.",
            "비밀번호는 영문, 숫자를 포함하여 8자 이상이어야 합니다."
        );
        evaluateFormState();
    });

    // 7. 폼 서브밋(Submit) 최종 방어 검사
    loginForm.addEventListener("submit", (e) => {
        const isUsernameValid = validateField(
            usernameInput,
            usernameFeedback,
            USERNAME_REGEX,
            "아이디를 입력해 주세요.",
            "아이디는 4~20자의 영문, 숫자 조합이어야 합니다."
        );

        const isPasswordValid = validateField(
            passwordInput,
            passwordFeedback,
            PASSWORD_REGEX,
            "비밀번호를 입력해 주세요.",
            "비밀번호는 영문, 숫자를 포함하여 8자 이상이어야 합니다."
        );

        if (!isUsernameValid || !isPasswordValid) {
            e.preventDefault(); // 제출 차단
            
            // 첫 번째 에러 필드로 포커스 이동
            if (!isUsernameValid) {
                usernameInput.focus();
            } else {
                passwordInput.focus();
            }
        }
    });
});