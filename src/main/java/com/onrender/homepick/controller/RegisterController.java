// RegisterController.java
package com.onrender.homepick.controller;

import com.onrender.homepick.dto.RegisterRequest;
import com.onrender.homepick.repository.JdbcMemberRepository;
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
public class RegisterController{

    private final JdbcMemberRepository repository;

    @GetMapping("/register")
    public String form(){
        return "member/register";
    }

    @PostMapping("/register")
    public String process(@ModelAttribute RegisterRequest req, Model model){
        if (repository.existsByEmail(req.getEmail())) {
            model.addAttribute("error", "이미 사용 중인 이메일입니다.");
            return "member/register";
        }
        repository.save(req);

        System.out.println(">> 신규 회원가입 등록 완료: " + req.getName() + " (" + req.getEmail() + ")");
        return "redirect:/member/login";
    }

    @GetMapping("/admin")
    public String memberList(Model model){
        model.addAttribute("members", repository.findAll());
        return "member/admin";
    }
}