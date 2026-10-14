/**
 * static/js/qna-write.js
 * Q&A 글쓰기 - 글자 수 표시, 빈 칸 안내, 중복 등록 방지 (최종 검사는 서버에서 다시 함)
 */
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('qnaWriteForm');
    const btn = document.getElementById('btnQnaSubmit');
    const fields = [
        { input: document.getElementById('title'), count: document.getElementById('titleCount'),
          feedback: document.getElementById('titleFeedback'), empty: '제목을 입력해 주세요.' },
        { input: document.getElementById('content'), count: document.getElementById('contentCount'),
          feedback: document.getElementById('contentFeedback'), empty: '내용을 입력해 주세요.' },
    ];

    // 줄바꿈은 서버와 같이 1글자로 계산
    const length = el => el.value.replace(/\r\n/g, '\n').length;

    fields.forEach(f => {
        const update = () => { f.count.textContent = length(f.input); };
        update();
        f.input.addEventListener('input', () => {
            update();
            if (f.input.value.trim()) f.input.classList.remove('is-invalid');
        });
    });

    form.addEventListener('submit', e => {
        let firstInvalid = null;
        fields.forEach(f => {
            const empty = !f.input.value.trim();
            if (empty) {
                f.feedback.textContent = f.empty;
                f.input.classList.add('is-invalid');
                firstInvalid ??= f.input;
            }
        });
        if (firstInvalid) {
            e.preventDefault();
            firstInvalid.focus();
            return;
        }
        // 등록 버튼 연타로 같은 글이 여러 번 저장되지 않게
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>등록 중...';
    });
});
