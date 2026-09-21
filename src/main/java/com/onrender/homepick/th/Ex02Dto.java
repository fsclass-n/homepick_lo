package com.onrender.homepick.th;

public class Ex02Dto {
    private String name;

    public Ex02Dto(String name) {
        this.name = name;
    }

    // 타임리프의 ${user.name} 접근을 위해 Getter 필수
    public String getName() {
        return name;
    }
}
