/* ============================================
   footer.js — Homepick 부동산 푸터 상호작용
   ============================================ */

document.addEventListener('DOMContentLoaded', function() {
    
    /* ── 투더탑 버튼 ── */
    const btnTotop = document.getElementById('btnTotop');

    if (btnTotop) {
        // 300px 이상 스크롤 시 버튼 표시
        window.addEventListener('scroll', function () {
            btnTotop.classList.toggle('show', window.scrollY > 300);
        });

        // 클릭 시 최상단으로 부드럽게 이동
        btnTotop.addEventListener('click', function () {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }

    /* ── 커스텀 드롭다운 (패밀리사이트) ── */
    const familySelect = document.getElementById('familySelect');
    if (familySelect) {
        const btn = familySelect.querySelector('.custom-select-button');
        const options = familySelect.querySelectorAll('.custom-select-list li');

        // 버튼 클릭 시 토글
        btn.addEventListener('click', function(e) {
            e.stopPropagation(); // 외부 클릭 이벤트 방지
            const isExpanded = btn.getAttribute('aria-expanded') === 'true';
            btn.setAttribute('aria-expanded', !isExpanded);
            familySelect.classList.toggle('open');
        });

        // 항목 클릭 시 새 창 열기
        options.forEach(option => {
            option.addEventListener('click', function() {
                const url = this.getAttribute('data-value');
                if (url && url !== '#') {
                    window.open(url, '_blank');
                }
                // 선택 후 닫기
                btn.setAttribute('aria-expanded', 'false');
                familySelect.classList.remove('open');
            });
        });

        // 드롭다운 외부 클릭 시 닫기
        document.addEventListener('click', function(e) {
            if (!familySelect.contains(e.target)) {
                btn.setAttribute('aria-expanded', 'false');
                familySelect.classList.remove('open');
            }
        });
    }

});