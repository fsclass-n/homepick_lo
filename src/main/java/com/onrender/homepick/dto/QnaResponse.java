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
    private String writerId;            // 작성 회원 아이디 (본인 확인용, 화면에는 표시하지 않음)
    private String status;              // 처리 상태 ('대기중', '답변완료' 등)
    private LocalDateTime createdAt;    // 작성일
    private String answer;              // 관리자 답변 (없으면 null)
    private String answerWriter;        // 답변한 관리자 이름
    private LocalDateTime answeredAt;   // 답변 일시
}
