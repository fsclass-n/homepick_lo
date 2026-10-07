"""4단계: 머신러닝(scikit-learn) → src/main/resources/static/data/apartments.json

- RandomForestRegressor: 단지 특성으로 '적정 거래가'를 예측 → 실제가 대비 저평가 점수(valueScore)
- KMeans: 비슷한 단지끼리 군집화(cluster) → 추천 시 비슷한 대안 단지 묶음에 활용
- StandardScaler 기준값을 JSON 에 함께 저장 → 브라우저에서 같은 기준으로 유사도 계산
- insights: 월별 추이, 구별 시세, 가격 결정 요인(feature importance), 단지 유형(군집) 요약 → 화면 시각화
- insights.charts: Plotly(Python)로 만든 그래프 JSON → 화면에서 plotly.js 로 그대로 표시
"""
import json
from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd
import plotly.graph_objects as go
import plotly.io as pio
from sklearn.cluster import KMeans
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from common import OUTPUT_JSON, PROCESSED_CSV, SIDO_ORDER

NUM_FEATURES = ["area", "floor", "age"]
KST = timezone(timedelta(hours=9))  # GitHub Actions(UTC)에서 실행해도 한국 시간으로 기록


def build_complex_table(df: pd.DataFrame) -> pd.DataFrame:
    """거래 단위 → 단지·평형 단위로 집계 (최근 거래 위주)"""
    df = df.sort_values("dealDate")
    keys = ["sido", "sgg", "umdNm", "aptNm", "rooms", "buildYear"]
    g = df.groupby(keys)
    table = g.agg(
        area=("area", "median"),
        floor=("floor", "median"),
        age=("age", "first"),
        price=("price", "median"),
        lastPrice=("price", "last"),
        lastDealDate=("dealDate", "last"),
        dealCount=("price", "size"),
    ).reset_index()
    return table


def cluster_label(row, overall) -> tuple[str, str]:
    """KMeans 군집의 중심값 → 사람이 읽기 쉬운 단지 유형 이름과 한 줄 설명"""
    age = "신축" if row.age <= 10 else "준신축" if row.age <= 20 else "구축" if row.age <= 30 else "노후"
    size = "대형" if row.area >= 102 else "중형" if row.area >= 80 else "중소형" if row.area >= 60 else "소형"
    tier = "고가" if row.price >= overall * 1.5 else "실속" if row.price <= overall * 0.7 else "중간가"

    if age == "노후" and tier == "고가":
        desc = "연식은 오래됐지만 가격이 높은 단지 — 재건축 기대감이 반영된 유형"
    elif age in ("신축", "준신축") and tier == "고가":
        desc = "새 아파트 프리미엄이 붙은 인기 단지 유형"
    elif tier == "실속":
        desc = "예산 부담이 적어 첫 내 집 마련·1~2인 가구에 적합한 유형"
    elif size == "대형":
        desc = "넓은 평형을 선호하는 다인 가구에 적합한 유형"
    else:
        desc = "가격·면적이 무난해 실거주 수요가 가장 많은 유형"
    return f"{age}·{size}·{tier}", desc


def scope_summary(df, table) -> dict:
    """한 지역 범위(수도권 전체 또는 시·도)의 요약 + 월별 거래량·중위가"""
    month = df.assign(month=df["dealDate"].dt.strftime("%Y-%m")).groupby("month")
    return {
        "tradeCount": int(len(df)),
        "complexCount": int(len(table)),
        "medianPrice": int(df["price"].median()),
        "medianPricePerPyeong": int(df["pricePerPyeong"].median()),
        "valueRatio": round(float((table["valueScore"] >= 1.05).mean()), 3),
        "monthly": [{
            "month": m, "count": int(len(g)), "medianPrice": int(g["price"].median()),
            "medianPricePerPyeong": int(g["pricePerPyeong"].median()),
        } for m, g in month],
    }


