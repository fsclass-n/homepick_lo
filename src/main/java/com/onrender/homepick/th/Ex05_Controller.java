package com.onrender.homepick.th;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Ex05_Controller {
    
    @GetMapping("/th/ex05")
    public String ex04() {
        return "th/ex05";
    }

    @GetMapping("/th/ex05/login")
    public String ex04login() {
        return "th/ex05_login";
    }

    @GetMapping("/th/ex05/join")
    public String ex04join() {
        return "th/ex05_join";
    }

    @GetMapping("/th/ex05/faq")
    public String ex04faq() {
        return "th/ex05_faq";
    }
}