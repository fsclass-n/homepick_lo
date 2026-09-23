package com.onrender.homepick.controller;

import com.onrender.homepick.dto.QnaResponse;
import com.onrender.homepick.service.QnaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/qna")
@RequiredArgsConstructor
public class QnaController{

    private final QnaService qnaService;

    @GetMapping
    public String list(Model model){
        List<QnaResponse> qnaList = qnaService.getQnaList();
        model.addAttribute("qnaList", qnaList);
        return "qna/list";
    }
}