package com.onrender.homepick.repository;

import com.onrender.homepick.dto.RegisterRequest;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryMemberRepository{

    // Key: email, Value: 가입 정보
    // Map<K, V>
    private final Map<String, RegisterRequest> store = new ConcurrentHashMap<>();

    public boolean existsByEmail(String email){
        // 존재 여부 확인: containsKey(Key)
        return store.containsKey(email);
    }

    // 가입 정보 저장
    public void save(RegisterRequest member){
        // 데이터 추가(수정): put(Key, Value)
        store.put(member.getEmail(), member);
    }

    public Optional<RegisterRequest> findByEmail(String email){
        return Optional.ofNullable(store.get(email));
    }

    // 전체 가입자 목록 조회 메서드 추가
    public Collection<RegisterRequest> findAll() {
        return store.values();
    }
}