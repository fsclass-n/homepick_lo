/**
 * static/js/ai-insights.js
 * AI 시장 인사이트 - ml-pipeline 이 만든 insights(월별 추이·구별 시세·가격 결정 요인·단지 유형)를 시각화
 * (Vanilla JS + SVG/Flexbox, 외부 차트 라이브러리 없음)
 *
 * 이벤트
 *  - ai:data   : 학습 데이터 로드 완료 (ai-recommend.js) → 전체 그리기
 *  - ai:search : 추천 조건 입력 (지역·예산) → 내 지역 강조, 저평가 TOP 지역 필터, 예산에 맞는 유형 표시
 */
(() => {
    let data = null;
    let condition = null;
    const MAX_VALUE_SCORE = 1 / (1 - 0.4); // 적정가 대비 최대 40% 할인까지만 '저평가'로 인정

    const $ = id => document.getElementById(id);

    const escapeHtml = text => String(text).replace(/[&<>"']/g,
        c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    // 만원 → '9억 4,500만' / '2,568만'
    function won(manwon, short = false){
        const eok = Math.floor(manwon / 10000);
        const rest = Math.round(manwon % 10000);
        if (!eok) return `${rest.toLocaleString()}만`;
        if (short) return `${(manwon / 10000).toFixed(1)}억`;
        return rest ? `${eok}억 ${rest.toLocaleString()}만` : `${eok}억`;
    }

    const pct = v => `${Math.round(v * 100)}%`;

    // 받침 유무에 따른 조사 선택: josa('전용면적', '과', '와') → '과'
    function josa(word, withBatchim, withoutBatchim){
        const ch = word.replace(/[^가-힣]/g, '').slice(-1);
        if (!ch) return withBatchim;
        return (ch.charCodeAt(0) - 0xAC00) % 28 ? withBatchim : withoutBatchim;
    }
    const monthLabel = m => `${Number(m.slice(5))}월`;

    // 검색 지역에 포함된 구 이름 (예: '서울 마포구' → '마포구')
    function searchedSgg(){
        if (!condition || !data) return null;
        const hit = data.insights.bySgg.find(s => condition.region.includes(s.sgg));
        return hit ? hit.sgg : null;
    }

    /* ---------------------------------------------------------------
     * 1. 핵심 지표
     * --------------------------------------------------------------- */
    function renderKpis(){
        const s = data.insights.summary;
        const kpis = [
            { icon: 'bi-receipt', label: '분석한 실거래', value: `${data.tradeCount.toLocaleString()}건`, sub: `단지 ${s.complexCount.toLocaleString()}개` },
            { icon: 'bi-cash-stack', label: '서울 중위 거래가', value: won(s.medianPrice), sub: `평당 ${won(s.medianPricePerPyeong)}` },
            { icon: 'bi-tags', label: 'AI 저평가 단지 비율', value: pct(s.valueRatio), sub: '적정가보다 5% 이상 저렴' },
            { icon: 'bi-bullseye', label: 'AI 적정가 예측력', value: `R² ${data.model.r2}`, sub: `평균 오차 ±${won(data.model.mae, true)}` }
        ];
        $('insightKpis').innerHTML = kpis.map(k => `
            <div class="col-6 col-lg-3">
                <div class="ai-kpi h-100 p-3 rounded-4 bg-white">
                    <p class="ai-kpi-label mb-1"><i class="bi ${k.icon}" aria-hidden="true"></i> ${k.label}</p>
                    <p class="ai-kpi-value fw-bold mb-0">${k.value}</p>
                    <p class="ai-kpi-sub mb-0">${k.sub}</p>
                </div>
            </div>`).join('');
        $('insightBasis').textContent =
            `${data.generatedAt} 학습 · 거래기간 ${data.periodFrom} ~ ${data.periodTo}`;
    }

    /* ---------------------------------------------------------------
     * 2. 월별 거래량(막대) + 중위 거래가(선) - SVG
     * --------------------------------------------------------------- */
    function renderMonthly(){
        const months = data.insights.monthly;
        const W = 600, H = 230, P = { t: 24, r: 16, b: 30, l: 16 };
        const innerW = W - P.l - P.r, innerH = H - P.t - P.b;
        const slot = innerW / months.length;
        const maxCount = Math.max(...months.map(m => m.count));
        const prices = months.map(m => m.medianPrice);
        const minP = Math.min(...prices) * 0.95, maxP = Math.max(...prices) * 1.03;
        const y = p => P.t + innerH - (p - minP) / (maxP - minP) * innerH;
        const lagFrom = months.length - 2; // 최근 2개월은 신고 기한(30일) 때문에 집계 중

        const bars = months.map((m, i) => {
            const h = m.count / maxCount * innerH * 0.85;
            const x = P.l + i * slot + slot * 0.2;
            return `<rect class="bar ${i >= lagFrom ? 'is-lag' : ''}" x="${x}" y="${P.t + innerH - h}" width="${slot * 0.6}" height="${h}" rx="4">
                        <title>${monthLabel(m.month)} 거래 ${m.count.toLocaleString()}건</title></rect>
                    <text class="axis" x="${x + slot * 0.3}" y="${H - 8}" text-anchor="middle">${monthLabel(m.month)}</text>
                    <text class="count" x="${x + slot * 0.3}" y="${P.t + innerH - 6}" text-anchor="middle">${m.count.toLocaleString()}</text>`;
        }).join('');

        const points = months.map((m, i) => [P.l + i * slot + slot / 2, y(m.medianPrice)]);
        const line = `<polyline class="line" points="${points.map(p => p.join(',')).join(' ')}"/>` +
            points.map(([px, py], i) => `
                <circle class="dot" cx="${px}" cy="${py}" r="4"><title>${monthLabel(months[i].month)} 중위가 ${won(months[i].medianPrice)}</title></circle>
                <text class="price" x="${px}" y="${py - 9}" text-anchor="middle">${won(months[i].medianPrice, true)}</text>`).join('');

        $('chartMonthly').innerHTML =
            `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="월별 거래량과 중위 거래가">${bars}${line}</svg>`;

        // 해석 문장: 추세는 신고가 끝난 달까지만 비교 (집계 중인 최근 2개월 제외)
        const settled = months.length > 3 ? months.slice(0, lagFrom) : months;
        const first = settled[0], last = settled[settled.length - 1];
        const change = (last.medianPrice - first.medianPrice) / first.medianPrice;
        const busiest = months.reduce((a, b) => (b.count > a.count ? b : a));
        const trend = Math.abs(change) < 0.02 ? '큰 변동 없이 유지' : change > 0 ? `${pct(change)} 상승` : `${pct(-change)} 하락`;
        $('noteMonthly').innerHTML =
            `💡 신고가 마무리된 ${monthLabel(first.month)}~${monthLabel(last.month)} 기준 서울 아파트 중위 거래가는 ` +
            `<strong>${won(first.medianPrice, true)} → ${won(last.medianPrice, true)}</strong>으로 ${trend}했고, ` +
            `거래가 가장 활발했던 달은 <strong>${monthLabel(busiest.month)}(${busiest.count.toLocaleString()}건)</strong>입니다. ` +
            `<span class="ai-note-sub">최근 2개월은 실거래 신고 기한(30일)으로 거래량이 적게 집계될 수 있습니다.</span>`;
    }

    /* ---------------------------------------------------------------
     * 3. 가격 결정 요인 (RandomForest feature importance)
     * --------------------------------------------------------------- */
    const FACTOR_TIP = {
        '전용면적': '같은 지역이라도 넓을수록 가격이 크게 올라갑니다.',
        '지역(구)': '같은 면적이라도 어느 구인지에 따라 가격 차이가 큽니다.',
        '건물 연식': '신축일수록, 또는 재건축 기대가 있을수록 가격에 영향을 줍니다.',
        '층': '층수는 다른 요인보다 가격 영향이 작습니다.'
    };

    function renderImportance(){
        const list = data.insights.importance;
        const max = list[0].weight;
        $('chartImportance').innerHTML = list.map((f, i) => `
            <div>
                <div class="d-flex justify-content-between ai-factor-label">
                    <span>${i + 1}. ${escapeHtml(f.feature)}</span><strong>${pct(f.weight)}</strong>
                </div>
                <div class="ai-hbar"><span style="width:${f.weight / max * 100}%"></span></div>
            </div>`).join('');
        const [a, b] = list;
        $('noteImportance').innerHTML =
            `💡 AI는 가격을 예측할 때 <strong>${escapeHtml(a.feature)}(${pct(a.weight)})</strong>${josa(a.feature, '과', '와')} ` +
            `<strong>${escapeHtml(b.feature)}(${pct(b.weight)})</strong>${josa(b.feature, '을', '를')} 가장 중요하게 봤습니다. ${FACTOR_TIP[a.feature] || ''}`;
    }

    /* ---------------------------------------------------------------
     * 4. 구별 평당가 순위 (내 지역 강조)
     * --------------------------------------------------------------- */
    function renderSgg(){
        const list = data.insights.bySgg;
        const max = list[0].medianPricePerPyeong;
        const mine = searchedSgg();
        $('chartSgg').innerHTML = list.map((s, i) => `
            <div class="ai-sgg-row ${s.sgg === mine ? 'is-mine' : ''}" title="거래 ${s.count.toLocaleString()}건 · 중위가 ${won(s.medianPrice)}">
                <span class="ai-sgg-rank">${i + 1}</span>
                <span class="ai-sgg-name">${escapeHtml(s.sgg)}</span>
                <span class="ai-hbar flex-fill"><span style="width:${s.medianPricePerPyeong / max * 100}%"></span></span>
                <span class="ai-sgg-value">${won(s.medianPricePerPyeong)}</span>
                <span class="ai-sgg-badge" title="AI 적정가보다 5% 이상 싸게 거래된 단지 비율">저평가 ${pct(s.valueRatio)}</span>
            </div>`).join('');

        const top = list[0], bottom = list[list.length - 1];
        const valueTop = [...list].sort((a, b) => b.valueRatio - a.valueRatio).slice(0, 3);
        let note = `💡 평당가가 가장 높은 곳은 <strong>${escapeHtml(top.sgg)}(${won(top.medianPricePerPyeong)})</strong>, ` +
            `가장 낮은 곳은 <strong>${escapeHtml(bottom.sgg)}(${won(bottom.medianPricePerPyeong)})</strong>로 약 ` +
            `${(top.medianPricePerPyeong / bottom.medianPricePerPyeong).toFixed(1)}배 차이가 납니다. ` +
            `AI 기준 저평가 단지 비율이 높은 구는 <strong>${valueTop.map(s => escapeHtml(s.sgg)).join(', ')}</strong>입니다.`;
        if (mine) {
            const rank = list.findIndex(s => s.sgg === mine) + 1;
            note += ` 선택하신 <strong>${escapeHtml(mine)}</strong>${josa(mine, '은', '는')} 서울 ${list.length}개 구 중 평당가 <strong>${rank}위</strong>입니다.`;
        }
        $('noteSgg').innerHTML = note;
    }

    /* ---------------------------------------------------------------
     * 5. 단지 유형 (KMeans 군집) - 내 예산으로 가능한 유형 표시
     * --------------------------------------------------------------- */
    function renderClusters(){
        const list = data.insights.clusters;
        const total = list.reduce((s, c) => s + c.count, 0);
        const budget = condition ? condition.budget : null;
        $('clusterCards').innerHTML = list.map(c => {
            const fits = budget !== null && c.medianPrice <= budget;
            return `
            <div class="col-12 col-sm-6 col-lg-3">
                <div class="ai-cluster h-100 p-3 rounded-3 ${fits ? 'is-fit' : ''}">
                    <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                        <strong class="ai-cluster-label">${escapeHtml(c.label)}</strong>
                        ${fits ? '<span class="ai-cluster-fit flex-shrink-0">내 예산 OK</span>' : ''}
                    </div>
                    <p class="ai-cluster-desc mb-2">${escapeHtml(c.desc || '')}</p>
                    <p class="ai-cluster-price fw-bold mb-1">중위 ${won(c.medianPrice)}</p>
                    <ul class="ai-cluster-meta list-unstyled mb-0">
                        <li>전용 ${c.medianArea}㎡ · 연식 ${c.medianAge}년</li>
                        <li>단지 ${c.count.toLocaleString()}개 (${pct(c.count / total)})</li>
                        <li>주요 지역: ${c.topSgg.map(escapeHtml).join(', ')}</li>
                    </ul>
                </div>
            </div>`;
        }).join('');
    }

    /* ---------------------------------------------------------------
     * 6. AI 저평가 단지 TOP 5 (검색 지역 기준)
     * --------------------------------------------------------------- */
    function renderValueTop(){
        const region = condition ? condition.region.trim() : '';
        const words = region.split(/\s+/).filter(Boolean);
        const inRegion = i => words.every(w => `${i.sido} ${i.sgg} ${i.umd}`.includes(w));
        // 할인율 40% 초과(valueScore > 1.67)는 지분·증여성 등 특수 거래일 가능성이 커서 제외
        let pool = data.items.filter(i => i.dealCount >= 2 && i.valueScore > 1 && i.valueScore <= MAX_VALUE_SCORE);
        let label = '· 서울 전체';
        if (words.length && pool.some(inRegion)) {
            pool = pool.filter(inRegion);
            label = `· ${region}`;
        }
        const top = pool.sort((a, b) => b.valueScore - a.valueScore).slice(0, 5);
        $('valueTopRegion').textContent = label;
        $('valueTop').innerHTML = top.length ? top.map((i, n) => {
            const discount = 1 - i.price / i.predictedPrice;
            return `
            <li class="ai-value-item d-flex flex-wrap align-items-center gap-2 gap-md-3 py-2">
                <span class="ai-value-rank">${n + 1}</span>
                <span class="ai-value-name flex-fill">
                    <strong>${escapeHtml(i.name)}</strong>
                    <small>${escapeHtml(`${i.sgg} ${i.umd}`)} · 전용 ${i.area}㎡ · ${i.buildYear}년 · 거래 ${i.dealCount}건</small>
                </span>
                <span class="ai-value-price text-end">
                    <strong>${won(i.price)}</strong>
                    <small>AI 적정가 ${won(i.predictedPrice)}</small>
                </span>
                <span class="ai-value-discount">${pct(discount)} 저렴</span>
            </li>`;
        }).join('') : '<li class="ai-panel-desc">조건에 맞는 저평가 단지가 없습니다.</li>';
    }

    function renderAll(){
        if (!data || !data.insights) return; // 인사이트가 없는 이전 버전 데이터는 숨김
        renderKpis();
        renderMonthly();
        renderImportance();
        renderSgg();
        renderClusters();
        renderValueTop();
        $('aiInsights').classList.remove('d-none');
    }

    document.addEventListener('ai:data', e => { data = e.detail; renderAll(); });
    document.addEventListener('ai:search', e => {
        condition = e.detail;
        if (!data || !data.insights) return;
        renderSgg();
        renderClusters();
        renderValueTop();
    });
})();
