package com.onrender.homepick.config;

import jakarta.servlet.http.HttpSession;

/**
 * 로그인이 필요한 페이지(예: Q&A 글쓰기)에서 로그인 화면으로 보낼 때, 로그인 후 원래 페이지로 돌아오게 한다.
 * 일반 로그인(LoginController)과 소셜 로그인(OAuth2LoginSuccessHandler)이 함께 사용
 */
public final class LoginRedirect{

    private static final String KEY = "redirectAfterLogin";

    private LoginRedirect(){
    }

    /** 로그인 후 돌아올 주소 저장 (사이트 안의 경로만) */
    public static void save(HttpSession session, String path){
        session.setAttribute(KEY, path);
    }

    /** 저장된 주소를 꺼내고 지움. 없거나 외부 주소처럼 보이면 메인(/) — 오픈 리다이렉트 방지 */
    public static String pop(HttpSession session){
        Object value = session.getAttribute(KEY);
        session.removeAttribute(KEY);
        if (value instanceof String path && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\")) {
            return path;
        }
        return "/";
    }
}
