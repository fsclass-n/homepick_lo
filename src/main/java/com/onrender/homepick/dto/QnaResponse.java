package com.onrender.homepick.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QnaResponse{
    private Long id;                    // 번호
    private String title;               // 제목
    private String content;             // 내용
    private String writer;              // 작성자
    private String status;              // 처리 상태 ('대기중', '답변완료' 등)
    private LocalDateTime createdAt;    // 작성일
}
