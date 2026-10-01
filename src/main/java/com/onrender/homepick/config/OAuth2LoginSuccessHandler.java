package com.onrender.homepick.config;

import com.onrender.homepick.dto.MemberJoinRequest;
import com.onrender.homepick.dto.MemberSessionDto;
import com.onrender.homepick.repository.JdbcMemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler{

    private final JdbcMemberRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException{
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            getRedirectStrategy().sendRedirect(request, response, "/member/login");
            return;
        }

        String provider = token.getAuthorizedClientRegistrationId();
        Map<String, Object> attributes = token.getPrincipal().getAttributes();

        String providerId = extractProviderId(provider, attributes);
        if (providerId.isBlank()) {
            getRedirectStrategy().sendRedirect(request, response, "/member/login?error=sns");
            return;
        }

        String snsType = provider.toLowerCase();
        String snsId = providerId;
        String userId = buildUserId(snsType, snsId);
        String displayName = truncate(extractDisplayName(snsType, attributes), 50);
        String email = truncate(extractEmail(snsType, attributes), 100);
        String gender = normalizeGender(extractGender(snsType, attributes));
        String phone = normalizePhone(extractPhone(snsType, attributes));
        if (displayName.isBlank()) displayName = userId;
        if (gender.isBlank()) gender = "M";
        if (phone.isBlank()) phone = "00000000000";

        try {
            MemberJoinRequest existing = repository.findBySnsIdentity(snsType, snsId).orElse(null);
            if (existing == null) {
                existing = repository.findByUserId(userId).orElse(null);
            }
            if (existing == null) {
                MemberJoinRequest member = new MemberJoinRequest();
                member.setUserId(userId);
                member.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                member.setName(displayName);
                member.setBirth("19000101");
                member.setGender(gender);
                member.setPhone(phone);
                member.setEmail(email.isBlank() ? null : email);
                member.setFirebaseUid(truncate("oauth:" + snsType + ":" + snsId, 128));
                member.setSnsType(snsType);
                member.setSnsId(truncate(snsId, 100));
                repository.saveSocialMember(member);
            } else {
                userId = existing.getUserId();
                if (existing.getName() != null && !existing.getName().isBlank()) {
                    displayName = existing.getName();
                }
            }
        } catch (DuplicateKeyException ignored) {
            // 동시 로그인으로 이미 생성된 경우 무시
        }

        request.getSession(true).setAttribute("loginUser", new MemberSessionDto(userId, displayName));
        getRedirectStrategy().sendRedirect(request, response, "/");
    }

    private String extractProviderId(String provider, Map<String, Object> attrs){
        if ("naver".equals(provider)) {
            Map<String, Object> response = getMap(attrs, "response");
            return stringValue(response.get("id"));
        }
        if ("kakao".equals(provider)) {
            return stringValue(attrs.get("id"));
        }
        return stringValue(attrs.get("sub")); // google
    }

    private String extractDisplayName(String provider, Map<String, Object> attrs){
        if ("naver".equals(provider)) {
            Map<String, Object> response = getMap(attrs, "response");
            return firstNonBlank(
                    stringValue(response.get("name")),
                    stringValue(response.get("nickname")),
                    stringValue(response.get("email"))
            );
        }
        if ("kakao".equals(provider)) {
            Map<String, Object> kakaoAccount = getMap(attrs, "kakao_account");
            Map<String, Object> profile = getMap(kakaoAccount, "profile");
            Map<String, Object> properties = getMap(attrs, "properties");
            return firstNonBlank(
                    stringValue(profile.get("nickname")),
                    stringValue(properties.get("nickname")),
                    stringValue(kakaoAccount.get("email"))
            );
        }
        return firstNonBlank(stringValue(attrs.get("name")), stringValue(attrs.get("email"))); // google
    }

    private String extractEmail(String provider, Map<String, Object> attrs){
        if ("naver".equals(provider)) {
            return stringValue(getMap(attrs, "response").get("email"));
        }
        if ("kakao".equals(provider)) {
            return stringValue(getMap(attrs, "kakao_account").get("email"));
        }
        return stringValue(attrs.get("email")); // google
    }

    private String extractGender(String provider, Map<String, Object> attrs){
        if ("naver".equals(provider)) {
            return stringValue(getMap(attrs, "response").get("gender"));
        }
        if ("kakao".equals(provider)) {
            return stringValue(getMap(attrs, "kakao_account").get("gender"));
        }
        return ""; // google 기본 scope 에서는 미제공
    }

    private String extractPhone(String provider, Map<String, Object> attrs){
        if ("naver".equals(provider)) {
            Map<String, Object> response = getMap(attrs, "response");
            return firstNonBlank(stringValue(response.get("mobile")), stringValue(response.get("mobile_e164")));
        }
        if ("kakao".equals(provider)) {
            return stringValue(getMap(attrs, "kakao_account").get("phone_number"));
        }
        return ""; // google 기본 scope 에서는 미제공
    }

    private String buildUserId(String provider, String providerId){
        String normalizedProvider = provider.replaceAll("[^a-zA-Z0-9_]", "_");
        String normalizedId = providerId.replaceAll("[^a-zA-Z0-9_]", "_");
        return truncate("sns_" + normalizedProvider + "_" + normalizedId, 50);
    }

    private String truncate(String value, int max){
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String firstNonBlank(String... values){
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key){
        if (source == null) return Map.of();
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String stringValue(Object value){
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeGender(String value){
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.startsWith("M")) return "M";
        if (normalized.startsWith("F")) return "F";
        return "";
    }

    private String normalizePhone(String value){
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