def build_insights(df, table, model) -> dict:
    """화면의 'AI 시장 인사이트'용 요약 (지역 범위별 요약·월별 추이, 시·군·구 시세, 가격 결정 요인, 단지 유형)"""
    # 1) 지역 범위별 요약: 수도권 전체 + 시·도
    scopes = {"수도권": scope_summary(df, table)}
    for sido in SIDO_ORDER:
        if (df["sido"] == sido).any():
            scopes[sido] = scope_summary(df[df["sido"] == sido], table[table["sido"] == sido])

    # 2) 시·군·구별 평당가·거래량·저평가 단지 비율
    value_ratio = table.groupby("region")["valueScore"].apply(lambda s: (s >= 1.05).mean())
    by_sgg = df.groupby(["region", "sido", "sgg"]).agg(count=("price", "size"),
                                                       medianPrice=("price", "median"),
                                                       medianPricePerPyeong=("pricePerPyeong", "median"))
    by_sgg = by_sgg.reset_index()
    by_sgg["valueRatio"] = by_sgg["region"].map(value_ratio).fillna(0)
    by_sgg = by_sgg.sort_values("medianPricePerPyeong", ascending=False)
    sgg_list = [{
        "sido": r.sido, "sgg": r.sgg, "count": int(r.count), "medianPrice": int(r.medianPrice),
        "medianPricePerPyeong": int(r.medianPricePerPyeong), "valueRatio": round(float(r.valueRatio), 3),
    } for r in by_sgg.itertuples(index=False)]

    # 3) RandomForest 가격 결정 요인 (지역 One-Hot 은 합산)
    imp = model.feature_importances_
    n = len(NUM_FEATURES)
    factors = {"전용면적": imp[0], "층": imp[1], "건물 연식": imp[2], "지역(시·군·구)": imp[n:].sum()}
    total = sum(factors.values())
    importance = sorted(({"feature": k, "weight": round(float(v / total), 3)} for k, v in factors.items()),
                        key=lambda f: -f["weight"])

    # 4) KMeans 단지 유형
    overall = table["price"].median()
    clusters = []
    for cid, g in table.groupby("cluster"):
        center = g[["price", "area", "age"]].median()
        label, desc = cluster_label(center, overall)
        clusters.append({
            "id": int(cid), "label": label, "desc": desc, "count": int(len(g)),
            "medianPrice": int(center.price), "medianArea": round(float(center.area), 1),
            "medianAge": int(center.age), "topSgg": g["region"].value_counts().head(3).index.tolist(),
        })
    clusters.sort(key=lambda c: -c["medianPrice"])

    return {"scopes": scopes, "bySgg": sgg_list, "importance": importance, "clusters": clusters}


# ---------------------------------------------------------------------------
# Plotly 그래프 (Python 에서 만들고 JSON 으로 저장 → 화면에서 plotly.js 로 표시)
# ---------------------------------------------------------------------------
PRIMARY, PRIMARY_LIGHT, ACCENT, TEXT, MUTED, GRID = "#006AC9", "#9cc5ec", "#FF600D", "#333333", "#888888", "#E0E4E8"
FONT = "NanumSquare, 'Noto Sans KR', sans-serif"
SGG_TOP_ALL = 20  # 수도권 전체 보기의 시·군·구 순위 개수


def base_layout(**kwargs) -> dict:
    layout = dict(
        font=dict(family=FONT, size=12, color=TEXT),
        paper_bgcolor="rgba(0,0,0,0)", plot_bgcolor="rgba(0,0,0,0)",
        margin=dict(l=8, r=8, t=8, b=8), hoverlabel=dict(font=dict(family=FONT)),
        showlegend=False,
    )
    layout.update(kwargs)
    return layout


def fig_json(fig: go.Figure) -> dict:
    return json.loads(pio.to_json(fig, validate=True, remove_uids=True))


