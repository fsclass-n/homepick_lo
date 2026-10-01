package com.onrender.homepick.controller;

import com.onrender.homepick.dto.RegisterRequest;
import com.onrender.homepick.repository.JdbcMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/member")
@RequiredArgsConstructor
public class RegisterController{

    private final JdbcMemberRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${firebase.web.api-key:}")
    private String firebaseApiKey;
    @Value("${firebase.web.auth-domain:}")
    private String firebaseAuthDomain;
    @Value("${firebase.web.project-id:}")
    private String firebaseProjectId;
    @Value("${firebase.web.storage-bucket:}")
    private String firebaseStorageBucket;
    @Value("${firebase.web.messaging-sender-id:}")
    private String firebaseMessagingSenderId;
    @Value("${firebase.web.app-id:}")
    private String firebaseAppId;

    @GetMapping("/register")
    public String form(Model model){
        model.addAttribute("firebaseConfig", buildFirebaseConfig());
        return "member/register";
    }

    @PostMapping("/register")
    public String process(@ModelAttribute RegisterRequest req, Model model){
        if (repository.existsByEmail(req.getEmail())) {
            model.addAttribute("error", "이미 사용 중인 이메일입니다.");
            model.addAttribute("firebaseConfig", buildFirebaseConfig());
            return "member/register";
        }
        req.setPassword(passwordEncoder.encode(req.getPassword()));
        repository.save(req);

        System.out.println(">> 신규 회원가입 등록 완료: " + req.getName() + " (" + req.getEmail() + ")");
        return "redirect:/member/login";
    }

    @GetMapping("/admin")
    public String memberList(Model model){
        model.addAttribute("members", repository.findAll());
        return "member/admin";
    }

    private Map<String, String> buildFirebaseConfig(){
        if (firebaseApiKey.isBlank() || firebaseAuthDomain.isBlank() || firebaseProjectId.isBlank() || firebaseAppId.isBlank()) {
            return null;
        }
        return Map.of(
                "apiKey", firebaseApiKey,
                "authDomain", firebaseAuthDomain,
                "projectId", firebaseProjectId,
                "storageBucket", firebaseStorageBucket,
                "messagingSenderId", firebaseMessagingSenderId,
                "appId", firebaseAppId
        );
    }
}