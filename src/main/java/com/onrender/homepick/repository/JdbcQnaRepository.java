package com.onrender.homepick.repository;

import com.onrender.homepick.dto.QnaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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

    // 전체 게시글 목록 조회
    public List<QnaResponse> findAll(){
        String sql = "SELECT id, title, content, writer, status, created_at FROM qna ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }
}