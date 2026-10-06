package com.onrender.homepick.controller;

import com.onrender.homepick.service.AiDataRefreshService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
@RequestMapping("/api/ai-data")
@RequiredArgsConstructor
public class AiDataController{

    private final AiDataRefreshService refreshService;

    // 최신 실거래 데이터로 재학습 요청 (로그인 사용자만)
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(HttpSession session){
        if (session.getAttribute("loginUser") == null) {
            return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");
        }
        try {
            String error = refreshService.trigger();
            if (error != null) {
                HttpStatus status = refreshService.isConfigured() ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
                return fail(status, error);
            }
            return ResponseEntity.accepted().body(Map.of("success", true, "message", "최신 데이터 수집을 시작했습니다."));
        } catch (RestClientException e) {
            System.err.println(">> AI 데이터 갱신 요청 실패: " + e.getMessage());
            return fail(HttpStatus.BAD_GATEWAY, "갱신 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // 진행 상태 (크롤링·학습·배포 반영 여부)
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(){
        try {
            return ResponseEntity.ok(refreshService.status());
        } catch (RestClientException e) {
            System.err.println(">> AI 데이터 상태 조회 실패: " + e.getMessage());
            return fail(HttpStatus.BAD_GATEWAY, "진행 상태를 확인할 수 없습니다.");
        }
    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message){
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message));
    }
}
