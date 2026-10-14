package com.onrender.homepick.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberSessionDto {
    private String userId;
    private String name;
    private String role;    // USER | ADMIN (member.role)

    public MemberSessionDto(String userId, String name){
        this(userId, name, "USER");
    }

    /** 관리자 (Q&A 답변 등록 권한) */
    public boolean isAdmin(){
        return "ADMIN".equals(role);
    }
}
