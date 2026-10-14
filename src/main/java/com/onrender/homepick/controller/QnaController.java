package com.onrender.homepick.controller;

import com.onrender.homepick.dto.QnaPage;
import com.onrender.homepick.service.QnaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/qna")
@RequiredArgsConstructor
public class QnaController{

    private final QnaService qnaService;

    // /qna?page=2 → 2페이지 (10개씩), 숫자가 아니면 1페이지
    // /qna?type=title&keyword=전세 → 제목에 '전세'가 들어간 글 (type: all·title·content·writer)
    @GetMapping
    public String list(@RequestParam(defaultValue = "1") String page,
                       @RequestParam(defaultValue = "all") String type,
                       @RequestParam(defaultValue = "") String keyword, Model model){
        int pageNo = page.matches("-?\\d{1,9}") ? Integer.parseInt(page) : 1;
        String searchType = QnaService.normalizeType(type);
        String searchKeyword = QnaService.normalizeKeyword(keyword);
        QnaPage qnaPage = qnaService.getQnaPage(pageNo, searchType, searchKeyword);
        model.addAttribute("qnaPage", qnaPage);
        model.addAttribute("type", searchType);
        model.addAttribute("keyword", searchKeyword.isEmpty() ? null : searchKeyword);
        // 페이지 링크에 이어 붙일 검색 조건 (검색 중일 때만, URL 인코딩)
        model.addAttribute("searchQuery", searchKeyword.isEmpty() ? ""
                : "&type=" + searchType + "&keyword=" + URLEncoder.encode(searchKeyword, StandardCharsets.UTF_8));
        model.addAttribute("qnaList", qnaPage.items());
        model.addAttribute("pageSize", QnaService.PAGE_SIZE);
        return "qna/list";
    }
}