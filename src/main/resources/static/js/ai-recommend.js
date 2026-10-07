/**
 * static/js/ai-recommend.js
 * AI 맞춤 매물 추천 - ml-pipeline 이 생성한 /data/apartments.json 으로 브라우저에서 추천
 *
 * 추천 점수 = 조건 유사도(예산·면적 근접도, StandardScaler 기준) + 저평가 점수(RandomForest) + 최근 거래 가중치
 * 최신 데이터 갱신 = GitHub Actions 에서 크롤링·학습 → JSON 커밋 → 서버가 GitHub 에서 받아 반영 (진행 상태 폴링)
 */
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('aiRecommendForm');
    const budget = document.getElementById('budget');
    const rooms = document.getElementById('rooms');
    const region = document.getElementById('region');
    const btn = document.getElementById('btnAiRecommend');
    const btnLabel = btn.querySelector('.btn-label');
    const result = document.getElementById('aiResult');
    const dataInfo = document.getElementById('aiDataInfo');

    const DATA_URL = '/api/ai-data/apartments'; // 서버가 GitHub 의 최신 학습 결과를 제공
    const STATUS_URL = '/api/ai-data/status';
    const REFRESH_URL = '/api/ai-data/refresh';
    const TOP_N = 6;
    const POLL_MS = 10000;
    let dataPromise = null;

    /* =================================================================
     * 1. 학습 데이터 로드 (첫 요청 때 한 번만, 갱신 완료 시 다시 받음)
     * ================================================================= */
    function loadData(force = false){
        if (!dataPromise || force) {
            const url = force ? `${DATA_URL}?v=${Date.now()}` : DATA_URL;
            dataPromise = fetch(url, { cache: force ? 'no-store' : 'default' }).then(res => {
                if (!res.ok) throw new Error('아직 학습된 추천 데이터가 없습니다. 최신 데이터로 AI 재학습을 먼저 실행해 주세요.');
                return res.json();
            });
            dataPromise.catch(() => { dataPromise = null; });
        }
        return dataPromise;
    }

    function updateDataInfo(){
        loadData()
            .then(d => {
                dataInfo.textContent = `AI 학습 데이터: ${d.generatedAt} 기준 · 실거래 ${(d.tradeCount ?? 0).toLocaleString()}건`;
            })
            .catch(() => { dataInfo.textContent = '아직 학습된 데이터가 없습니다.'; });
    }

    /* =================================================================
     * 2. 조건 검증 및 추천 계산
     * ================================================================= */
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

    // 지역 입력: '서울', '강남구', '서울 강남구', '역삼동' 모두 허용
    function matchRegion(item, keyword){
        const words = keyword.split(/\s+/).filter(Boolean);
        const text = `${item.sido} ${item.sgg} ${item.umd}`;
        return words.every(w => text.includes(w));
    }

    function recommend(data, condition){
        const [, meanArea] = data.scaler.mean;
        const [scalePrice, scaleArea] = data.scaler.scale;
        const latest = Math.max(...data.items.map(i => Date.parse(i.lastDealDate)));
        const DAY = 86400000;

        const inRegion = data.items.filter(i => matchRegion(i, condition.region));
        const candidates = inRegion.filter(i =>
            i.price <= condition.budget && (condition.rooms >= 4 ? i.rooms >= 4 : i.rooms === condition.rooms));

        // 조건 벡터와의 거리: 예산을 최대한 활용하고, 해당 방 수의 평균 면적에 가까울수록 좋음
        const targetArea = candidates.length
            ? candidates.reduce((s, i) => s + i.area, 0) / candidates.length : meanArea;
        const ranked = candidates.map(i => {
            const dPrice = (i.price - condition.budget) / scalePrice;
            const dArea = (i.area - targetArea) / scaleArea;
            const similarity = 1 / (1 + Math.hypot(dPrice, dArea));
            const recency = Math.max(0, 1 - (latest - Date.parse(i.lastDealDate)) / (180 * DAY));
            const score = similarity * 0.5 + Math.min(i.valueScore, 1.5) / 1.5 * 0.35 + recency * 0.15;
            return { ...i, score };
        }).sort((a, b) => b.score - a.score);

        const cheapest = inRegion.length ? Math.min(...inRegion.map(i => i.price)) : null;
        return { list: ranked.slice(0, TOP_N), total: candidates.length, cheapest };
    }

    /* =================================================================
     * 3. 결과 렌더링 (AI 학습 결과임을 문장으로 안내)
     * ================================================================= */
    function formatPrice(manwon){
        const eok = Math.floor(manwon / 10000);
        const rest = manwon % 10000;
        if (!eok) return `${rest.toLocaleString()}만원`;
        return rest ? `${eok}억 ${rest.toLocaleString()}만원` : `${eok}억`;
    }

    function escapeHtml(text){
        return String(text).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    // 단지별 AI 분석 문장 (RandomForest 적정가 대비)
    function insightText(item){
        const diff = Math.round((item.predictedPrice - item.price) / item.predictedPrice * 100);
        if (diff >= 5) return { text: `AI 분석: 예측 적정가보다 ${diff}% 낮게 거래된 저평가 단지입니다.`, isValue: true };
        if (diff <= -5) return { text: `AI 분석: 예측 적정가보다 ${-diff}% 높게 거래된 단지입니다.`, isValue: false };
        return { text: 'AI 분석: 예측 적정가 수준으로 거래된 단지입니다.', isValue: false };
    }

    function aiNote(data){
        const model = data.model || {};
        const period = data.periodFrom ? ` (${data.periodFrom} ~ ${data.periodTo})` : '';
        const accuracy = model.r2 !== undefined ? `, 적정가 예측 정확도 R² ${model.r2}` : '';
        return `<p class="ai-result-note d-flex gap-2 mb-3">
                    <i class="bi bi-robot" aria-hidden="true"></i>
                    <span>이 결과는 AI가 <strong>${escapeHtml(data.generatedAt)}</strong>에 국토교통부 아파트 실거래
                    <strong>${(data.tradeCount ?? 0).toLocaleString()}건</strong>${escapeHtml(period)}을 크롤링·학습한 모델
                    (${escapeHtml(model.name || 'RandomForest + KMeans')}${escapeHtml(accuracy)})로 분석해 추천한 것입니다.</span>
                </p>`;
    }

    function renderCards(rec, data){
        const cards = rec.list.map(i => {
            const insight = insightText(i);
            return `
            <div class="col-12 col-md-6">
                <article class="ai-item h-100 d-flex flex-column gap-2 p-3 rounded-3 bg-white">
                    <div class="d-flex justify-content-between align-items-start gap-2">
                        <h3 class="ai-item-name fw-bold mb-0">${escapeHtml(i.name)}</h3>
                        ${insight.isValue ? '<span class="ai-badge flex-shrink-0">AI 저평가</span>' : ''}
                    </div>
                    <p class="ai-item-loc mb-0"><i class="bi bi-geo-alt"></i> ${escapeHtml(`${i.sido} ${i.sgg} ${i.umd}`)}</p>
                    <p class="ai-item-price fw-bold mb-0">${formatPrice(i.price)}</p>
                    <ul class="ai-item-meta list-unstyled d-flex flex-wrap gap-2 mb-0">
                        <li>${i.rooms >= 4 ? '4방+' : `${i.rooms}방`} (추정)</li>
                        <li>전용 ${i.area}㎡</li>
                        <li>${i.buildYear}년 준공</li>
                        <li>최근 거래 ${i.lastDealDate}</li>
                    </ul>
                    <p class="ai-item-insight mb-0 ${insight.isValue ? 'is-value' : ''}">${insight.text}</p>
                    <p class="ai-item-ai mb-0 mt-auto">AI 적정가 ${formatPrice(i.predictedPrice)} · 매칭 ${Math.round(i.score * 100)}점</p>
                </article>
            </div>`;
        }).join('');

        return `
            <div class="d-flex justify-content-between align-items-end flex-wrap gap-2 mb-3">
                <h2 class="ai-result-title fw-bold mb-0">AI 추천 결과 <span>${rec.total.toLocaleString()}개 중 상위 ${rec.list.length}개</span></h2>
                <small class="ai-result-source">${escapeHtml(data.source)}</small>
            </div>
            ${aiNote(data)}
            <div class="row g-3">${cards}</div>`;
    }

    function renderMessage(html){
        result.classList.add('is-empty');
        result.innerHTML = html;
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!validate()) return;

        const condition = {
            budget: Number(budget.value),
            rooms: Number(rooms.value),
            region: region.value.trim()
        };

        setLoading(true);
        result.classList.add('d-none');

        try {
            const data = await loadData();
            const rec = recommend(data, condition);
            result.classList.remove('is-empty');

            if (rec.list.length) {
                result.innerHTML = renderCards(rec, data);
            } else if (rec.cheapest === null) {
                renderMessage(`'${escapeHtml(condition.region)}' 지역의 거래 데이터가 없습니다. 예) 서울, 강남구, 서울 마포구`);
            } else {
                renderMessage(`조건에 맞는 단지가 없습니다. 이 지역의 최저 거래가는 <strong>${formatPrice(rec.cheapest)}</strong>입니다. 예산이나 방 수를 조정해 보세요.`);
            }
        } catch (err) {
            renderMessage(escapeHtml(err.message));
        } finally {
            setLoading(false);
            result.classList.remove('d-none');
        }
    });

    /* =================================================================
     * 4. 최신 데이터로 AI 재학습 (GitHub Actions 실행 + 진행 상태 표시)
     * ================================================================= */
    const refreshBtn = document.getElementById('btnAiRefresh');
    const panel = document.getElementById('aiRefreshPanel');
    const panelTitle = document.getElementById('aiRefreshTitle');
    const panelElapsed = document.getElementById('aiRefreshElapsed');
    const panelBar = document.getElementById('aiRefreshBar');
    const panelNote = document.getElementById('aiRefreshNote');
    const stepItems = panel.querySelectorAll('[data-step]');
    const spinner = panel.querySelector('.ai-refresh-spinner');
    const doneIcon = panel.querySelector('.ai-refresh-done-icon');
    const failIcon = panel.querySelector('.ai-refresh-fail-icon');
    const DEFAULT_NOTE = panelNote.textContent.trim();

    // GitHub Actions 단계 이름 → 화면 단계 번호
    const STEP_INDEX = { '공공데이터 크롤링': 1, '데이터 전처리': 2, '머신러닝 학습': 3, '학습 결과 반영': 4 };
    const STEP_TITLE = [
        '크롤링 환경을 준비하는 중...',
        '최신 실거래 데이터를 크롤링하는 중...',
        '수집한 데이터를 전처리하는 중...',
        'AI가 새 데이터로 학습하는 중...',
        '학습 결과를 서버에 반영하는 중...'
    ];

    let pollTimer = null;
    let elapsedTimer = null;
    let watching = false; // 이번 화면에서 진행 과정을 지켜보는 중인지

    function setPanelMode(mode){
        panel.classList.remove('d-none');
        panel.classList.toggle('is-failed', mode === 'failed');
        spinner.classList.toggle('d-none', mode !== 'running');
        doneIcon.classList.toggle('d-none', mode !== 'done');
        failIcon.classList.toggle('d-none', mode !== 'failed');
        panelBar.classList.toggle('progress-bar-animated', mode === 'running');
        if (refreshBtn) refreshBtn.disabled = mode === 'running';
    }

    function showStep(index){
        stepItems.forEach(li => {
            const n = Number(li.dataset.step);
            li.classList.toggle('is-done', n < index);
            li.classList.toggle('is-active', n === index);
        });
        panelTitle.textContent = STEP_TITLE[index];
        panelBar.style.width = `${Math.round((index + 0.5) / stepItems.length * 100)}%`;
        panelNote.textContent = DEFAULT_NOTE;
    }

    function startElapsed(startedAt){
        clearInterval(elapsedTimer);
        const start = startedAt ? Date.parse(startedAt) : Date.now();
        const tick = () => {
            const sec = Math.max(0, Math.floor((Date.now() - start) / 1000));
            panelElapsed.textContent = `경과 ${Math.floor(sec / 60)}분 ${String(sec % 60).padStart(2, '0')}초`;
        };
        tick();
        elapsedTimer = setInterval(tick, 1000);
    }

    function stopWatching(){
        clearTimeout(pollTimer);
        clearInterval(elapsedTimer);
        watching = false;
        if (refreshBtn) refreshBtn.disabled = false;
    }

    async function onDone(){
        stopWatching();
        setPanelMode('done');
        stepItems.forEach(li => { li.classList.add('is-done'); li.classList.remove('is-active'); });
        panelBar.style.width = '100%';
        panelTitle.textContent = '최신 데이터로 AI 학습이 완료되었습니다!';
        panelNote.textContent = '이제 AI 추천 받기를 누르면 새로 크롤링·학습된 데이터로 추천합니다.';
        await loadData(true).catch(() => {});
        updateDataInfo();
    }

    function onFailed(message, step){
        stopWatching();
        setPanelMode('failed');
        // 실패한 단계를 표시 (그 이전 단계는 완료)
        const failedIndex = STEP_INDEX[step];
        if (failedIndex !== undefined) {
            stepItems.forEach(li => {
                const n = Number(li.dataset.step);
                li.classList.toggle('is-done', n < failedIndex);
                li.classList.toggle('is-active', n === failedIndex);
            });
        }
        panelTitle.textContent = message || '데이터 갱신에 실패했습니다.';
        panelNote.textContent = '기존 학습 데이터로 계속 추천합니다. 잠시 후 다시 시도해 주세요.';
    }

    async function poll(){
        try {
            const res = await fetch(STATUS_URL, { cache: 'no-store' });
            if (res.ok) {
                const s = await res.json();
                if (s.state === 'running' || s.state === 'syncing') {
                    if (!watching) { watching = true; startElapsed(s.runStartedAt); }
                    setPanelMode('running');
                    showStep(s.state === 'syncing' ? 4 : (STEP_INDEX[s.step] ?? 0));
                } else if (watching && s.state === 'done') {
                    return onDone();
                } else if (watching && s.state === 'failed') {
                    return onFailed(s.message || '크롤링 또는 학습 중 오류가 발생했습니다.', s.step);
                } else {
                    return stopWatching(); // 진행 중인 작업 없음
                }
            }
            // 서버 재배포 중에는 응답이 실패할 수 있으므로 계속 확인
        } catch (e) { /* 재배포 중 연결 끊김 → 다음 주기에 재시도 */ }
        if (watching) pollTimer = setTimeout(poll, POLL_MS);
    }

    if (refreshBtn) {
        refreshBtn.addEventListener('click', async () => {
            if (!confirm('공공데이터를 새로 크롤링하고 AI를 다시 학습합니다.\n완료까지 약 3~5분 걸립니다. 진행할까요?')) return;

            refreshBtn.disabled = true;
            try {
                const res = await fetch(REFRESH_URL, { method: 'POST' });
                const body = await res.json().catch(() => ({}));
                if (!res.ok) {
                    refreshBtn.disabled = false;
                    alert(body.message || '갱신 요청에 실패했습니다.');
                    return;
                }
                watching = true;
                setPanelMode('running');
                showStep(0);
                startElapsed();
                pollTimer = setTimeout(poll, 3000);
            } catch (e) {
                refreshBtn.disabled = false;
                alert('서버와 통신 중 오류가 발생했습니다.');
            }
        });
    }

    // 페이지 진입 시: 데이터 기준 시각 표시 + 진행 중인 갱신이 있으면 이어서 표시
    updateDataInfo();
    poll();
});
