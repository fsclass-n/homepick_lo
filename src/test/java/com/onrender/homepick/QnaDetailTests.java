package com.onrender.homepick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Q&A 상세 보기: 본문 표시, 이전·다음 글, 목록 복귀 주소, 없는 글 처리
// @Transactional: 테스트용으로 넣은 글은 끝나면 롤백 → 실제 DB 에 남지 않음
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
class QnaDetailTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long insert(String title, String content){
        jdbc.update("INSERT INTO qna (title, content, writer) VALUES (?, ?, ?)", title, content, "상세테스트");
        return jdbc.queryForObject("SELECT MAX(id) FROM qna", Long.class);
    }

    private String html(String url, String... params) throws Exception {
        var request = get(url);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 제목_작성자_본문을_보여주고_태그는_글자로_출력() throws Exception {
        long id = insert("상세 제목 <b>굵게</b>", "첫 줄\n<script>alert(1)</script>");
        String body = html("/qna/" + id);
        assertThat(body).contains("상세 제목 &lt;b&gt;굵게&lt;/b&gt;", "상세테스트", "No. " + id, "대기중");
        assertThat(body).contains("첫 줄\n&lt;script&gt;alert(1)&lt;/script&gt;"); // 줄바꿈 유지, 스크립트 실행 안 됨
        assertThat(body).doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void 이전_다음_글_링크() throws Exception {
        long older = insert("오래된 글", "a");
        long middle = insert("가운데 글", "b");
        long newer = insert("최근 글", "c");
        String body = html("/qna/" + middle);
        assertThat(body).contains("href=\"/qna/" + newer + "?page=1\"", "최근 글");   // 다음 글 = 더 최근
        assertThat(body).contains("href=\"/qna/" + older + "?page=1\"", "오래된 글"); // 이전 글 = 더 오래된
        assertThat(html("/qna/" + newer)).contains("다음 글이 없습니다.");
    }

    @Test
    void 목록_버튼은_보던_페이지와_검색_조건으로_돌아감() throws Exception {
        long id = insert("검색 복귀 테스트", "내용");
        String body = html("/qna/" + id, "page", "2", "type", "title", "keyword", "전세");
        assertThat(body).contains("href=\"/qna?page=2&amp;type=title&amp;keyword=%EC%A0%84%EC%84%B8\"");
        // 잘못된 검색 종류는 제목+내용(all)으로
        assertThat(html("/qna/" + id, "page", "3", "type", "hack", "keyword", "x"))
                .contains("href=\"/qna?page=3&amp;type=all&amp;keyword=x\"");
    }

    @Test
    void 목록_제목은_상세_보기로_연결되고_현재_페이지_정보를_붙임() throws Exception {
        long id = insert("목록 링크 테스트", "내용");
        assertThat(html("/qna")).contains("href=\"/qna/" + id + "?page=1\"");
        assertThat(html("/qna", "type", "title", "keyword", "목록 링크"))
                .contains("href=\"/qna/" + id + "?page=1&amp;type=title&amp;keyword=%EB%AA%A9%EB%A1%9D+%EB%A7%81%ED%81%AC\"");
    }

    @Test
    void 없는_글은_목록으로_보내고_안내() throws Exception {
        long missing = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM qna", Long.class);
        mvc.perform(get("/qna/" + missing))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna"))
                .andExpect(flash().attribute("errorMessage", "삭제되었거나 존재하지 않는 글입니다."));
    }

    @Test
    void 숫자가_아닌_주소는_404() throws Exception {
        mvc.perform(get("/qna/abc")).andExpect(status().isNotFound());
    }

    @Test
    void 글쓰기_주소는_상세_보기와_겹치지_않음() throws Exception {
        mvc.perform(get("/qna/write"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
        mvc.perform(get("/qna")).andExpect(content().string(containsString("qna-title-link")));
    }
}
