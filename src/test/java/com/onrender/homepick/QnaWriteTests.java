package com.onrender.homepick;

import com.onrender.homepick.config.LoginRedirect;
import com.onrender.homepick.dto.MemberSessionDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Q&A 글쓰기: 로그인 확인, 입력 검사, 등록
// @Transactional: 테스트에서 저장한 글은 테스트가 끝나면 롤백 → 실제 DB 에 남지 않음
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.naver.client-id=test-naver-client",
        "spring.security.oauth2.client.registration.naver.client-secret=test-naver-secret",
        "spring.security.oauth2.client.registration.kakao.client-id=test-kakao-client",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-kakao-secret"
})
@AutoConfigureMockMvc
@Transactional
class QnaWriteTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long count(){
        return jdbc.queryForObject("SELECT COUNT(*) FROM qna", Long.class);
    }

    private MockHttpSession loginSession(){
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginUser", new MemberSessionDto("tester01", "테스트회원"));
        return session;
    }

    @Test
    void 비로그인_글쓰기_화면은_로그인으로_보내고_돌아올_주소를_저장() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/qna/write").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
        assertThat(LoginRedirect.pop(session)).isEqualTo("/qna/write");
    }

    @Test
    void 로그인하면_글쓰기_화면에_작성자_이름_표시() throws Exception {
        mvc.perform(get("/qna/write").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("qna/write"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("테스트회원")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("로그아웃"))); // 헤더도 로그인 상태
    }

    @Test
    void 빈_제목_내용은_오류와_함께_다시_표시하고_저장하지_않음() throws Exception {
        long before = count();
        mvc.perform(post("/qna/write").session(loginSession()).param("title", "   ").param("content", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("qna/write"))
                .andExpect(model().attribute("errors", Map.of("title", "제목을 입력해 주세요.", "content", "내용을 입력해 주세요.")));
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void 제목이_너무_길면_오류_입력값은_유지() throws Exception {
        String longTitle = "가".repeat(101);
        mvc.perform(post("/qna/write").session(loginSession()).param("title", longTitle).param("content", "내용"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errors"))
                .andExpect(model().attribute("title", longTitle))
                .andExpect(model().attribute("content", "내용"));
    }

    @Test
    void 정상_등록하면_목록으로_이동하고_작성자는_로그인_회원_이름() throws Exception {
        long before = count();
        mvc.perform(post("/qna/write").session(loginSession())
                        .param("title", "  테스트 질문 제목  ")
                        .param("content", "첫 줄\r\n둘째 줄")
                        .param("writer", "다른사람")) // 폼으로 작성자를 바꾸려 해도 무시
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna"))
                .andExpect(flash().attributeExists("message"));

        assertThat(count()).isEqualTo(before + 1);
        Map<String, Object> row = jdbc.queryForMap("SELECT title, content, writer, writer_id, status FROM qna ORDER BY id DESC LIMIT 1");
        assertThat(row.get("writer_id")).isEqualTo("tester01"); // 본인 확인(삭제)용 아이디
        assertThat(row.get("title")).isEqualTo("테스트 질문 제목");
        assertThat(row.get("content")).isEqualTo("첫 줄\n둘째 줄");
        assertThat(row.get("writer")).isEqualTo("테스트회원");
        assertThat(row.get("status")).isEqualTo("대기중");
    }

    @Test
    void 비로그인_등록_요청은_저장하지_않음() throws Exception {
        long before = count();
        mvc.perform(post("/qna/write").param("title", "제목").param("content", "내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void 로그인_후_돌아갈_주소는_사이트_안_경로만_허용(){
        MockHttpSession session = new MockHttpSession();
        for (String bad : new String[]{"//evil.com", "https://evil.com", "/\\evil.com", "evil"}) {
            LoginRedirect.save(session, bad);
            assertThat(LoginRedirect.pop(session)).as(bad).isEqualTo("/");
        }
        assertThat(LoginRedirect.pop(session)).isEqualTo("/"); // 저장된 값 없음
    }
}
