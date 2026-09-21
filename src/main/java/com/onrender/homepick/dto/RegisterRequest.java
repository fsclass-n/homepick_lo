package com.onrender.homepick.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest{
    private String email;
    private String password;
    private String name;
}