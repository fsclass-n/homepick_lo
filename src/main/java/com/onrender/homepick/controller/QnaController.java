package com.onrender.homepick.controller;

import com.onrender.homepick.config.LoginRedirect;
import com.onrender.homepick.dto.MemberSessionDto;
import com.onrender.homepick.dto.QnaPage;
import com.onrender.homepick.dto.QnaResponse;
import com.onrender.homepick.service.QnaService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequestMapping("/qna")
@RequiredArgsConstructor
public class QnaController{

    private final QnaService qnaService;

    // /qna?page=2 → 2페이지 (10개씩), 숫자가 아니면 1페이지
    // /qna?type=title&keyword=전세 → 제목에 '전세'가 들어간 글 (type: all·title·content·writer)
    @GetMapping
    public String list(@RequestParam(defaultValue = "1") String page,
                       @RequestParam(defaultValue = "all") String type,
                       @RequestParam(defaultValue = "") String keyword, HttpSession session, Model model){
        String searchType = QnaService.normalizeType(type);
        String searchKeyword = QnaService.normalizeKeyword(keyword);
        QnaPage qnaPage = qnaService.getQnaPage(parsePage(page), searchType, searchKeyword);
        model.addAttribute("qnaPage", qnaPage);
        model.addAttribute("type", searchType);
        model.addAttribute("keyword", searchKeyword.isEmpty() ? null : searchKeyword);
        // 페이지 링크에 이어 붙일 검색 조건 (검색 중일 때만, URL 인코딩)
        String searchQuery = searchQuery(searchType, searchKeyword);
        model.addAttribute("searchQuery", searchQuery);
        // 상세 보기 링크에 붙여 [목록]으로 돌아올 때 같은 페이지·검색 결과로
        model.addAttribute("listQuery", "page=" + qnaPage.page() + searchQuery);
        model.addAttribute("qnaList", qnaPage.items());
        model.addAttribute("pageSize", QnaService.PAGE_SIZE);
        model.addAttribute("user", session.getAttribute("loginUser"));
        return "qna/list";
    }

    // 상세 보기 (누구나), /qna/12?page=2&type=..&keyword=.. → [목록]이 보던 페이지·검색 결과로 돌아감
    // 숫자가 아닌 주소(/qna/abc)는 이 매핑에 걸리지 않아 404
    @GetMapping("/{id:\\d{1,18}}")
    public String detail(@PathVariable long id,
                         @RequestParam(defaultValue = "1") String page,
                         @RequestParam(defaultValue = "all") String type,
                         @RequestParam(defaultValue = "") String keyword,
                         HttpSession session, Model model, RedirectAttributes redirect){
        QnaResponse qna = qnaService.getQna(id).orElse(null);
        if (qna == null) {
            redirect.addFlashAttribute("errorMessage", "삭제되었거나 존재하지 않는 글입니다.");
            return "redirect:/qna";
        }
        String listQuery = "page=" + Math.max(parsePage(page), 1)
                + searchQuery(QnaService.normalizeType(type), QnaService.normalizeKeyword(keyword));
        model.addAttribute("qna", qna);
        model.addAttribute("prev", qnaService.getPrev(id).orElse(null));
        model.addAttribute("next", qnaService.getNext(id).orElse(null));
        model.addAttribute("listQuery", listQuery);
        model.addAttribute("user", session.getAttribute("loginUser"));
        return "qna/detail";
    }

    private static int parsePage(String page){
        return page.matches("-?\\d{1,9}") ? Integer.parseInt(page) : 1;
    }

    private static String searchQuery(String type, String keyword){
        return keyword.isEmpty() ? "" : "&type=" + type + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    // 글쓰기 화면 (로그인 필요 → 로그인 후 다시 글쓰기 화면으로)
    @GetMapping("/write")
    public String writeForm(HttpSession session, Model model){
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        if (user == null) {
            LoginRedirect.save(session, "/qna/write");
            return "redirect:/member/login";
        }
        addFormAttributes(model, user);
        return "qna/write";
    }

    // 글 등록: 입력 오류면 입력값을 유지한 채 화면에 오류 표시, 성공하면 목록 1페이지로 (새로고침 시 중복 등록 방지: PRG)
    @PostMapping("/write")
    public String write(@RequestParam(defaultValue = "") String title,
                        @RequestParam(defaultValue = "") String content,
                        HttpSession session, Model model, RedirectAttributes redirect){
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        if (user == null) {
            LoginRedirect.save(session, "/qna/write");
            return "redirect:/member/login";
        }
        String cleanTitle = title.strip();
        String cleanContent = content.replace("\r\n", "\n").strip(); // 줄바꿈 1글자로 계산 (브라우저 글자 수와 동일)

        Map<String, String> errors = QnaService.validate(cleanTitle, cleanContent);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("title", cleanTitle);
            model.addAttribute("content", cleanContent);
            addFormAttributes(model, user);
            return "qna/write";
        }

        // 작성자는 입력받지 않고 로그인한 회원 이름으로 (다른 사람 이름으로 쓰기 방지)
        qnaService.write(cleanTitle, cleanContent, writerName(user));
        redirect.addFlashAttribute("message", "질문이 등록되었습니다. 답변이 달리면 상태가 '답변완료'로 바뀝니다.");
        return "redirect:/qna";
    }

    private static void addFormAttributes(Model model, MemberSessionDto user){
        model.addAttribute("user", user); // 헤더의 로그인 상태 표시
        model.addAttribute("writer", writerName(user));
        model.addAttribute("titleMax", QnaService.TITLE_MAX);
        model.addAttribute("contentMax", QnaService.CONTENT_MAX);
    }

    private static String writerName(MemberSessionDto user){
        String name = user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getUserId();
        return name.length() > 50 ? name.substring(0, 50) : name; // writer 컬럼 VARCHAR(50)
    }
}