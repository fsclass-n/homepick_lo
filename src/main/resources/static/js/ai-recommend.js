/**
 * static/js/ai-recommend.js
 * AI 맞춤 매물 추천 - 조건 입력 검증 및 UI 처리 (프론트엔드 전용)
 */
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('aiRecommendForm');
    const budget = document.getElementById('budget');
    const rooms = document.getElementById('rooms');
    const region = document.getElementById('region');
    const btn = document.getElementById('btnAiRecommend');
    const btnLabel = btn.querySelector('.btn-label');
    const result = document.getElementById('aiResult');

    // 입력값 검증 (Bootstrap is-invalid 클래스 사용)
    function validate(){
        const isBudgetOk = Number(budget.value) >= 1;
        const isRegionOk = region.value.trim().length > 0;
        budget.classList.toggle('is-invalid', !isBudgetOk);
        region.classList.toggle('is-invalid', !isRegionOk);
        return isBudgetOk && isRegionOk;
    }

    [budget, region].forEach(input => input.addEventListener('input', () => input.classList.remove('is-invalid')));

    function setLoading(isLoading){
        btn.disabled = isLoading;
        btnLabel.textContent = isLoading ? 'AI가 매물을 분석 중...' : 'AI 추천 받기';
    }

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        if (!validate()) return;

        // 백엔드 API 연동 시 이 값을 전송
        const condition = {
            budget: Number(budget.value),
            rooms: Number(rooms.value),
            region: region.value.trim()
        };

        setLoading(true);
        result.classList.add('d-none');

        // 백엔드 연동 전 임시 처리
        setTimeout(() => {
            setLoading(false);
            result.textContent = `${condition.region} · ${condition.rooms}방 · 최대 ${condition.budget.toLocaleString()}만원 조건으로 추천을 요청했습니다. (추천 결과는 백엔드 연동 후 표시됩니다.)`;
            result.classList.remove('d-none');
        }, 800);
    });
});
