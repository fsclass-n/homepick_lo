package com.onrender.homepick.th;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class Ex07_Controller {

    // @GetMapping("/th/ex07")
    // public String post(Model model) {
    // model.addAttribute("postId", 1); // 기본 1번으로 지정
    // return "th/ex07";
    // }

    // // 경로 변수 매핑 (/th/ex07/1, /th/ex07/2 ...)
    // @GetMapping("/th/ex07/{id}")
    // public String postDetail(@PathVariable("id") int id, Model model) {

    // // 넘겨받은 글 번호를 화면으로 전달
    // model.addAttribute("postId", id);

    // return "th/ex07"; // templates/th/ex07.html 반환
    // }

    @GetMapping({ "/th/ex07", "/th/ex07/{id}" })
    public String postDetail(@PathVariable(name = "id", required = false) Optional<Integer> id, Model model) {
        model.addAttribute("postId", id.orElse(1)); // id가 없으면 1, 있으면 해당 id 사용
        return "th/ex07";
    }
}