package com.onrender.homepick.repository;

import com.onrender.homepick.dto.MemberJoinRequest;
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

    // DB 결과를 member 객체로 변환
    private final RowMapper<RegisterRequest> memberRowMapper = (rs, rowNum) -> {
        RegisterRequest member = new RegisterRequest();
        member.setEmail(rs.getString("email"));
        member.setPassword(rs.getString("password"));
        member.setName(rs.getString("name"));
        return member;
    };

    // 이메일 중복 확인
    public boolean existsByEmail(String email){
        String sql = "SELECT COUNT(*) FROM member WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    // 회원 정보 저장
    public void save(RegisterRequest member){
        String sql = "INSERT INTO member (email, password, name) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, member.getEmail(), member.getPassword(), member.getName());
    }

    // email로 회원 조회
    public Optional<RegisterRequest> findByEmail(String email){
        String sql = "SELECT email, password, name FROM member WHERE email = ?";
        return jdbcTemplate.query(sql, memberRowMapper, email)
                .stream()
                .findFirst();
    }

    // 아이디 중복 확인
    public boolean existsByUserId(String userId){
        return count("SELECT COUNT(*) FROM member WHERE user_id = ?", userId);
    }

    // 아이디로 로그인용 회원 조회 (user_id, password_hash, name)
    public Optional<MemberJoinRequest> findByUserId(String userId){
        String sql = "SELECT user_id, password_hash, name FROM member WHERE user_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
                    MemberJoinRequest member = new MemberJoinRequest();
                    member.setUserId(rs.getString("user_id"));
                    member.setPassword(rs.getString("password_hash"));
                    member.setName(rs.getString("name"));
                    return member;
                }, userId)
                .stream()
                .findFirst();
    }

    // 회원가입 (휴대폰 본인인증 기반)
    public void saveMember(MemberJoinRequest member){
        String sql = "INSERT INTO member (user_id, password_hash, name, birth, gender, phone, firebase_uid) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                member.getUserId(), member.getPassword(), member.getName(),
                member.getBirth(), member.getGender(), member.getPhone(), member.getFirebaseUid());
    }

    // (진단용) 현재 연결된 DB 이름과 member 테이블 컬럼 목록
    public String describeMemberTable(){
        String db = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        String columns = String.join(", ", jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'member' ORDER BY ordinal_position",
                String.class));
        return "DB=" + db + ", member 컬럼=[" + columns + "]";
    }

    private boolean count(String sql, String value){
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, value);
        return count != null && count > 0;
    }

    // 전체 회원 목록 조회
    public Collection<RegisterRequest> findAll(){
        String sql = "SELECT email, password, name FROM member ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, memberRowMapper);
    }
}