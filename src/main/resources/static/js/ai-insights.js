/**
 * static/js/ai-insights.js
 * AI 시장 인사이트 - ml-pipeline 이 만든 insights(지역별 요약·월별 추이·시군구 시세·가격 결정 요인·단지 유형)를 시각화
 * (Vanilla JS + SVG/Flexbox, 외부 차트 라이브러리 없음)
 *
 * 범위(scope): 수도권 전체 / 서울 / 인천 / 경기 탭으로 전환
 *
 * 이벤트
 *  - ai:data   : 학습 데이터 로드 완료 (ai-recommend.js) → 전체 그리기
 *  - ai:search : 추천 조건 입력 (지역·예산) → 해당 시·도 탭 선택, 내 지역 강조, 저평가 TOP 지역 필터, 예산에 맞는 유형 표시
 */
(() => {
    let data = null;
    let condition = null;
    let scope = '수도권';
    const ALL = '수도권';
    const MAX_VALUE_SCORE = 1 / (1 - 0.4); // 적정가 대비 최대 40% 할인까지만 '저평가'로 인정
    const ALL_SGG_LIMIT = 20;              // 수도권 전체 보기에서 시·군·구 순위는 상위 20곳만

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
    const monthLabel = m => `${Number(m.slice(5))}월`;

    // 받침 유무에 따른 조사 선택: josa('전용면적', '과', '와') → '과'
    function josa(word, withBatchim, withoutBatchim){
        const ch = word.replace(/[^가-힣]/g, '').slice(-1);
        if (!ch) return withBatchim;
        return (ch.charCodeAt(0) - 0xAC00) % 28 ? withBatchim : withoutBatchim;
    }

    function median(values){
        if (!values.length) return 0;
        const s = [...values].sort((a, b) => a - b);
        const mid = Math.floor(s.length / 2);
        return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
    }

    const scopeLabel = () => (scope === ALL ? '수도권' : scope);
    const regionName = s => (scope === ALL ? `${s.sido} ${s.sgg}` : s.sgg);
    const scopeItems = () => (scope === ALL ? data.items : data.items.filter(i => i.sido === scope));

    /* ---------------------------------------------------------------
     * 검색 조건 해석: '분당구' / '경기 분당' / '인천 연수구' → 시·군·구 매칭
     * --------------------------------------------------------------- */
    function searchWords(){
        return condition ? condition.region.trim().split(/\s+/).filter(Boolean) : [];
    }

    function searchedSgg(){
        const words = searchWords();
        if (!words.length || !data) return null;
        return data.insights.bySgg.find(s => {
            const tokens = s.sgg.split(' ');
            const hitToken = w => tokens.some(t => t.startsWith(w));
            return words.some(hitToken) && words.every(w => w === s.sido || hitToken(w));
        }) || null;
    }

    function scopeFromSearch(){
        const words = searchWords();
        const sido = Object.keys(data.insights.scopes).find(k => k !== ALL && words.includes(k));
        if (sido) return sido;
        const mine = searchedSgg();
        return mine ? mine.sido : scope;
    }

    /* ---------------------------------------------------------------
     * 0. 범위 탭 (수도권 / 서울 / 인천 / 경기)
     * --------------------------------------------------------------- */
    function renderTabs(){
        $('insightScopeTabs').innerHTML = Object.keys(data.insights.scopes).map(k => `
            <button type="button" class="ai-scope-tab ${k === scope ? 'is-active' : ''}" data-scope="${escapeHtml(k)}"
                aria-pressed="${k === scope}">${k === ALL ? '수도권 전체' : escapeHtml(k)}</button>`).join('');
    }

    /* ---------------------------------------------------------------
     * 1. 핵심 지표
     * --------------------------------------------------------------- */
    function renderKpis(){
        const s = data.insights.scopes[scope];
        const kpis = [
            { icon: 'bi-receipt', label: `${scopeLabel()} 분석 실거래`, value: `${s.tradeCount.toLocaleString()}건`, sub: `단지 ${s.complexCount.toLocaleString()}개` },
            { icon: 'bi-cash-stack', label: `${scopeLabel()} 중위 거래가`, value: won(s.medianPrice), sub: `평당 ${won(s.medianPricePerPyeong)}` },
            { icon: 'bi-tags', label: '저평가 거래 단지 비율', value: pct(s.valueRatio), sub: 'AI 적정 거래가보다 5% 이상 낮게 거래' },
            { icon: 'bi-bullseye', label: 'AI 적정 거래가 예측력', value: `R² ${data.model.r2}`, sub: `평균 오차 ±${won(data.model.mae, true)}` }
        ];
        // 지표 스트립 (박스 없이 세로 구분선으로 나눔)
        $('insightKpis').innerHTML = kpis.map(k => `
            <div class="ai-stat">
                <p class="ai-stat-label mb-1"><i class="bi ${k.icon}" aria-hidden="true"></i> ${k.label}</p>
                <p class="ai-stat-value mb-0">${k.value}</p>
                <p class="ai-stat-sub mb-0">${k.sub}</p>
            </div>`).join('');
        $('insightBasis').textContent =
            `수도권 실거래 ${data.tradeCount.toLocaleString()}건 학습 · 계약일 ${data.periodFrom} ~ ${data.periodTo} · ${data.generatedAt} 기준`;
    }

    /* ---------------------------------------------------------------
     * 2. 월별 거래량(막대) + 중위 거래가(선) - SVG
     * --------------------------------------------------------------- */
    function renderMonthly(){
        const months = data.insights.scopes[scope].monthly;
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
            `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="${scopeLabel()} 월별 거래량과 중위 거래가">${bars}${line}</svg>`;

        // 해석 문장: 추세는 신고가 끝난 달까지만 비교 (집계 중인 최근 2개월 제외)
        const settled = months.length > 3 ? months.slice(0, lagFrom) : months;
        const first = settled[0], last = settled[settled.length - 1];
        const change = (last.medianPrice - first.medianPrice) / first.medianPrice;
        const busiest = months.reduce((a, b) => (b.count > a.count ? b : a));
        const trend = Math.abs(change) < 0.02 ? '큰 변동 없이 유지' : change > 0 ? `${pct(change)} 상승` : `${pct(-change)} 하락`;
        $('noteMonthly').innerHTML =
            `💡 신고가 마무리된 ${monthLabel(first.month)}~${monthLabel(last.month)} 기준 ${scopeLabel()} 아파트 중위 거래가는 ` +
            `<strong>${won(first.medianPrice, true)} → ${won(last.medianPrice, true)}</strong>으로 ${trend}했고, ` +
            `거래가 가장 활발했던 달은 <strong>${monthLabel(busiest.month)}(${busiest.count.toLocaleString()}건)</strong>입니다. ` +
            `<span class="ai-note-sub">최근 2개월은 실거래 신고 기한(30일)으로 거래량이 적게 집계될 수 있습니다.</span>`;
    }

    /* ---------------------------------------------------------------
     * 3. 가격 결정 요인 (RandomForest feature importance, 수도권 전체 모델 기준)
     * --------------------------------------------------------------- */
    const FACTOR_TIP = {
        '전용면적': '같은 지역이라도 넓을수록 가격이 크게 올라갑니다.',
        '지역(시·군·구)': '같은 면적이라도 어느 시·군·구인지에 따라 가격 차이가 큽니다.',
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
            `<strong>${escapeHtml(b.feature)}(${pct(b.weight)})</strong>${josa(b.feature, '을', '를')} 가장 중요하게 봤습니다. ${FACTOR_TIP[a.feature] || ''}` +
            `<span class="ai-note-sub">수도권 전체 거래로 학습한 모델 기준입니다.</span>`;
    }

    /* ---------------------------------------------------------------
     * 4. 시·군·구별 평당가 순위 (내 지역 강조)
     * --------------------------------------------------------------- */
    function renderSgg(){
        const full = data.insights.bySgg.filter(s => scope === ALL || s.sido === scope);
        const mine = searchedSgg();
        const isMine = s => mine && s.sido === mine.sido && s.sgg === mine.sgg;
        let list = full.map((s, i) => ({ ...s, rank: i + 1 }));
        if (scope === ALL && list.length > ALL_SGG_LIMIT) {
            const extra = list.find(s => isMine(s) && s.rank > ALL_SGG_LIMIT); // 내 지역은 순위 밖이어도 표시
            list = list.slice(0, ALL_SGG_LIMIT).concat(extra ? [extra] : []);
        }
        const max = full[0].medianPricePerPyeong;
        $('sggScopeLabel').textContent = scope === ALL && full.length > ALL_SGG_LIMIT
            ? `· 수도권 상위 ${ALL_SGG_LIMIT}곳 (전체 ${full.length}곳)` : `· ${scopeLabel()} ${full.length}곳`;

        const grid = $('chartSgg');
        grid.style.setProperty('--sgg-rows', Math.ceil(list.length / 2));
        grid.innerHTML = list.map(s => `
            <div class="ai-sgg-row ${isMine(s) ? 'is-mine' : ''}" title="${escapeHtml(`${s.sido} ${s.sgg}`)} · 거래 ${s.count.toLocaleString()}건 · 중위가 ${won(s.medianPrice)}">
                <span class="ai-sgg-rank">${s.rank}</span>
                <span class="ai-sgg-name">${scope === ALL ? `<small>${escapeHtml(s.sido)}</small> ` : ''}${escapeHtml(s.sgg)}</span>
                <span class="ai-hbar flex-fill"><span style="width:${s.medianPricePerPyeong / max * 100}%"></span></span>
                <span class="ai-sgg-value">${won(s.medianPricePerPyeong)}</span>
                <span class="ai-sgg-badge" title="AI 적정 거래가보다 5% 이상 낮게 거래된 단지 비율">저평가 ${pct(s.valueRatio)}</span>
            </div>`).join('');

        const top = full[0], bottom = full[full.length - 1];
        const valueTop = [...full].sort((a, b) => b.valueRatio - a.valueRatio).slice(0, 3);
        let note = `💡 ${scopeLabel()}에서 평당가가 가장 높은 곳은 <strong>${escapeHtml(regionName(top))}(${won(top.medianPricePerPyeong)})</strong>, ` +
            `가장 낮은 곳은 <strong>${escapeHtml(regionName(bottom))}(${won(bottom.medianPricePerPyeong)})</strong>로 약 ` +
            `${(top.medianPricePerPyeong / bottom.medianPricePerPyeong).toFixed(1)}배 차이가 납니다. ` +
            `AI 기준 저평가 단지 비율이 높은 곳은 <strong>${valueTop.map(s => escapeHtml(regionName(s))).join(', ')}</strong>입니다.`;
        const mineRow = full.find(isMine);
        if (mineRow) {
            const rank = full.indexOf(mineRow) + 1;
            note += ` 선택하신 <strong>${escapeHtml(mineRow.sgg)}</strong>${josa(mineRow.sgg, '은', '는')} ` +
                `${scopeLabel()} ${full.length}개 시·군·구 중 평당가 <strong>${rank}위</strong>입니다.`;
        }
        $('noteSgg').innerHTML = note;
    }

    /* ---------------------------------------------------------------
     * 5. 단지 유형 (KMeans 군집) - 선택 범위의 단지로 다시 집계, 내 예산으로 가능한 유형 표시
     * --------------------------------------------------------------- */
    function renderClusters(){
        const info = Object.fromEntries(data.insights.clusters.map(c => [c.id, c]));
        const items = scopeItems();
        const thisYear = new Date().getFullYear();
        const groups = {};
        items.forEach(i => (groups[i.cluster] = groups[i.cluster] || []).push(i));

        const list = Object.entries(groups).map(([id, g]) => {
            const counts = {};
            g.forEach(i => { const r = regionName(i); counts[r] = (counts[r] || 0) + 1; });
            return {
                ...info[id], count: g.length,
                medianPrice: Math.round(median(g.map(i => i.price))),
                medianArea: median(g.map(i => i.area)).toFixed(1),
                medianAge: Math.round(median(g.map(i => thisYear - i.buildYear))),
                topSgg: Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([r]) => r)
            };
        }).sort((a, b) => b.medianPrice - a.medianPrice);

        // 유형 리스트 (행): 이름·설명 | 중위가 | 면적·연식 | 비중 막대 | 주요 지역
        const budget = condition ? condition.budget : null;
        const maxShare = Math.max(...list.map(c => c.count));
        $('clusterCards').innerHTML = list.map(c => {
            const fits = budget !== null && c.medianPrice <= budget;
            return `
            <li class="ai-type-row ${fits ? 'is-fit' : ''}">
                <div class="ai-type-main">
                    <p class="mb-0"><strong>${escapeHtml(c.label)}</strong>${fits ? '<span class="ai-tag is-primary">내 예산 OK</span>' : ''}</p>
                    <p class="ai-type-desc mb-0">${escapeHtml(c.desc || '')}</p>
                </div>
                <div class="ai-type-price"><span class="ai-rank-label">중위 거래가</span><strong>${won(c.medianPrice)}</strong></div>
                <div class="ai-type-spec"><span class="ai-rank-label">전용 · 연식</span>${c.medianArea}㎡ · ${c.medianAge}년</div>
                <div class="ai-type-share">
                    <span class="ai-rank-label">단지 비중 ${pct(c.count / items.length)}</span>
                    <span class="ai-meter"><span style="width:${c.count / maxShare * 100}%"></span></span>
                    <small>${c.count.toLocaleString()}개 · ${c.topSgg.map(escapeHtml).join(', ')}</small>
                </div>
            </li>`;
        }).join('');
    }

    /* ---------------------------------------------------------------
     * 6. AI 저평가 단지 TOP 5 (선택 범위 + 검색 지역 기준)
     * --------------------------------------------------------------- */
    function renderValueTop(){
        const region = condition ? condition.region.trim() : '';
        const words = searchWords();
        const inRegion = i => words.every(w => `${i.sido} ${i.sgg} ${i.umd}`.includes(w));
        // 할인율 40% 초과(valueScore > 1.67)는 지분·증여성 등 특수 거래일 가능성이 커서 제외
        let pool = scopeItems().filter(i => i.dealCount >= 2 && i.valueScore > 1 && i.valueScore <= MAX_VALUE_SCORE);
        let label = `· ${scope === ALL ? '수도권 전체' : scope}`;
        if (words.length && pool.some(inRegion)) {
            pool = pool.filter(inRegion);
            label = `· ${region}`;
        }
        const top = pool.sort((a, b) => b.valueScore - a.valueScore).slice(0, 5);
        $('valueTopRegion').textContent = label;
        $('valueTop').innerHTML = top.length ? top.map((i, n) => {
            const discount = 1 - i.price / i.predictedPrice;
            return `
            <li class="ai-rank-row">
                <span class="ai-rank-no">${String(n + 1).padStart(2, '0')}</span>
                <div class="ai-rank-main">
                    <p class="ai-rank-name mb-0"><strong>${escapeHtml(i.name)}</strong></p>
                    <p class="ai-rank-meta mb-0">
                        <i class="bi bi-geo-alt" aria-hidden="true"></i> ${escapeHtml(`${i.sido} ${i.sgg} ${i.umd}`)}
                        <span>전용 ${i.area}㎡</span><span>${i.buildYear}년</span><span>거래 ${i.dealCount}건</span>
                    </p>
                </div>
                <div class="ai-rank-price">
                    <span class="ai-rank-label">실거래 중위가</span>
                    <strong>${won(i.price)}</strong>
                    <small>AI 적정 거래가 ${won(i.predictedPrice)}</small>
                </div>
                <div class="ai-rank-score">
                    <span class="ai-rank-label">적정가 대비</span>
                    <strong class="is-accent">-${pct(discount)}</strong>
                    <span class="ai-meter is-accent"><span style="width:${Math.min(discount / 0.4, 1) * 100}%"></span></span>
                </div>
            </li>`;
        }).join('') : '<li class="ai-sec-desc">조건에 맞는 저평가 거래 단지가 없습니다.</li>';
    }

    function renderScope(){
        renderTabs();
        renderKpis();
        renderMonthly();
        renderSgg();
        renderClusters();
        renderValueTop();
    }

    function renderAll(){
        // 인사이트가 없거나 지역 범위(scopes)가 없는 이전 버전 데이터는 숨김
        if (!data || !data.insights || !data.insights.scopes) return;
        if (!data.insights.scopes[scope]) scope = ALL;
        renderScope();
        renderImportance();
        $('aiInsights').classList.remove('d-none');
    }

    document.addEventListener('click', e => {
        const tab = e.target.closest('.ai-scope-tab');
        if (!tab || !data) return;
        scope = tab.dataset.scope;
        renderScope();
    });

    document.addEventListener('ai:data', e => { data = e.detail; renderAll(); });
    document.addEventListener('ai:search', e => {
        condition = e.detail;
        if (!data || !data.insights || !data.insights.scopes) return;
        scope = scopeFromSearch();
        renderScope();
    });
})();
