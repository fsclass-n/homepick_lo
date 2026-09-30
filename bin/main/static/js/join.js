document.addEventListener('DOMContentLoaded', () => {
  /* =================================================================
   * 1. Firebase Spark (무료) SDK 설정
   * (Google Firebase 콘솔 -> 프로젝트 설정 -> 내 앱에서 확인 가능한 config 입력)
   * ================================================================= */
  const firebaseConfig = {
    apiKey: "YOUR_FIREBASE_API_KEY",
    authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT_ID.appspot.com",
    messagingSenderId: "YOUR_MESSAGING_SENDER_ID",
    appId: "YOUR_APP_ID"
  };

  // Firebase 초기화 (CDN compat)
  if (!firebase.apps.length) {
    firebase.initializeApp(firebaseConfig);
  }

  // 모달 인스턴스
  const authModalEl = document.getElementById('authModal');
  const authModal = new bootstrap.Modal(authModalEl);

  // 글로벌 회원가입 상태 데이터 (Spring Boot 4.x 백엔드 전송 규격)
  const memberFormData = {
    name: '',
    birth: '',
    gender: '',
    phone: '',
    userId: '',
    password: '',
    firebaseUid: '' // 휴대폰 본인인증 완료 식별자
  };

  let confirmationResult = null; // Firebase 인증 세션
  let timerInterval = null;

  /* =================================================================
   * 2. Step 1: 약관 동의 검증
   * ================================================================= */
  const checkAll = document.getElementById('checkAll');
  const termItems = document.querySelectorAll('.term-item');
  const termService = document.getElementById('termService');
  const termPrivacy = document.getElementById('termPrivacy');
  const btnStep1 = document.getElementById('btnStep1');

  function validateStep1(){
    btnStep1.disabled = !(termService.checked && termPrivacy.checked);
  }

  checkAll.addEventListener('change', () => {
    termItems.forEach(item => (item.checked = checkAll.checked));
    validateStep1();
  });

  termItems.forEach(item => {
    item.addEventListener('change', () => {
      checkAll.checked = Array.from(termItems).every(i => i.checked);
      validateStep1();
    });
  });

  btnStep1.addEventListener('click', () => switchStep('step1', 'step2'));

  /* =================================================================
   * 3. Step 2: 기본 정보(이름, 생년월일, 성별) 검증
   * ================================================================= */
  const userName = document.getElementById('userName');
  const userBirth = document.getElementById('userBirth');
  const genderRadios = document.querySelectorAll('input[name="userGender"]');
  const btnStep2 = document.getElementById('btnStep2');

  function validateStep2(){
    const isNameOk = userName.value.trim().length >= 2;
    const isBirthOk = userBirth.value.trim().length === 8;
    const isGenderOk = Array.from(genderRadios).some(r => r.checked);
    btnStep2.disabled = !(isNameOk && isBirthOk && isGenderOk);
  }

  userName.addEventListener('input', validateStep2);
  userBirth.addEventListener('input', (e) => {
    e.target.value = e.target.value.replace(/[^0-9]/g, '');
    validateStep2();
  });
  genderRadios.forEach(r => r.addEventListener('change', validateStep2));

  btnStep2.addEventListener('click', () => {
    memberFormData.name = userName.value.trim();
    memberFormData.birth = userBirth.value.trim();
    memberFormData.gender = document.querySelector('input[name="userGender"]:checked').value;
    switchStep('step2', 'step3');
    setupRecaptcha(); // 3단계 진입 시 reCAPTCHA 초기화
  });

  /* =================================================================
   * 4. Step 3: Firebase Phone Auth 본인인증
   * ================================================================= */
  const userPhone = document.getElementById('userPhone');
  const btnSendAuthCode = document.getElementById('btnSendAuthCode');
  const otpInputs = document.querySelectorAll('.otp-input');
  const btnVerifyAuthCode = document.getElementById('btnVerifyAuthCode');

  userPhone.addEventListener('input', (e) => {
    e.target.value = e.target.value.replace(/[^0-9]/g, '');
    btnSendAuthCode.disabled = e.target.value.length < 10;
  });

  function setupRecaptcha(){
    if (!window.recaptchaVerifier) {
      window.recaptchaVerifier = new firebase.auth.RecaptchaVerifier('recaptcha-container', {
        size: 'invisible',
        callback: () => {
          // reCAPTCHA 통과 시 동작
        }
      });
    }
  }

  // 인증 문자 발송
  btnSendAuthCode.addEventListener('click', async () => {
    const rawPhone = userPhone.value.trim();
    // 대한민국 국가코드 +82 포맷 변환 (01012345678 -> +821012345678)
    const formattedPhone = `+82${rawPhone.replace(/^0/, '')}`;

    btnSendAuthCode.disabled = true;
    btnSendAuthCode.textContent = '문자 발송 중...';

    try {
      const appVerifier = window.recaptchaVerifier;
      confirmationResult = await firebase.auth().signInWithPhoneNumber(formattedPhone, appVerifier);

      memberFormData.phone = rawPhone;
      document.getElementById('modalTargetPhone').textContent = rawPhone;

      authModal.show();
      startTimer(180);
      otpInputs[0].focus();
    } catch (err) {
      console.error(err);
      alert('인증문자 발송에 실패했습니다. 번호를 확인하거나 잠시 후 다시 시도해 주세요.\n' + err.message);
      if (window.recaptchaVerifier) {
        window.recaptchaVerifier.render().then(widgetId => grecaptcha.reset(widgetId));
      }
    } finally {
      btnSendAuthCode.disabled = false;
      btnSendAuthCode.textContent = '인증문자 받기';
    }
  });

  // OTP 6자리 인풋 포커스 자동 제어
  otpInputs.forEach((input, idx) => {
    input.addEventListener('input', (e) => {
      e.target.value = e.target.value.replace(/[^0-9]/g, '');
      if (e.target.value && idx < otpInputs.length - 1) {
        otpInputs[idx + 1].focus();
      }
    });

    input.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' && !e.target.value && idx > 0) {
        otpInputs[idx - 1].focus();
      }
    });
  });

  // 인증번호 검증 및 토큰 획득
  btnVerifyAuthCode.addEventListener('click', async () => {
    const code = Array.from(otpInputs).map(i => i.value).join('');
    if (code.length !== 6) {
      alert('6자리 인증번호를 모두 입력해 주세요.');
      return;
    }

    try {
      const result = await confirmationResult.confirm(code);
      const user = result.user;

      // Firebase 인증 성공 식별자(UID) 보관
      memberFormData.firebaseUid = user.uid;

      clearInterval(timerInterval);
      authModal.hide();
      alert('휴대폰 번호 인증이 완료되었습니다.');
      switchStep('step3', 'step4');
    } catch (err) {
      alert('인증번호가 일치하지 않거나 만료되었습니다.');
    }
  });

  /* =================================================================
   * 5. Step 4: 아이디 / 패스워드 설정 & Spring Boot 서버 가입 요청
   * ================================================================= */
  const userId = document.getElementById('userId');
  const userPassword = document.getElementById('userPassword');
  const userPasswordConfirm = document.getElementById('userPasswordConfirm');
  const btnSubmitJoin = document.getElementById('btnSubmitJoin');

  function validateStep4(){
    const isIdOk = userId.value.trim().length >= 4;
    const isPwOk = userPassword.value.length >= 8;
    const isPwMatch = userPassword.value === userPasswordConfirm.value;
    btnSubmitJoin.disabled = !(isIdOk && isPwOk && isPwMatch);
  }

  userId.addEventListener('input', validateStep4);
  userPassword.addEventListener('input', validateStep4);
  userPasswordConfirm.addEventListener('input', validateStep4);

  // 최종 회원가입 완료 요청
  btnSubmitJoin.addEventListener('click', async () => {
    memberFormData.userId = userId.value.trim();
    memberFormData.password = userPassword.value;

    btnSubmitJoin.disabled = true;
    btnSubmitJoin.textContent = '가입 처리 중...';

    try {
      // Spring Boot 4.x + JDBC / TiDB 백엔드 연동 엔드포인트 호출
      const response = await fetch('/api/members/join', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(memberFormData)
      });

      const result = await response.json();

      if (response.ok && result.success) {
        alert(`${memberFormData.name}님, 홈픽 가입을 환영합니다! 로그인 페이지로 이동합니다.`);
        window.location.href = '/member/login';
      } else {
        alert(result.message || '회원가입 처리에 실패했습니다.');
      }
    } catch (error) {
      console.error('Join API Error:', error);
      alert('서버와 통신 중 오류가 발생했습니다.');
    } finally {
      btnSubmitJoin.disabled = false;
      btnSubmitJoin.textContent = '홈픽 시작하기';
    }
  });

  /* =================================================================
   * 공통 헬퍼 유틸 함수
   * ================================================================= */
  function switchStep(fromId, toId){
    document.getElementById(fromId).classList.add('d-none');
    document.getElementById(toId).classList.remove('d-none');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function startTimer(seconds){
    clearInterval(timerInterval);
    const timerDisplay = document.getElementById('timer');

    timerInterval = setInterval(() => {
      const min = String(Math.floor(seconds / 60)).padStart(2, '0');
      const sec = String(seconds % 60).padStart(2, '0');
      timerDisplay.textContent = `${min}:${sec}`;

      if (--seconds < 0) {
        clearInterval(timerInterval);
        alert('인증 시간이 만료되었습니다. 다시 시도해 주세요.');
        authModal.hide();
      }
    }, 1000);
  }
});