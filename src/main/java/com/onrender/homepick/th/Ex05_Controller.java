package com.onrender.homepick.th;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class Ex05_Controller {

    // 게시판 페이징 매핑 (/th/ex05?page=1)
    @GetMapping("/th/ex05")
    public String boardList(
            @RequestParam(name = "page", defaultValue = "1") int page, Model model) {

        // 현재 페이지 번호를 모델에 담아 화면으로 전달
        model.addAttribute("currentPage", page);

        return "th/ex05"; // templates/th/ex04_board.html 반환
    }
}
