// LoginController.java
package com.onrender.homepick.controller;

import com.onrender.homepick.dto.LoginRequest;
import com.onrender.homepick.dto.MemberSessionDto;
import com.onrender.homepick.dto.RegisterRequest;
import com.onrender.homepick.repository.JdbcMemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/member")
@RequiredArgsConstructor
public class LoginController{

    private final JdbcMemberRepository repository;

    @GetMapping("/login")
    public String form(){
        return "member/login";
    }

    @PostMapping("/login")
    public String process(@ModelAttribute LoginRequest req, HttpSession session, Model model){
        RegisterRequest member = repository.findByEmail(req.getEmail()).orElse(null);

        if (member == null || !member.getPassword().equals(req.getPassword())) {
            model.addAttribute("error", "이메일 또는 비밀번호가 일치하지 않습니다.");
            return "member/login";
        }

        session.setAttribute("loginUser", new MemberSessionDto(member.getEmail(), member.getName()));
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session){
        session.invalidate();
        return "redirect:/";
    }
}