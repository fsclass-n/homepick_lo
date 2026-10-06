"""전체 파이프라인 실행: 크롤링 → 전처리 → EDA → 학습/JSON 생성"""
import runpy

from common import BASE_DIR

for step in ["01_crawl.py", "02_preprocess.py", "03_eda.py", "04_train.py"]:
    print(f"\n===== {step} =====")
    runpy.run_path(str(BASE_DIR / step), run_name="__main__")
