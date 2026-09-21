package com.onrender.homepick.th;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Ex02Controller {
    
    @GetMapping("/th/ex02")
    public String ex02(Model model) {
        model.addAttribute("message", "안녕하세요!");

        // user 객체를 모델에 추가 (th:if="${user != null}" 통과)
        model.addAttribute("user", new Ex02Dto("박길동"));
        
        return "th/ex02";
    }
}
