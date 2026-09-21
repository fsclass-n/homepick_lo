package com.onrender.homepick.th;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Ex01 {
    
    @GetMapping("/th/ex01")
    public String ex01(Model model) {
        model.addAttribute("message", "안녕!");
        return "th/ex01.html";
    }
}
