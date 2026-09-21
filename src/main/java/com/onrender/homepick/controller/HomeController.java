package com.onrender.homepick.controller;

import com.onrender.homepick.dto.MemberSessionDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController{

    @GetMapping("/")
    public String index(HttpSession session, Model model){
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        model.addAttribute("user", user);
        return "index"; // templates/index.html
    }
}