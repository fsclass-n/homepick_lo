package com.onrender.homepick.service;

import com.onrender.homepick.dto.QnaResponse;
import com.onrender.homepick.repository.JdbcQnaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 단 또는 메서드 단에 추가
public class QnaService{

    private final JdbcQnaRepository qnaRepository;

    public List<QnaResponse> getQnaList(){
        return qnaRepository.findAll();
    }
}