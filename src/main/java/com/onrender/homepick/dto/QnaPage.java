package com.onrender.homepick.dto;

import java.util.List;

/**
 * Q&A 목록 한 페이지
 * page: 현재 페이지(1부터), startPage~endPage: 화면에 보이는 페이지 번호 범위
 */
public record QnaPage(List<QnaResponse> items, int page, int totalPages, long totalCount,
                      int startPage, int endPage){

    public boolean hasPrev(){
        return page > 1;
    }

    public boolean hasNext(){
        return page < totalPages;
    }
}
