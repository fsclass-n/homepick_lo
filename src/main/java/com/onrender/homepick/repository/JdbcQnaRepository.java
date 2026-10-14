package com.onrender.homepick.repository;

import com.onrender.homepick.dto.QnaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcQnaRepository{

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<QnaResponse> rowMapper = (rs, rowNum) -> {
        var timestamp = rs.getTimestamp("created_at");
        var answeredAt = rs.getTimestamp("answered_at");
        return QnaResponse.builder()
                .answer(rs.getString("answer"))
                .answerWriter(rs.getString("answer_writer"))
                .answeredAt(answeredAt != null ? answeredAt.toLocalDateTime() : null)
                .id(rs.getLong("id"))
                .title(rs.getString("title"))
                .content(rs.getString("content"))
                .writer(rs.getString("writer"))
                .writerId(rs.getString("writer_id"))
                .status(rs.getString("status"))
                .createdAt(timestamp != null ? timestamp.toLocalDateTime() : null)
                .build();
    };

    /**
     * 검색 조건 → WHERE 절과 바인딩 값 (키워드가 비어 있으면 조건 없음)
     * type: all(제목+내용) | title | content | writer — 컬럼명은 정해진 값만 사용(SQL 주입 방지)
     */
    private record Where(String sql, List<Object> params){}

    private Where where(String type, String keyword){
        if (keyword == null || keyword.isBlank()) return new Where("", List.of());
        // LIKE 특수문자(% _ \)는 글자 그대로 검색되도록 이스케이프
        String like = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return switch (type) {
            case "title" -> new Where(" WHERE title LIKE ?", List.of(like));
            case "content" -> new Where(" WHERE content LIKE ?", List.of(like));
            case "writer" -> new Where(" WHERE writer LIKE ?", List.of(like));
            default -> new Where(" WHERE (title LIKE ? OR content LIKE ?)", List.of(like, like));
        };
    }

    private static final String COLUMNS =
            "SELECT id, title, content, writer, writer_id, status, created_at, answer, answer_writer, answered_at FROM qna";

    // 글 하나 (상세 보기)
    public Optional<QnaResponse> findById(long id){
        return jdbcTemplate.query(COLUMNS + " WHERE id = ?", rowMapper, id).stream().findFirst();
    }

    // 이전 글 = 바로 아래 번호(더 오래된 글), 다음 글 = 바로 위 번호(더 최근 글)
    public Optional<QnaResponse> findPrev(long id){
        return jdbcTemplate.query(COLUMNS + " WHERE id < ? ORDER BY id DESC LIMIT 1", rowMapper, id).stream().findFirst();
    }

    public Optional<QnaResponse> findNext(long id){
        return jdbcTemplate.query(COLUMNS + " WHERE id > ? ORDER BY id ASC LIMIT 1", rowMapper, id).stream().findFirst();
    }

    // 새 글 등록 (상태는 DB 기본값 '대기중', 작성일은 현재 시각)
    public void insert(String title, String content, String writer, String writerId){
        jdbcTemplate.update("INSERT INTO qna (title, content, writer, writer_id) VALUES (?, ?, ?, ?)",
                title, content, writer, writerId);
    }

    // 본인 글 수정: 번호 · 작성자 아이디가 맞고 아직 답변이 없을 때만 → 바뀐 건수(0 또는 1)
    public int updateByIdAndWriterId(long id, String writerId, String title, String content){
        return jdbcTemplate.update(
                "UPDATE qna SET title = ?, content = ? WHERE id = ? AND writer_id = ? AND answer IS NULL",
                title, content, id, writerId);
    }

    // 관리자 답변 저장(등록·수정 공통) → 상태 '답변완료', 답변 일시는 지금 → 바뀐 건수(0이면 글 없음)
    public int saveAnswer(long id, String answer, String answerWriter){
        return jdbcTemplate.update(
                "UPDATE qna SET answer = ?, answer_writer = ?, answered_at = NOW(), status = '답변완료' WHERE id = ?",
                answer, answerWriter, id);
    }

    // 본인 글만 삭제: 번호와 작성자 아이디가 모두 맞을 때만 지움 → 지운 건수(0 또는 1)
    public int deleteByIdAndWriterId(long id, String writerId){
        return jdbcTemplate.update("DELETE FROM qna WHERE id = ? AND writer_id = ?", id, writerId);
    }

    // 게시글 수 (페이지 수 계산용, 검색 조건 반영)
    public long count(String type, String keyword){
        Where w = where(type, keyword);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM qna" + w.sql(), Long.class, w.params().toArray());
        return total != null ? total : 0;
    }

    // 한 페이지 분량만 조회 (최신 글 먼저, 검색 조건 반영)
    public List<QnaResponse> findPage(String type, String keyword, int limit, int offset){
        Where w = where(type, keyword);
        String sql = COLUMNS + w.sql()
                + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(w.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, rowMapper, params.toArray());
    }
}