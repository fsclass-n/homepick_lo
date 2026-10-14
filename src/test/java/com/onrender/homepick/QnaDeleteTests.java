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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Q&A 글 삭제: 본인 글만, 삭제 버튼 표시, 삭제 후 목록 복귀
// @Transactional: 테스트용 글 생성·삭제는 끝나면 롤백 → 실제 DB 에 영향 없음
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
class QnaDeleteTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long insert(String writerId){
        jdbc.update("INSERT INTO qna (title, content, writer, writer_id) VALUES (?, ?, ?, ?)",
                "삭제 테스트 글", "내용", "작성회원", writerId);
        return jdbc.queryForObject("SELECT MAX(id) FROM qna", Long.class);
    }

    private boolean exists(long id){
        return jdbc.queryForObject("SELECT COUNT(*) FROM qna WHERE id = ?", Long.class, id) == 1;
    }

    private MockHttpSession login(String userId){
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginUser", new MemberSessionDto(userId, "작성회원"));
        return session;
    }

    private String detailHtml(long id, MockHttpSession session) throws Exception {
        var request = get("/qna/" + id);
        if (session != null) request.session(session);
        return mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 삭제_버튼은_본인_글에만_표시() throws Exception {
        long id = insert("owner01");
        assertThat(detailHtml(id, login("owner01"))).contains("qnaDeleteModal", "/qna/" + id + "/delete");
        assertThat(detailHtml(id, login("other02"))).doesNotContain("qnaDeleteModal");
        assertThat(detailHtml(id, null)).doesNotContain("qnaDeleteModal");
    }

    @Test
    void 본인은_삭제하고_보던_목록으로_돌아감() throws Exception {
        long id = insert("owner01");
        mvc.perform(post("/qna/" + id + "/delete").session(login("owner01"))
                        .param("page", "2").param("type", "title").param("keyword", "전세"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna?page=2&type=title&keyword=%EC%A0%84%EC%84%B8"))
                .andExpect(flash().attribute("message", "글이 삭제되었습니다."));
        assertThat(exists(id)).isFalse();
    }

    @Test
    void 다른_회원은_삭제할_수_없음() throws Exception {
        long id = insert("owner01");
        mvc.perform(post("/qna/" + id + "/delete").session(login("other02")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna/" + id + "?page=1"))
                .andExpect(flash().attribute("errorMessage", "본인이 작성한 글만 삭제할 수 있습니다."));
        assertThat(exists(id)).isTrue();
    }

    @Test
    void 작성자_아이디가_없는_예전_글은_삭제할_수_없음() throws Exception {
        long id = insert(null);
        mvc.perform(post("/qna/" + id + "/delete").session(login("owner01")))
                .andExpect(flash().attribute("errorMessage", "본인이 작성한 글만 삭제할 수 있습니다."));
        assertThat(exists(id)).isTrue();
        assertThat(detailHtml(id, login("owner01"))).doesNotContain("qnaDeleteModal");
    }

    @Test
    void 비로그인_삭제_요청은_로그인으로_보내고_삭제하지_않음() throws Exception {
        long id = insert("owner01");
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/qna/" + id + "/delete").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
        assertThat(exists(id)).isTrue();
        assertThat(LoginRedirect.pop(session)).isEqualTo("/qna/" + id + "?page=1"); // 로그인 후 그 글로 복귀
    }

    @Test
    void 이미_삭제된_글은_목록으로_보내고_안내() throws Exception {
        long id = insert("owner01");
        jdbc.update("DELETE FROM qna WHERE id = ?", id);
        mvc.perform(post("/qna/" + id + "/delete").session(login("owner01")))
                .andExpect(redirectedUrl("/qna?page=1"))
                .andExpect(flash().attribute("errorMessage", "이미 삭제되었거나 존재하지 않는 글입니다."));
    }

    @Test
    void GET_으로는_삭제되지_않음() throws Exception {
        long id = insert("owner01");
        mvc.perform(get("/qna/" + id + "/delete").session(login("owner01")));
        assertThat(exists(id)).isTrue();
    }
}
