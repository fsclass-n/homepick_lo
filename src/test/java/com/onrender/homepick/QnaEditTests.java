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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Q&A 글 수정: 본인 글 · 답변 전만, 수정 화면, 저장 후 상세로
// @Transactional: 테스트용 글 · 수정은 끝나면 롤백 → 실제 DB 에 남지 않음
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
class QnaEditTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long insert(String writerId, String answer){
        jdbc.update("INSERT INTO qna (title, content, writer, writer_id, answer) VALUES (?, ?, ?, ?, ?)",
                "원래 제목", "원래 내용", "작성회원", writerId, answer);
        return jdbc.queryForObject("SELECT MAX(id) FROM qna", Long.class);
    }

    private MockHttpSession login(String userId){
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginUser", new MemberSessionDto(userId, "로그인회원"));
        return session;
    }

    private Map<String, Object> row(long id){
        return jdbc.queryForMap("SELECT title, content, writer FROM qna WHERE id = ?", id);
    }

    private String html(String url, MockHttpSession session) throws Exception {
        var request = get(url);
        if (session != null) request.session(session);
        return mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 수정_버튼은_본인_글이고_답변_전일_때만() throws Exception {
        long mine = insert("owner01", null);
        long answered = insert("owner01", "관리자 답변");
        assertThat(html("/qna/" + mine, login("owner01"))).contains("/qna/" + mine + "/edit?page=1");
        assertThat(html("/qna/" + mine, login("other02"))).doesNotContain("/edit");
        assertThat(html("/qna/" + mine, null)).doesNotContain("/edit");
        assertThat(html("/qna/" + answered, login("owner01"))).doesNotContain("/edit");
    }

    @Test
    void 수정_화면은_기존_내용이_채워진_수정_모드() throws Exception {
        long id = insert("owner01", null);
        String page = html("/qna/" + id + "/edit?page=2&type=title&keyword=x", login("owner01"));
        assertThat(page).contains("질문 수정하기", "수정하기", "원래 제목", "원래 내용", "작성회원",
                "action=\"/qna/" + id + "/edit\"", "name=\"page\" value=\"2\"", "name=\"keyword\" value=\"x\"");
        assertThat(page).contains("href=\"/qna/" + id + "?page=2&amp;type=title&amp;keyword=x\""); // 취소 → 상세
    }

    @Test
    void 본인이_수정하면_저장되고_상세로_돌아감_작성자_이름은_그대로() throws Exception {
        long id = insert("owner01", null);
        mvc.perform(post("/qna/" + id + "/edit").session(login("owner01"))
                        .param("title", "  고친 제목 ").param("content", "고친\r\n내용")
                        .param("page", "3").param("type", "content").param("keyword", "대출"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna/" + id + "?page=3&type=content&keyword=%EB%8C%80%EC%B6%9C"))
                .andExpect(flash().attribute("message", "글이 수정되었습니다."));
        Map<String, Object> r = row(id);
        assertThat(r.get("title")).isEqualTo("고친 제목");
        assertThat(r.get("content")).isEqualTo("고친\n내용");
        assertThat(r.get("writer")).isEqualTo("작성회원"); // 로그인 이름(로그인회원)으로 바뀌지 않음
    }

    @Test
    void 입력_오류면_수정_화면에_오류와_입력값_유지() throws Exception {
        long id = insert("owner01", null);
        mvc.perform(post("/qna/" + id + "/edit").session(login("owner01")).param("title", "").param("content", "남긴 내용"))
                .andExpect(status().isOk())
                .andExpect(view().name("qna/write"))
                .andExpect(model().attribute("editId", id))
                .andExpect(model().attribute("content", "남긴 내용"))
                .andExpect(model().attributeExists("errors"));
        assertThat(row(id).get("title")).isEqualTo("원래 제목");
    }

    @Test
    void 다른_회원은_수정_화면도_저장도_불가() throws Exception {
        long id = insert("owner01", null);
        mvc.perform(get("/qna/" + id + "/edit").session(login("other02")))
                .andExpect(redirectedUrl("/qna/" + id + "?page=1"))
                .andExpect(flash().attribute("errorMessage", "본인이 작성한 글만 수정할 수 있습니다."));
        mvc.perform(post("/qna/" + id + "/edit").session(login("other02")).param("title", "몰래").param("content", "몰래"))
                .andExpect(flash().attribute("errorMessage", "본인이 작성한 글만 수정할 수 있습니다."));
        assertThat(row(id).get("title")).isEqualTo("원래 제목");
    }

    @Test
    void 답변이_달린_글은_수정_불가() throws Exception {
        long id = insert("owner01", "관리자 답변");
        mvc.perform(post("/qna/" + id + "/edit").session(login("owner01")).param("title", "고침").param("content", "고침"))
                .andExpect(flash().attribute("errorMessage", "답변이 등록된 글은 수정할 수 없습니다."));
        assertThat(row(id).get("title")).isEqualTo("원래 제목");
    }

    @Test
    void 예전_글은_수정_불가() throws Exception {
        long id = insert(null, null);
        mvc.perform(get("/qna/" + id + "/edit").session(login("owner01")))
                .andExpect(flash().attribute("errorMessage", "본인이 작성한 글만 수정할 수 있습니다."));
    }

    @Test
    void 비로그인은_로그인_후_수정_화면으로_복귀() throws Exception {
        long id = insert("owner01", null);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/qna/" + id + "/edit").session(session)).andExpect(redirectedUrl("/member/login"));
        assertThat(LoginRedirect.pop(session)).isEqualTo("/qna/" + id + "/edit?page=1");
        mvc.perform(post("/qna/" + id + "/edit").param("title", "고침").param("content", "고침"))
                .andExpect(redirectedUrl("/member/login"));
        assertThat(row(id).get("title")).isEqualTo("원래 제목");
    }

    @Test
    void 없는_글은_목록으로() throws Exception {
        long missing = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM qna", Long.class);
        mvc.perform(get("/qna/" + missing + "/edit").session(login("owner01")))
                .andExpect(redirectedUrl("/qna?page=1"))
                .andExpect(flash().attribute("errorMessage", "삭제되었거나 존재하지 않는 글입니다."));
    }
}