def monthly_figure(monthly: list) -> dict:
    """월별 거래량(막대) + 중위 거래가(선, 보조축). 최근 2개월은 신고 집계 중이라 연하게 표시"""
    months = [f"{int(m['month'][5:])}월" for m in monthly]
    lag_from = len(monthly) - 2
    fig = go.Figure()
    fig.add_bar(
        x=months, y=[m["count"] for m in monthly], name="거래량",
        marker=dict(color=[PRIMARY_LIGHT if i < lag_from else "rgba(156,197,236,0.35)" for i in range(len(monthly))],
                    line=dict(color=[PRIMARY_LIGHT if i < lag_from else PRIMARY for i in range(len(monthly))], width=1)),
        text=[f"{m['count']:,}" for m in monthly], textposition="inside", insidetextanchor="start",
        textfont=dict(color="#0d3b73", size=11),
        customdata=["신고 집계 중" if i >= lag_from else "신고 완료" for i in range(len(monthly))],
        hovertemplate="%{x} 거래 %{y:,}건 (%{customdata})<extra></extra>",
    )
    fig.add_scatter(
        x=months, y=[round(m["medianPrice"] / 10000, 2) for m in monthly], name="중위 거래가", yaxis="y2",
        mode="lines+markers+text", line=dict(color=ACCENT, width=3),
        marker=dict(size=9, color="white", line=dict(color=ACCENT, width=2)),
        text=[f"{m['medianPrice'] / 10000:.1f}억" for m in monthly], textposition="top center",
        textfont=dict(color=ACCENT, size=11), hovertemplate="%{x} 중위 거래가 %{y:.2f}억<extra></extra>",
    )
    prices = [m["medianPrice"] / 10000 for m in monthly]
    fig.update_layout(base_layout(
        height=300, showlegend=True, bargap=0.35,
        legend=dict(orientation="h", y=-0.12, x=0, font=dict(size=11, color=MUTED)),
        xaxis=dict(showgrid=False, tickfont=dict(color=MUTED)),
        yaxis=dict(title=dict(text="거래량(건)", font=dict(size=11, color=MUTED)), gridcolor=GRID, zeroline=False,
                   tickfont=dict(color=MUTED), rangemode="tozero"),
        yaxis2=dict(title=dict(text="중위 거래가", font=dict(size=11, color=MUTED)), overlaying="y", side="right",
                    showgrid=False, tickfont=dict(color=MUTED), tickformat=".1f", ticksuffix="억", nticks=5,
                    range=[min(prices) * 0.85, max(prices) * 1.12]),
        margin=dict(l=8, r=8, t=12, b=8),
    ))
    return fig_json(fig)


def importance_figure(importance: list) -> dict:
    """RandomForest 가격 결정 요인 (가로 막대)"""
    items = list(reversed(importance))
    fig = go.Figure(go.Bar(
        x=[f["weight"] * 100 for f in items], y=[f["feature"] for f in items], orientation="h",
        marker=dict(color=[PRIMARY if f is importance[0] else PRIMARY_LIGHT for f in items]),
        text=[f"{f['weight'] * 100:.0f}%" for f in items], textposition="outside", cliponaxis=False,
        textfont=dict(size=12, color=TEXT), hovertemplate="%{y}: %{x:.1f}%<extra></extra>",
    ))
    fig.update_layout(base_layout(
        height=240, bargap=0.45,
        xaxis=dict(visible=False, range=[0, max(f["weight"] for f in importance) * 118]),
        yaxis=dict(tickfont=dict(size=13, color=TEXT), automargin=True),
        margin=dict(l=8, r=36, t=4, b=4),
    ))
    return fig_json(fig)


def sgg_figure(rows: list, show_sido: bool) -> dict:
    """시·군·구별 평당 거래가 순위 (가로 막대, 위에서부터 1위). 저평가 비율은 마우스 오버로 표시"""
    rows = list(reversed(rows))
    labels = [f"{r['rank']}. {r['sido'] + ' ' if show_sido else ''}{r['sgg']}" for r in rows]
    fig = go.Figure(go.Bar(
        x=[r["medianPricePerPyeong"] for r in rows], y=labels, orientation="h",
        marker=dict(color=PRIMARY, opacity=0.85),
        text=[f"{r['medianPricePerPyeong']:,}만" for r in rows], textposition="outside", cliponaxis=False,
        textfont=dict(size=11, color=TEXT),
        customdata=[[r["sido"], r["sgg"], r["count"], round(r["valueRatio"] * 100), r["medianPrice"]] for r in rows],
        hovertemplate=("%{customdata[0]} %{customdata[1]}<br>평당 %{x:,}만원 · 중위가 %{customdata[4]:,}만원"
                       "<br>거래 %{customdata[2]:,}건 · 저평가 거래 %{customdata[3]}%<extra></extra>"),
    ))
    fig.update_layout(base_layout(
        height=max(260, 24 * len(rows) + 30), bargap=0.3,
        xaxis=dict(visible=False, range=[0, max(r["medianPricePerPyeong"] for r in rows) * 1.15]),
        yaxis=dict(tickfont=dict(size=12, color=TEXT), automargin=True),
        margin=dict(l=8, r=48, t=4, b=4),
    ))
    return fig_json(fig)


