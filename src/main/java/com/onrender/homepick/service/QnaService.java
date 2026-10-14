package com.onrender.homepick.service;

import com.onrender.homepick.dto.QnaPage;
import com.onrender.homepick.repository.JdbcQnaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public static final int TITLE_MAX = 100;   // DB 는 200자까지지만 목록에서 읽기 좋게 100자로 제한
    public static final int CONTENT_MAX = 2000;

    /** 글쓰기 입력 검사 → 항목별 오류 메시지 (비어 있으면 통과) */
    public static Map<String, String> validate(String title, String content){
        Map<String, String> errors = new LinkedHashMap<>();
        if (title.isEmpty()) errors.put("title", "제목을 입력해 주세요.");
        else if (title.length() > TITLE_MAX) errors.put("title", "제목은 " + TITLE_MAX + "자 이내로 입력해 주세요.");
        if (content.isEmpty()) errors.put("content", "내용을 입력해 주세요.");
        else if (content.length() > CONTENT_MAX) errors.put("content", "내용은 " + CONTENT_MAX + "자 이내로 입력해 주세요.");
        return errors;
    }

    public void write(String title, String content, String writer){
        qnaRepository.insert(title, content, writer);
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