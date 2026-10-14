package com.onrender.homepick;

import com.onrender.homepick.dto.MemberSessionDto;
import com.onrender.homepick.repository.JdbcMemberRepository;
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

// Q&A 관리자 답변: 관리자만 등록·수정, 상태 '답변완료', 화면 표시
// @Transactional: 테스트용 글·답변은 끝나면 롤백 → 실제 DB 에 남지 않음
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
class QnaAnswerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JdbcMemberRepository memberRepository;

    private long insertQuestion(){
        jdbc.update("INSERT INTO qna (title, content, writer, writer_id) VALUES (?, ?, ?, ?)",
                "답변 테스트 질문", "질문 내용", "질문회원", "asker01");
        return jdbc.queryForObject("SELECT MAX(id) FROM qna", Long.class);
    }

    private MockHttpSession session(String userId, String name, String role){
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginUser", new MemberSessionDto(userId, name, role));
        return session;
    }

    private MockHttpSession admin(){
        return session("admin01", "홈픽관리자", "ADMIN");
    }

    private Map<String, Object> row(long id){
        return jdbc.queryForMap("SELECT status, answer, answer_writer, answered_at FROM qna WHERE id = ?", id);
    }

    private String detailHtml(long id, MockHttpSession session) throws Exception {
        var request = get("/qna/" + id);
        if (session != null) request.session(session);
        return mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 관리자가_답변하면_답변완료로_바뀌고_화면에_표시() throws Exception {
        long id = insertQuestion();
        mvc.perform(post("/qna/" + id + "/answer").session(admin())
                        .param("answer", "  안녕하세요.\r\n계약서를 확인해 주세요.  ")
                        .param("page", "2").param("type", "title").param("keyword", "전세"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna/" + id + "?page=2&type=title&keyword=%EC%A0%84%EC%84%B8"))
                .andExpect(flash().attribute("message", "답변이 등록되었습니다."));

        Map<String, Object> r = row(id);
        assertThat(r.get("status")).isEqualTo("답변완료");
        assertThat(r.get("answer")).isEqualTo("안녕하세요.\n계약서를 확인해 주세요.");
        assertThat(r.get("answer_writer")).isEqualTo("홈픽관리자");
        assertThat(r.get("answered_at")).isNotNull();

        String html = detailHtml(id, null);
        assertThat(html).contains("홈픽 답변", "안녕하세요.\n계약서를 확인해 주세요.", "홈픽관리자", "답변완료");
        assertThat(html).doesNotContain("qnaAnswerForm"); // 비로그인에게 입력란 없음
    }

    @Test
    void 관리자는_답변을_수정할_수_있음() throws Exception {
        long id = insertQuestion();
        mvc.perform(post("/qna/" + id + "/answer").session(admin()).param("answer", "첫 답변"));
        assertThat(detailHtml(id, admin())).contains("답변 수정", "첫 답변");
        mvc.perform(post("/qna/" + id + "/answer").session(admin()).param("answer", "고친 답변"));
        assertThat(row(id).get("answer")).isEqualTo("고친 답변");
    }

    @Test
    void 일반_회원은_답변할_수_없고_입력란도_없음() throws Exception {
        long id = insertQuestion();
        MockHttpSession user = session("asker01", "질문회원", "USER");
        assertThat(detailHtml(id, user)).doesNotContain("qnaAnswerForm");
        mvc.perform(post("/qna/" + id + "/answer").session(user).param("answer", "몰래 답변"))
                .andExpect(redirectedUrl("/qna/" + id + "?page=1"))
                .andExpect(flash().attribute("errorMessage", "답변은 관리자만 등록할 수 있습니다."));
        assertThat(row(id).get("answer")).isNull();
        assertThat(row(id).get("status")).isEqualTo("대기중");
    }

    @Test
    void 비로그인은_로그인으로_보내고_저장하지_않음() throws Exception {
        long id = insertQuestion();
        mvc.perform(post("/qna/" + id + "/answer").param("answer", "답변"))
                .andExpect(redirectedUrl("/member/login"));
        assertThat(row(id).get("answer")).isNull();
    }

    @Test
    void 빈_답변은_오류_안내하고_저장하지_않음() throws Exception {
        long id = insertQuestion();
        mvc.perform(post("/qna/" + id + "/answer").session(admin()).param("answer", "   "))
                .andExpect(redirectedUrl("/qna/" + id + "?page=1"))
                .andExpect(flash().attribute("answerError", "답변 내용을 입력해 주세요."));
        mvc.perform(post("/qna/" + id + "/answer").session(admin()).param("answer", "가".repeat(2001)))
                .andExpect(flash().attribute("answerError", "답변은 2000자 이내로 입력해 주세요."))
                .andExpect(flash().attributeExists("answerDraft"));
        assertThat(row(id).get("answer")).isNull();
    }

    @Test
    void 없는_글에_답변하면_목록으로() throws Exception {
        long missing = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM qna", Long.class);
        mvc.perform(post("/qna/" + missing + "/answer").session(admin()).param("answer", "답변"))
                .andExpect(redirectedUrl("/qna?page=1"))
                .andExpect(flash().attribute("errorMessage", "삭제되었거나 존재하지 않는 글입니다."));
    }

    @Test
    void 답변의_태그는_글자로만_출력() throws Exception {
        long id = insertQuestion();
        mvc.perform(post("/qna/" + id + "/answer").session(admin()).param("answer", "<script>alert(1)</script>"));
        String html = detailHtml(id, null);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void 회원_권한_조회_관리자와_일반_회원() {
        jdbc.update("INSERT INTO member (user_id, password_hash, name, birth, gender, phone, firebase_uid, role) "
                + "VALUES ('roletest_admin', 'x', '권한테스트', '19900101', 'M', '01000000000', 'test-uid-role', 'ADMIN')");
        assertThat(memberRepository.findRole("roletest_admin")).isEqualTo("ADMIN");
        assertThat(memberRepository.findRole("no_such_user_zz")).isEqualTo("USER");
        assertThat(new MemberSessionDto("a", "b").isAdmin()).isFalse();
        assertThat(new MemberSessionDto("a", "b", "ADMIN").isAdmin()).isTrue();
    }
}