def build_charts(insights: dict) -> dict:
    """범위(수도권/서울/인천/경기)별 Plotly 그래프 JSON"""
    charts = {"importance": importance_figure(insights["importance"]), "monthly": {}, "sgg": {}}
    for scope, summary in insights["scopes"].items():
        charts["monthly"][scope] = monthly_figure(summary["monthly"])
        rows = [r for r in insights["bySgg"] if scope == "수도권" or r["sido"] == scope]
        rows = [{**r, "rank": i + 1} for i, r in enumerate(rows)]
        if scope == "수도권":
            rows = rows[:SGG_TOP_ALL]
        charts["sgg"][scope] = sgg_figure(rows, show_sido=(scope == "수도권"))
    return charts


def main():
    df = pd.read_csv(PROCESSED_CSV, parse_dates=["dealDate"])
    # 시·도 + 시·군·구 를 지역 키로 사용 (다른 시·도의 같은 이름 구가 섞이지 않도록)
    df["region"] = df["sido"] + " " + df["sgg"]
    table = build_complex_table(df)
    table["region"] = table["sido"] + " " + table["sgg"]

    # 1) 적정가 예측 모델 (거래 단위로 학습)
    encoder = OneHotEncoder(handle_unknown="ignore", sparse_output=False)
    x_cat = encoder.fit_transform(df[["region"]])
    x = np.hstack([df[NUM_FEATURES].to_numpy(), x_cat])
    y = np.log1p(df["price"].to_numpy())

    x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=42)
    model = RandomForestRegressor(n_estimators=200, min_samples_leaf=3, n_jobs=-1, random_state=42)
    model.fit(x_train, y_train)
    pred_test = np.expm1(model.predict(x_test))
    r2 = r2_score(np.expm1(y_test), pred_test)
    mae = mean_absolute_error(np.expm1(y_test), pred_test)
    print(f"[적정가 모델] R2={r2:.3f}, MAE={mae:,.0f}만원")

    x_table = np.hstack([table[NUM_FEATURES].to_numpy(), encoder.transform(table[["region"]])])
    table["predictedPrice"] = np.expm1(model.predict(x_table)).round(0)
    # 1보다 크면 예측 적정가보다 싸게 거래된 단지 (저평가)
    table["valueScore"] = (table["predictedPrice"] / table["price"]).round(3)

    # 2) 유사 단지 군집화
    scaler = StandardScaler()
    scaled = scaler.fit_transform(table[["price", "area", "age"]])
    n_clusters = min(8, max(1, len(table) // 20))
    table["cluster"] = KMeans(n_clusters=n_clusters, n_init=10, random_state=42).fit_predict(scaled)

    # 3) 브라우저용 JSON 출력 (필요한 컬럼만)
    items = [{
        "id": i,
        "sido": r.sido, "sgg": r.sgg, "umd": r.umdNm, "name": r.aptNm,
        "rooms": int(r.rooms), "area": round(float(r.area), 1), "floor": int(r.floor),
        "buildYear": int(r.buildYear),
        "price": int(r.price), "lastPrice": int(r.lastPrice),
        "lastDealDate": r.lastDealDate.strftime("%Y-%m-%d"), "dealCount": int(r.dealCount),
        "predictedPrice": int(r.predictedPrice), "valueScore": float(r.valueScore),
        "cluster": int(r.cluster),
    } for i, r in enumerate(table.itertuples(index=False))]

    # 4) 시장 인사이트 요약 + Plotly 그래프
    insights = build_insights(df, table, model)
    insights["charts"] = build_charts(insights)

    now = datetime.now(KST)
    payload = {
        # generatedAtEpoch 는 맨 앞에 둔다 (Spring 이 파일 앞부분만 읽어 서버 반영 여부 확인)
        "generatedAtEpoch": int(now.timestamp()),
        "generatedAt": now.strftime("%Y-%m-%d %H:%M"),
        "source": "국토교통부_아파트 매매 실거래가 자료",
        "tradeCount": int(len(df)),
        "periodFrom": df["dealDate"].min().strftime("%Y-%m-%d"),
        "periodTo": df["dealDate"].max().strftime("%Y-%m-%d"),
        "model": {"name": "RandomForestRegressor + KMeans", "r2": round(float(r2), 3), "mae": int(mae)},
        "scaler": {
            "features": ["price", "area", "age"],
            "mean": scaler.mean_.round(4).tolist(),
            "scale": scaler.scale_.round(4).tolist(),
        },
        "insights": insights,
        "items": items,
    }

    OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"단지 {len(items):,}개 → {OUTPUT_JSON} ({OUTPUT_JSON.stat().st_size / 1024:,.0f} KB)")


if __name__ == "__main__":
    main()
