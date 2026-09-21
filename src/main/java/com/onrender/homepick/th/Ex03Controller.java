package com.onrender.homepick.th;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Ex03Controller {
    
    @GetMapping("/th/ex03")
    public String ex03(Model model) {
        model.addAttribute("message", "안녕하세요!");

        // user 객체를 모델에 추가 (th:if="${user != null}" 통과)
        model.addAttribute("user", new Ex02Dto("길동"));

        // itemList 생성 후 Model에 추가
        List<Ex03Dto> items = List.of(
            new Ex03Dto("노트북"),
            new Ex03Dto("무선 마우스"),
            new Ex03Dto("기계식 키보드")
        );
        model.addAttribute("itemList", items);

        return "th/ex03";
    }
}
