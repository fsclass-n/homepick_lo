package com.onrender.homepick.repository;

import com.onrender.homepick.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcMemberRepository{

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RegisterRequest> memberRowMapper = (rs, rowNum) -> {
        RegisterRequest member = new RegisterRequest();
        member.setEmail(rs.getString("email"));
        member.setPassword(rs.getString("password"));
        member.setName(rs.getString("name"));
        return member;
    };

    public boolean existsByEmail(String email){
        String sql = "SELECT COUNT(*) FROM member WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public void save(RegisterRequest member){
        String sql = "INSERT INTO member (email, password, name) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, member.getEmail(), member.getPassword(), member.getName());
    }

    public Optional<RegisterRequest> findByEmail(String email){
        String sql = "SELECT email, password, name FROM member WHERE email = ?";
        return jdbcTemplate.query(sql, memberRowMapper, email)
                .stream()
                .findFirst();
    }

    public Collection<RegisterRequest> findAll(){
        String sql = "SELECT email, password, name FROM member ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, memberRowMapper);
    }
}