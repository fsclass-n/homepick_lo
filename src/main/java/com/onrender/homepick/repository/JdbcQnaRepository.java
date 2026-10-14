package com.onrender.homepick.repository;

import com.onrender.homepick.dto.QnaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcQnaRepository{

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<QnaResponse> rowMapper = (rs, rowNum) -> {
        var timestamp = rs.getTimestamp("created_at");
        return QnaResponse.builder()
                .id(rs.getLong("id"))
                .title(rs.getString("title"))
                .content(rs.getString("content"))
                .writer(rs.getString("writer"))
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

    // 게시글 수 (페이지 수 계산용, 검색 조건 반영)
    public long count(String type, String keyword){
        Where w = where(type, keyword);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM qna" + w.sql(), Long.class, w.params().toArray());
        return total != null ? total : 0;
    }

    // 한 페이지 분량만 조회 (최신 글 먼저, 검색 조건 반영)
    public List<QnaResponse> findPage(String type, String keyword, int limit, int offset){
        Where w = where(type, keyword);
        String sql = "SELECT id, title, content, writer, status, created_at FROM qna" + w.sql()
                + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(w.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, rowMapper, params.toArray());
    }
}