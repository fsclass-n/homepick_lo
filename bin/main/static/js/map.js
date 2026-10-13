/**
 * static/js/map.js
 * 지도 중심 실거래 조회 - AI 학습 데이터(/api/ai-data/apartments)의 단지를 카카오맵에 표시
 *
 * - 같은 좌표·단지명의 평형(방 수)들을 하나의 단지 마커로 묶고, 마커가 많으면 클러스터로 묶어 표시
 * - 목록은 현재 지도에 보이는 영역의 단지만, 필터(지역·예산·방 수·단지명·저평가)와 정렬 적용
 * - AI 저평가: RandomForest 적정가 ÷ 실제 거래가(valueScore) ≥ 1.05 (적정가보다 5% 이상 싸게 거래)
 *   단, 할인율 40% 초과(valueScore > 1.67)는 지분·증여성 등 특수 거래 가능성이 커서 제외 (시장 리포트와 같은 기준)
 */
document.addEventListener('DOMContentLoaded', () => {
    const DATA_URL = '/api/ai-data/apartments';
    const VALUE_MIN = 1.05;
    const VALUE_MAX = 1 / (1 - 0.4);
    const isValue = v => v >= VALUE_MIN && v <= VALUE_MAX;
    const valueRank = v => (v <= VALUE_MAX ? v : 0); // 특수 거래 의심은 저평가순 맨 뒤로
    const LIST_LIMIT = 80;
    const SIDO_ORDER = ['서울', '인천', '경기'];

    const $ = id => document.getElementById(id);
    const f = { sido: $('fSido'), sgg: $('fSgg'), budget: $('fBudget'), rooms: $('fRooms'),
                name: $('fName'), value: $('fValue'), sort: $('fSort') };
    const listEl = $('mapList');
    const countEl = $('mapListCount');
    const notice = $('mapNotice');

    let complexes = [];   // { key, name, sido, sgg, umd, jibun, lat, lng, items[], marker }
    let filtered = [];
    let map = null, clusterer = null, overlay = null, activeKey = null;

    /* ---------- 표시 형식 ---------- */
    const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

    function won(manwon){ // 125000 → 12억 5,000만
        const eok = Math.floor(manwon / 10000), rest = manwon % 10000;
        if (!eok) return `${rest.toLocaleString()}만`;
        return rest ? `${eok}억 ${rest.toLocaleString()}만` : `${eok}억`;
    }
    const pyeong = area => Math.round(area / 3.3058);
    const discount = v => Math.round((1 - 1 / v) * 100); // 실제가가 적정가보다 몇 % 싼지

    function showNotice(html){
        notice.innerHTML = html;
        notice.classList.remove('d-none');
    }

    /* ---------- 데이터 → 단지 묶음 ---------- */
    function buildComplexes(items){
        const byKey = new Map();
        items.forEach(it => {
            if (typeof it.lat !== 'number') return;
            const key = `${it.lat},${it.lng}|${it.name}`;
            if (!byKey.has(key)) {
                byKey.set(key, { key, name: it.name, sido: it.sido, sgg: it.sgg, umd: it.umd,
                                 jibun: it.jibun || '', lat: it.lat, lng: it.lng, items: [] });
            }
            byKey.get(key).items.push(it);
        });
        byKey.forEach(c => c.items.sort((a, b) => a.area - b.area));
        return [...byKey.values()];
    }

    function fillSidoOptions(){
        SIDO_ORDER.filter(s => complexes.some(c => c.sido === s))
            .forEach(s => f.sido.add(new Option(s, s)));
    }

    function fillSggOptions(){
        const sido = f.sido.value;
        f.sgg.length = 1;
        if (!sido) return;
        [...new Set(complexes.filter(c => c.sido === sido).map(c => c.sgg))].sort((a, b) => a.localeCompare(b, 'ko'))
            .forEach(s => f.sgg.add(new Option(s, s)));
    }

    /* ---------- 필터: 조건에 맞는 평형만 남기고, 하나라도 맞으면 단지 표시 ---------- */
    function matchItems(c){
        const budget = Number(f.budget.value) || 0;
        const rooms = Number(f.rooms.value) || 0;
        return c.items.filter(it =>
            (!budget || it.price <= budget) &&
            (!rooms || it.rooms === rooms) &&
            (!f.value.checked || isValue(it.valueScore)));
    }

    function applyFilter(fitBounds = false){
        const q = f.name.value.trim().replace(/\s+/g, '');
        filtered = [];
        complexes.forEach(c => {
            if (f.sido.value && c.sido !== f.sido.value) return;
            if (f.sgg.value && c.sgg !== f.sgg.value) return;
            if (q && !(c.name + c.umd).replace(/\s+/g, '').includes(q)) return;
            const shown = matchItems(c);
            if (!shown.length) return;
            c.shown = shown;
            c.best = shown.reduce((a, b) => (valueRank(b.valueScore) > valueRank(a.valueScore) ? b : a));
            c.minPrice = Math.min(...shown.map(it => it.price));
            c.recent = shown.reduce((a, b) => (b.lastDealDate > a.lastDealDate ? b : a)).lastDealDate;
            filtered.push(c);
        });

        if (map) {
            if (overlay && !filtered.some(c => c.key === activeKey)) closeOverlay();
            clusterer.clear();
            clusterer.addMarkers(filtered.map(c => markerOf(c)));
            if (fitBounds && filtered.length) {
                const bounds = new kakao.maps.LatLngBounds();
                filtered.forEach(c => bounds.extend(new kakao.maps.LatLng(c.lat, c.lng)));
                map.setBounds(bounds, 40, 40, 40, 40);
            }
        }
        renderList();
    }

    /* ---------- 목록: 지도에 보이는 영역의 단지 ---------- */
    function sortList(arr){
        const by = {
            value: (a, b) => valueRank(b.best.valueScore) - valueRank(a.best.valueScore),
            priceAsc: (a, b) => a.minPrice - b.minPrice,
            priceDesc: (a, b) => b.minPrice - a.minPrice,
            recent: (a, b) => b.recent.localeCompare(a.recent),
        }[f.sort.value];
        return arr.sort(by);
    }

    function renderList(){
        let visible = filtered;
        if (map) {
            const bounds = map.getBounds();
            visible = filtered.filter(c => bounds.contain(new kakao.maps.LatLng(c.lat, c.lng)));
        }
        sortList(visible);
        countEl.textContent = map
            ? `지도 영역 ${visible.length.toLocaleString()} / 조건 일치 ${filtered.length.toLocaleString()}개 단지`
            : `조건 일치 ${visible.length.toLocaleString()}개 단지`;

        if (!visible.length) {
            const msg = !complexes.length ? '단지 위치(좌표) 데이터를 준비 중입니다.'
                : filtered.length ? '지도를 움직이거나 확대·축소해 보세요.' : '조건에 맞는 실거래 단지가 없습니다.';
            listEl.innerHTML = `<li class="map-empty"><i class="bi bi-search d-block fs-3 mb-2"></i>${msg}</li>`;
            return;
        }
        const rows = visible.slice(0, LIST_LIMIT).map(c => {
            const value = isValue(c.best.valueScore);
            const rooms = [...new Set(c.shown.map(it => it.rooms))].map(r => (r >= 4 ? '4방+' : `${r}방`)).join('·');
            return `<li><button type="button" class="map-item${c.key === activeKey ? ' is-active' : ''}" data-key="${esc(c.key)}">
                <div class="d-flex justify-content-between align-items-start gap-2">
                    <div class="min-w-0">
                        <div class="map-item-name text-truncate">${esc(c.name)}</div>
                        <div class="map-item-addr text-truncate">${esc(c.sido)} ${esc(c.sgg)} ${esc(c.umd)}</div>
                    </div>
                    <div class="map-item-price">${won(c.minPrice)}~</div>
                </div>
                <div class="d-flex align-items-center gap-1 flex-wrap mt-1 map-item-meta">
                    ${value ? `<span class="map-tag map-tag-value">AI 저평가 ${discount(c.best.valueScore)}%↓</span>` : ''}
                    <span class="map-tag">${rooms}</span>
                    <span class="map-tag">${c.items[0].buildYear}년식</span>
                    <span class="ms-auto">최근 거래 ${esc(c.recent)}</span>
                </div>
            </button></li>`;
        });
        if (visible.length > LIST_LIMIT) {
            rows.push(`<li class="map-empty py-3">상위 ${LIST_LIMIT}개만 표시합니다. 지도를 확대하면 더 자세히 볼 수 있어요.</li>`);
        }
        listEl.innerHTML = rows.join('');
    }

    listEl.addEventListener('click', e => {
        const btn = e.target.closest('.map-item');
        if (!btn) return;
        const c = complexes.find(x => x.key === btn.dataset.key);
        if (!c || !map) return;
        if (map.getLevel() > 4) map.setLevel(4);
        map.panTo(new kakao.maps.LatLng(c.lat, c.lng));
        openOverlay(c);
    });

    /* ---------- 카카오맵: 마커·클러스터·팝업 ---------- */
    function markerImage(color){
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="28" height="36" viewBox="0 0 28 36">
            <path d="M14 0C6.3 0 0 6.2 0 13.9 0 24.3 14 36 14 36s14-11.7 14-22.1C28 6.2 21.7 0 14 0z" fill="${color}"/>
            <circle cx="14" cy="14" r="5.5" fill="#fff"/></svg>`;
        return new kakao.maps.MarkerImage('data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg),
            new kakao.maps.Size(28, 36), { offset: new kakao.maps.Point(14, 36) });
    }
    let IMG_NORMAL, IMG_VALUE;

    function markerOf(c){
        const value = isValue(c.best.valueScore);
        if (!c.marker) {
            c.marker = new kakao.maps.Marker({ position: new kakao.maps.LatLng(c.lat, c.lng), title: c.name });
            kakao.maps.event.addListener(c.marker, 'click', () => openOverlay(c));
        }
        c.marker.setImage(value ? IMG_VALUE : IMG_NORMAL);
        c.marker.setZIndex(value ? 2 : 1);
        return c.marker;
    }

    function popupHtml(c){
        const rows = c.shown.map(it => {
            const value = isValue(it.valueScore);
            return `<tr>
                <td>${it.area}㎡ <span class="text-secondary">(${pyeong(it.area)}평)</span></td>
                <td class="num"><strong>${won(it.lastPrice)}</strong></td>
                <td class="num">${won(it.predictedPrice)}</td>
                <td class="num">${value ? `<span class="map-tag map-tag-value">${discount(it.valueScore)}%↓</span>` : '-'}</td>
            </tr>`;
        }).join('');
        const deals = c.shown.reduce((s, it) => s + it.dealCount, 0);
        return `<div class="map-pop">
            <button type="button" class="map-pop-close" aria-label="닫기">&times;</button>
            <div class="map-pop-name">${esc(c.name)}</div>
            <div class="map-pop-addr">${esc(c.sido)} ${esc(c.sgg)} ${esc(c.umd)} ${esc(c.jibun)} · ${c.items[0].buildYear}년식</div>
            <table>
                <thead><tr><th>전용면적</th><th class="num">최근 거래가</th><th class="num">AI 적정가</th><th class="num">저평가</th></tr></thead>
                <tbody>${rows}</tbody>
            </table>
            <div class="map-pop-foot">최근 6개월 거래 ${deals}건 · 최근 거래 ${esc(c.recent)} · 거래 완료 기록</div>
        </div>`;
    }

    function openOverlay(c){
        closeOverlay();
        const el = document.createElement('div');
        el.innerHTML = popupHtml(c);
        el.querySelector('.map-pop-close').addEventListener('click', closeOverlay);
        overlay = new kakao.maps.CustomOverlay({
            position: new kakao.maps.LatLng(c.lat, c.lng), content: el.firstElementChild,
            yAnchor: 1, xAnchor: 0.5, zIndex: 10, clickable: true,
        });
        overlay.setMap(map);
        activeKey = c.key;
        listEl.querySelectorAll('.map-item').forEach(b => b.classList.toggle('is-active', b.dataset.key === c.key));
    }

    function closeOverlay(){
        if (overlay) overlay.setMap(null);
        overlay = null;
        activeKey = null;
        listEl.querySelectorAll('.map-item.is-active').forEach(b => b.classList.remove('is-active'));
    }

    function initMap(){
        IMG_NORMAL = markerImage('#006AC9');
        IMG_VALUE = markerImage('#FF600D');
        map = new kakao.maps.Map($('map'), { center: new kakao.maps.LatLng(37.5665, 126.978), level: 8 });
        map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
        map.addControl(new kakao.maps.MapTypeControl(), kakao.maps.ControlPosition.TOPRIGHT);
        clusterer = new kakao.maps.MarkerClusterer({
            map, averageCenter: true, minLevel: 6, gridSize: 70,
            calculator: [20, 100, 500],
            styles: [30, 38, 46, 54].map(size => ({
                width: `${size}px`, height: `${size}px`, lineHeight: `${size}px`,
                background: 'rgba(0, 106, 201, 0.85)', color: '#fff', textAlign: 'center',
                fontWeight: '700', fontSize: '13px', borderRadius: '50%',
                border: '3px solid rgba(255, 255, 255, 0.75)', boxShadow: '0 2px 6px rgba(0,0,0,.25)',
            })),
        });
        kakao.maps.event.addListener(map, 'idle', renderList);
    }

    /* ---------- 이벤트 ---------- */
    let timer = null;
    const debounced = () => { clearTimeout(timer); timer = setTimeout(() => applyFilter(false), 250); };
    f.sido.addEventListener('change', () => { fillSggOptions(); applyFilter(true); });
    f.sgg.addEventListener('change', () => applyFilter(true));
    [f.rooms, f.value].forEach(el => el.addEventListener('change', () => applyFilter(false)));
    [f.budget, f.name].forEach(el => el.addEventListener('input', debounced));
    f.sort.addEventListener('change', renderList);

    /* ---------- 시작 ---------- */
    const hasSdk = !!(window.kakao && kakao.maps && kakao.maps.load);
    if (!hasSdk) {
        showNotice('<div><i class="bi bi-map d-block fs-1 mb-2"></i><strong>카카오맵 키가 설정되지 않았습니다.</strong><br>'
            + '서버 환경변수 KAKAO_JS_KEY 를 설정하면 지도가 표시됩니다. 왼쪽 목록은 그대로 이용할 수 있어요.</div>');
    }

    fetch(DATA_URL)
        .then(res => { if (!res.ok) throw new Error(); return res.json(); })
        .then(data => {
            complexes = buildComplexes(data.items || []);
            $('mapDataInfo').textContent = `국토교통부 실거래가 ${data.periodFrom} ~ ${data.periodTo} · AI 학습 ${data.generatedAt} 기준 · 지도 표시 단지 ${complexes.length.toLocaleString()}개`;
            if (!complexes.length) {
                showNotice('<div><i class="bi bi-hourglass-split d-block fs-1 mb-2"></i><strong>단지 위치(좌표) 데이터를 준비 중입니다.</strong><br>'
                    + 'AI 재학습 때 단지 좌표 변환이 완료되면 지도에 표시됩니다.</div>');
            }
            fillSidoOptions();
            if (hasSdk) kakao.maps.load(() => { initMap(); applyFilter(false); });
            else applyFilter(false);
        })
        .catch(() => {
            $('mapDataInfo').textContent = '실거래 데이터를 불러오지 못했습니다.';
            listEl.innerHTML = '<li class="map-empty">잠시 후 다시 시도해 주세요.</li>';
        });
});
