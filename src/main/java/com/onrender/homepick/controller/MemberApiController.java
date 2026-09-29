// MemberApiController.java
package com.onrender.homepick.controller;

import com.onrender.homepick.dto.MemberJoinRequest;
import com.onrender.homepick.repository.JdbcMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberApiController{

    // 로그인(login.js)과 동일한 유효성 규칙
    private static final String USERNAME_REGEX = "^[a-zA-Z0-9_]{4,20}$";
    private static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,30}$";

    private final JdbcMemberRepository repository;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody MemberJoinRequest req){
        String error = validate(req);
        if (error != null) {
            return fail(HttpStatus.BAD_REQUEST, error);
        }
        // 동일 휴대폰/본인인증(firebaseUid)으로 여러 아이디 가입 허용 → 아이디 중복만 검사
        try {
            if (repository.existsByUserId(req.getUserId())) {
                return fail(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
            }
            repository.saveMember(req);
        } catch (DuplicateKeyException e) {
            return fail(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        } catch (DataAccessException e) {
            System.err.println(">> 회원가입 DB 오류: " + e.getMostSpecificCause().getMessage());
            try {
                System.err.println(">> 연결 정보: " + repository.describeMemberTable());
            } catch (DataAccessException ignored) {
            }
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "회원가입 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }

        System.out.println(">> 신규 회원가입 등록 완료: " + req.getName() + " (" + req.getUserId() + ")");
        return ResponseEntity.ok(Map.of("success", true, "message", "회원가입이 완료되었습니다."));
    }

    private String validate(MemberJoinRequest req){
        if (isBlank(req.getName()) || req.getName().trim().length() < 2) return "이름을 2자 이상 입력해 주세요.";
        if (req.getBirth() == null || !req.getBirth().matches("\\d{8}")) return "생년월일 8자리를 입력해 주세요.";
        if (req.getGender() == null || !req.getGender().matches("[MF]")) return "성별을 선택해 주세요.";
        if (req.getPhone() == null || !req.getPhone().matches("\\d{10,11}")) return "휴대폰 번호가 올바르지 않습니다.";
        if (isBlank(req.getFirebaseUid())) return "휴대폰 본인인증이 필요합니다.";
        if (req.getUserId() == null || !req.getUserId().matches(USERNAME_REGEX)) return "아이디는 4~20자의 영문, 숫자 조합이어야 합니다.";
        if (req.getPassword() == null || !req.getPassword().matches(PASSWORD_REGEX)) return "비밀번호는 영문, 숫자를 포함하여 8자 이상이어야 합니다.";
        return null;
    }

    private boolean isBlank(String s){
        return s == null || s.isBlank();
    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message){
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message));
    }
}