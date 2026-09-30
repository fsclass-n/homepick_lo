package com.onrender.homepick.service;

import com.onrender.homepick.dto.QnaResponse;
import com.onrender.homepick.repository.JdbcQnaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QnaService{

    private final JdbcQnaRepository qnaRepository;

    public List<QnaResponse> getQnaList(){
        return qnaRepository.findAll();
    }
}