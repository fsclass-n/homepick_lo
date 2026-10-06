"""3단계: 탐색적 데이터 분석(EDA) → data/reports/*.png, *.html"""
import matplotlib

matplotlib.use("Agg")  # 화면 없이 파일로만 저장
import matplotlib.pyplot as plt
import pandas as pd
import plotly.express as px
import seaborn as sns

from common import PROCESSED_CSV, REPORT_DIR

plt.rcParams["font.family"] = "Malgun Gothic"  # Windows 한글 폰트
plt.rcParams["axes.unicode_minus"] = False


def main():
    df = pd.read_csv(PROCESSED_CSV, parse_dates=["dealDate"])
    REPORT_DIR.mkdir(parents=True, exist_ok=True)

    # 1) 시군구별 평당가 분포
    order = df.groupby("sgg")["pricePerPyeong"].median().sort_values(ascending=False).index
    plt.figure(figsize=(12, 6))
    sns.boxplot(data=df, x="sgg", y="pricePerPyeong", order=order, showfliers=False)
    plt.xticks(rotation=60)
    plt.title("시군구별 평당 거래가 (만원)")
    plt.tight_layout()
    plt.savefig(REPORT_DIR / "price_per_pyeong_by_sgg.png", dpi=120)
    plt.close()

    # 2) 수치형 변수 상관관계
    plt.figure(figsize=(7, 5))
    sns.heatmap(df[["price", "area", "floor", "age", "pricePerPyeong"]].corr(), annot=True, fmt=".2f", cmap="Blues")
    plt.title("변수 상관관계")
    plt.tight_layout()
    plt.savefig(REPORT_DIR / "correlation.png", dpi=120)
    plt.close()

    # 3) 면적 vs 가격 (인터랙티브)
    fig = px.scatter(df, x="area", y="price", color="sgg", hover_name="aptNm",
                     labels={"area": "전용면적(㎡)", "price": "거래가(만원)"}, title="전용면적 vs 거래가")
    fig.write_html(REPORT_DIR / "area_vs_price.html")

    print(f"리포트 저장 완료 → {REPORT_DIR}")


if __name__ == "__main__":
    main()
