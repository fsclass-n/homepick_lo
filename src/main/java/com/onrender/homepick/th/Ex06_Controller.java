package com.onrender.homepick.th;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class Ex06_Controller {

    @GetMapping("/th/ex06")
    public String post(Model model) {
        model.addAttribute("postId", 1); // 기본 1번으로 지정
        return "th/ex06";
    }

    // 경로 변수 매핑 (/th/ex06/1, /th/ex06/2 ...)
    @GetMapping("/th/ex06/{id}")
    public String postDetail(@PathVariable("id") int id, Model model) {

        // 넘겨받은 글 번호를 화면으로 전달
        model.addAttribute("postId", id);

        return "th/ex06"; // templates/th/ex06.html 반환
    }
}
