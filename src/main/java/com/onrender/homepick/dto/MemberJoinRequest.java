package com.onrender.homepick.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberJoinRequest{
    private String name;
    private String birth;
    private String gender;
    private String phone;
    private String userId;
    private String password;
    private String firebaseUid;
}