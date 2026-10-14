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
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        model.addAttribute("user", user);
        // 삭제 버튼은 본인 글에만 (화면 표시용, 실제 권한은 삭제 요청 때 서버에서 다시 확인)
        model.addAttribute("canDelete", QnaService.isOwner(qna, user != null ? user.getUserId() : null));
        model.addAttribute("canEdit", QnaService.isEditable(qna, user != null ? user.getUserId() : null));
        model.addAttribute("isAdmin", user != null && user.isAdmin()); // 답변 입력란 표시
        model.addAttribute("answerMax", QnaService.ANSWER_MAX);
        model.addAttribute("searchType", QnaService.normalizeType(type));
        model.addAttribute("searchKeyword", QnaService.normalizeKeyword(keyword));
        model.addAttribute("listPage", Math.max(parsePage(page), 1));
        return "qna/detail";
    }

    // 답변 등록·수정 (관리자만): 저장하면 상태가 '답변완료'로 바뀌고 같은 상세 화면으로 돌아감
    @PostMapping("/{id:\\d{1,18}}/answer")
    public String answer(@PathVariable long id,
                         @RequestParam(defaultValue = "") String answer,
                         @RequestParam(defaultValue = "1") String page,
                         @RequestParam(defaultValue = "all") String type,
                         @RequestParam(defaultValue = "") String keyword,
                         HttpSession session, RedirectAttributes redirect){
        String listQuery = "page=" + Math.max(parsePage(page), 1)
                + searchQuery(QnaService.normalizeType(type), QnaService.normalizeKeyword(keyword));
        String detailUrl = "redirect:/qna/" + id + "?" + listQuery;
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        if (user == null) {
            LoginRedirect.save(session, "/qna/" + id + "?" + listQuery);
            return "redirect:/member/login";
        }
        if (!user.isAdmin()) {
            redirect.addFlashAttribute("errorMessage", "답변은 관리자만 등록할 수 있습니다.");
            return detailUrl;
        }
        String cleanAnswer = answer.replace("\r\n", "\n").strip();
        String error = QnaService.validateAnswer(cleanAnswer);
        if (error != null) {
            redirect.addFlashAttribute("answerError", error);
            redirect.addFlashAttribute("answerDraft", cleanAnswer); // 입력한 내용 유지
            return detailUrl;
        }
        if (!qnaService.answer(id, cleanAnswer, writerName(user))) {
            redirect.addFlashAttribute("errorMessage", "삭제되었거나 존재하지 않는 글입니다.");
            return "redirect:/qna?" + listQuery;
        }
        redirect.addFlashAttribute("message", "답변이 등록되었습니다.");
        return detailUrl;
    }

    // 글 삭제 (본인 글만): 성공하면 보던 목록으로, 권한이 없으면 상세 화면에 안내
    @PostMapping("/{id:\\d{1,18}}/delete")
    public String delete(@PathVariable long id,
                         @RequestParam(defaultValue = "1") String page,
                         @RequestParam(defaultValue = "all") String type,
                         @RequestParam(defaultValue = "") String keyword,
                         HttpSession session, RedirectAttributes redirect){
        String listQuery = "page=" + Math.max(parsePage(page), 1)
                + searchQuery(QnaService.normalizeType(type), QnaService.normalizeKeyword(keyword));
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        if (user == null) {
            LoginRedirect.save(session, "/qna/" + id + "?" + listQuery);
            return "redirect:/member/login";
        }
        if (qnaService.getQna(id).isEmpty()) {
            redirect.addFlashAttribute("errorMessage", "이미 삭제되었거나 존재하지 않는 글입니다.");
            return "redirect:/qna?" + listQuery;
        }
        if (!qnaService.delete(id, user.getUserId())) {
            redirect.addFlashAttribute("errorMessage", "본인이 작성한 글만 삭제할 수 있습니다.");
            return "redirect:/qna/" + id + "?" + listQuery;
        }
        redirect.addFlashAttribute("message", "글이 삭제되었습니다.");
        return "redirect:/qna?" + listQuery;
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

        // 작성자는 입력받지 않고 로그인한 회원 이름으로 (다른 사람 이름으로 쓰기 방지), 아이디는 본인 확인용
        qnaService.write(cleanTitle, cleanContent, writerName(user), user.getUserId());
        redirect.addFlashAttribute("message", "질문이 등록되었습니다. 답변이 달리면 상태가 '답변완료'로 바뀝니다.");
        return "redirect:/qna";
    }

    // 글 수정 화면 (본인 글 · 답변 전만): 글쓰기 화면을 수정 모드로 사용
    @GetMapping("/{id:\\d{1,18}}/edit")
    public String editForm(@PathVariable long id,
                           @RequestParam(defaultValue = "1") String page,
                           @RequestParam(defaultValue = "all") String type,
                           @RequestParam(defaultValue = "") String keyword,
                           HttpSession session, Model model, RedirectAttributes redirect){
        ListState list = new ListState(page, type, keyword);
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        String blocked = checkEditable(id, user, list, session, redirect);
        if (blocked != null) return blocked;

        QnaResponse qna = qnaService.getQna(id).orElseThrow();
        model.addAttribute("title", qna.getTitle());
        model.addAttribute("content", qna.getContent());
        addEditAttributes(model, user, qna, list);
        return "qna/write";
    }

    // 글 수정 저장: 오류면 입력값 유지, 성공하면 그 글 상세로 (보던 목록 상태 유지)
    @PostMapping("/{id:\\d{1,18}}/edit")
    public String edit(@PathVariable long id,
                       @RequestParam(defaultValue = "") String title,
                       @RequestParam(defaultValue = "") String content,
                       @RequestParam(defaultValue = "1") String page,
                       @RequestParam(defaultValue = "all") String type,
                       @RequestParam(defaultValue = "") String keyword,
                       HttpSession session, Model model, RedirectAttributes redirect){
        ListState list = new ListState(page, type, keyword);
        MemberSessionDto user = (MemberSessionDto) session.getAttribute("loginUser");
        String blocked = checkEditable(id, user, list, session, redirect);
        if (blocked != null) return blocked;

        String cleanTitle = title.strip();
        String cleanContent = content.replace("\r\n", "\n").strip();
        Map<String, String> errors = QnaService.validate(cleanTitle, cleanContent);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("title", cleanTitle);
            model.addAttribute("content", cleanContent);
            addEditAttributes(model, user, qnaService.getQna(id).orElseThrow(), list);
            return "qna/write";
        }
        // 확인 직후 답변이 달리는 경우까지 막기 위해 저장 SQL 에서도 본인 · 답변 전 조건을 다시 검사
        if (!qnaService.update(id, user.getUserId(), cleanTitle, cleanContent)) {
            redirect.addFlashAttribute("errorMessage", "답변이 등록되었거나 수정할 수 없는 글입니다.");
            return "redirect:/qna/" + id + "?" + list.query();
        }
        redirect.addFlashAttribute("message", "글이 수정되었습니다.");
        return "redirect:/qna/" + id + "?" + list.query();
    }

    /** 수정할 수 없으면 이동할 주소(redirect:...), 수정 가능하면 null */
    private String checkEditable(long id, MemberSessionDto user, ListState list, HttpSession session, RedirectAttributes redirect){
        if (user == null) {
            LoginRedirect.save(session, "/qna/" + id + "/edit?" + list.query());
            return "redirect:/member/login";
        }
        QnaResponse qna = qnaService.getQna(id).orElse(null);
        if (qna == null) {
            redirect.addFlashAttribute("errorMessage", "삭제되었거나 존재하지 않는 글입니다.");
            return "redirect:/qna?" + list.query();
        }
        if (!QnaService.isOwner(qna, user.getUserId())) {
            redirect.addFlashAttribute("errorMessage", "본인이 작성한 글만 수정할 수 있습니다.");
            return "redirect:/qna/" + id + "?" + list.query();
        }
        if (qna.getAnswer() != null) {
            redirect.addFlashAttribute("errorMessage", "답변이 등록된 글은 수정할 수 없습니다.");
            return "redirect:/qna/" + id + "?" + list.query();
        }
        return null;
    }

    private static void addEditAttributes(Model model, MemberSessionDto user, QnaResponse qna, ListState list){
        addFormAttributes(model, user);
        model.addAttribute("editId", qna.getId());      // 있으면 수정 모드
        model.addAttribute("writer", qna.getWriter());  // 작성자 이름은 처음 등록한 그대로
        model.addAttribute("listPage", list.page());
        model.addAttribute("searchType", list.type());
        model.addAttribute("searchKeyword", list.keyword());
        model.addAttribute("listQuery", list.query());
    }

    /** 목록에서 보던 페이지 · 검색 조건 (정리된 값) */
    private record ListState(int page, String type, String keyword){
        ListState(String page, String type, String keyword){
            this(Math.max(parsePage(page), 1), QnaService.normalizeType(type), QnaService.normalizeKeyword(keyword));
        }

        String query(){
            return "page=" + page + searchQuery(type, keyword);
        }
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