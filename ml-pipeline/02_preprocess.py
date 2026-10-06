"""2단계: 전처리 data/raw/*.json → data/processed/apartments.csv"""
import json

import pandas as pd

from common import PROCESSED_CSV, PROCESSED_DIR, RAW_DIR, REGIONS, estimate_rooms


def load_raw() -> pd.DataFrame:
    frames = []
    for path in sorted(RAW_DIR.glob("*.json")):
        rows = json.loads(path.read_text(encoding="utf-8"))
        if rows:
            df = pd.DataFrame(rows)
            df["lawdCd"] = path.stem.split("_")[0]
            frames.append(df)
    if not frames:
        raise SystemExit("data/raw 에 크롤링 결과가 없습니다. 01_crawl.py 를 먼저 실행하세요.")
    return pd.concat(frames, ignore_index=True)


def preprocess(df: pd.DataFrame) -> pd.DataFrame:
    # 해제(취소)된 거래 제외
    if "cdealType" in df.columns:
        df = df[df["cdealType"].fillna("").str.strip() == ""]

    out = pd.DataFrame({
        "lawdCd": df["lawdCd"],
        "aptNm": df["aptNm"].str.strip(),
        "umdNm": df["umdNm"].str.strip(),
        "price": pd.to_numeric(df["dealAmount"].str.replace(",", ""), errors="coerce"),  # 만원
        "area": pd.to_numeric(df["excluUseAr"], errors="coerce"),                       # ㎡
        "floor": pd.to_numeric(df["floor"], errors="coerce"),
        "buildYear": pd.to_numeric(df["buildYear"], errors="coerce"),
        "dealDate": pd.to_datetime(
            df["dealYear"] + "-" + df["dealMonth"].str.zfill(2) + "-" + df["dealDay"].str.zfill(2),
            errors="coerce"),
    })

    out = out.dropna(subset=["aptNm", "price", "area", "buildYear", "dealDate"])
    out = out[(out["price"] > 0) & (out["area"] > 10)]
    out = out.drop_duplicates()

    out["sido"] = out["lawdCd"].map(lambda c: REGIONS.get(c, ("", ""))[0])
    out["sgg"] = out["lawdCd"].map(lambda c: REGIONS.get(c, ("", ""))[1])
    out["rooms"] = out["area"].apply(estimate_rooms)
    out["pyeong"] = (out["area"] / 3.3058).round(1)
    out["pricePerPyeong"] = (out["price"] / out["pyeong"]).round(0)
    out["age"] = pd.Timestamp.today().year - out["buildYear"]

    # 이상치 제거: 시군구별 평당가 상하위 1%
    q = out.groupby("sgg")["pricePerPyeong"].transform
    out = out[(out["pricePerPyeong"] >= q(lambda s: s.quantile(0.01)))
              & (out["pricePerPyeong"] <= q(lambda s: s.quantile(0.99)))]

    out["floor"] = out["floor"].fillna(out["floor"].median()).astype(int)
    return out.reset_index(drop=True)


def main():
    df = preprocess(load_raw())
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    df.to_csv(PROCESSED_CSV, index=False, encoding="utf-8-sig")
    print(f"전처리 완료: {len(df):,}건 → {PROCESSED_CSV}")
    print(df[["price", "area", "pricePerPyeong", "age"]].describe().round(1))


if __name__ == "__main__":
    main()
