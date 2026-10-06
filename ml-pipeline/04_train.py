"""4단계: 머신러닝(scikit-learn) → src/main/resources/static/data/apartments.json

- RandomForestRegressor: 단지 특성으로 '적정 거래가'를 예측 → 실제가 대비 저평가 점수(valueScore)
- KMeans: 비슷한 단지끼리 군집화(cluster) → 추천 시 비슷한 대안 단지 묶음에 활용
- StandardScaler 기준값을 JSON 에 함께 저장 → 브라우저에서 같은 기준으로 유사도 계산
"""
import json
from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from common import OUTPUT_JSON, PROCESSED_CSV

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


def main():
    df = pd.read_csv(PROCESSED_CSV, parse_dates=["dealDate"])
    table = build_complex_table(df)

    # 1) 적정가 예측 모델 (거래 단위로 학습)
    encoder = OneHotEncoder(handle_unknown="ignore", sparse_output=False)
    x_cat = encoder.fit_transform(df[["sgg"]])
    x = np.hstack([df[NUM_FEATURES].to_numpy(), x_cat])
    y = np.log1p(df["price"].to_numpy())

    x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=42)
    model = RandomForestRegressor(n_estimators=200, min_samples_leaf=3, n_jobs=-1, random_state=42)
    model.fit(x_train, y_train)
    pred_test = np.expm1(model.predict(x_test))
    r2 = r2_score(np.expm1(y_test), pred_test)
    mae = mean_absolute_error(np.expm1(y_test), pred_test)
    print(f"[적정가 모델] R2={r2:.3f}, MAE={mae:,.0f}만원")

    x_table = np.hstack([table[NUM_FEATURES].to_numpy(), encoder.transform(table[["sgg"]])])
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
        "items": items,
    }

    OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"단지 {len(items):,}개 → {OUTPUT_JSON} ({OUTPUT_JSON.stat().st_size / 1024:,.0f} KB)")


if __name__ == "__main__":
    main()
