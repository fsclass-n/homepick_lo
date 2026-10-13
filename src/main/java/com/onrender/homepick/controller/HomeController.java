package com.onrender.homepick.controller;

import com.onrender.homepick.dto.MemberSessionDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController{

    @GetMapping("/")
    public String index(HttpSession session, Model model){
        // 다운캐스팅(강제타입변환: 큰 -> 작은): Object -> MemberSessionDto
        // 데이터 타입 객체 = (강제타입) session.getAttribute("변수");
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        model.addAttribute("user", user);
        return "index"; // templates/index.html
    }

    // AI 맞춤 매물 추천 (프론트엔드 화면)
    @GetMapping("/ai/recommend")
    public String aiRecommend(HttpSession session, Model model){
        model.addAttribute("user", session.getAttribute("loginUser"));
        return "sub/ai-recommend"; // templates/sub/ai-recommend.html
    }

    @Value("${kakao.map.js-key:}")
    private String kakaoMapJsKey;

    // 지도 중심 실거래 조회 (카카오맵, 누구나 이용 가능)
    @GetMapping("/map")
    public String map(HttpSession session, Model model){
        model.addAttribute("user", session.getAttribute("loginUser"));
        model.addAttribute("kakaoJsKey", kakaoMapJsKey);
        return "sub/map"; // templates/sub/map.html
    }
}