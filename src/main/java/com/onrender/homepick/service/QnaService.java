package com.onrender.homepick.service;

import com.onrender.homepick.dto.QnaPage;
import com.onrender.homepick.repository.JdbcQnaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QnaService{

    public static final int PAGE_SIZE = 10;  // 한 페이지 글 수
    private static final int PAGE_BLOCK = 5; // 한 번에 보여줄 페이지 번호 개수

    private final JdbcQnaRepository qnaRepository;

    public static final List<String> SEARCH_TYPES = List.of("all", "title", "content", "writer");
    private static final int KEYWORD_MAX = 50;

    /** 검색 종류: 정해진 값이 아니면 제목+내용(all) */
    public static String normalizeType(String type){
        return SEARCH_TYPES.contains(type) ? type : "all";
    }

    /** 검색어: 앞뒤 공백 제거, 최대 50자 */
    public static String normalizeKeyword(String keyword){
        String k = keyword == null ? "" : keyword.strip();
        return k.length() > KEYWORD_MAX ? k.substring(0, KEYWORD_MAX) : k;
    }

    public QnaPage getQnaPage(int requestedPage, String type, String keyword){
        long total = qnaRepository.count(type, keyword);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        int page = Math.min(Math.max(requestedPage, 1), totalPages); // 범위 밖 페이지는 처음/끝으로

        // 현재 페이지가 가운데 오도록 번호 범위 계산 (예: 7페이지 → 5~9)
        int start = Math.max(1, page - PAGE_BLOCK / 2);
        int end = Math.min(totalPages, start + PAGE_BLOCK - 1);
        start = Math.max(1, end - PAGE_BLOCK + 1);

        return new QnaPage(qnaRepository.findPage(type, keyword, PAGE_SIZE, (page - 1) * PAGE_SIZE),
                page, totalPages, total, start, end);
    }
}