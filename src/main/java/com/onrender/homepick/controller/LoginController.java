package com.onrender.homepick.controller;

import com.onrender.homepick.dto.LoginRequest;
import com.onrender.homepick.dto.MemberJoinRequest;
import com.onrender.homepick.dto.MemberSessionDto;
import com.onrender.homepick.repository.JdbcMemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String form(){
        return "member/login";
    }

    @PostMapping("/login")
    public String process(@ModelAttribute LoginRequest req, HttpSession session, Model model){
        MemberJoinRequest member = repository.findByUserId(req.getUsername()).orElse(null);

        if (member == null || !passwordEncoder.matches(req.getPassword(), member.getPassword())) {
            model.addAttribute("error", "아이디 또는 비밀번호가 일치하지 않습니다.");
            model.addAttribute("username", req.getUsername());
            return "member/login";
        }

        session.setAttribute("loginUser", new MemberSessionDto(member.getUserId(), member.getName()));
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session){
        session.invalidate();
        return "redirect:/";
    }
}