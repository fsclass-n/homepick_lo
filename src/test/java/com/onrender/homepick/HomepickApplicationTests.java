package com.onrender.homepick;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// CI(GitHub Actions)에는 소셜 로그인 키가 없어 client-id 가 비면 컨텍스트 로딩이 실패하므로
// 테스트에서만 더미 값을 넣는다 (실제 로그인 호출은 하지 않음)
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.google.client-id=test-google-client",
		"spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
		"spring.security.oauth2.client.registration.naver.client-id=test-naver-client",
		"spring.security.oauth2.client.registration.naver.client-secret=test-naver-secret",
		"spring.security.oauth2.client.registration.kakao.client-id=test-kakao-client",
		"spring.security.oauth2.client.registration.kakao.client-secret=test-kakao-secret"
})
class HomepickApplicationTests {

	@Test
	void contextLoads() {
	}

}
