"""1단계: 공공데이터 API 크롤링 → data/raw/{지역코드}_{년월}.json"""
import json
import os
import time
import xml.etree.ElementTree as ET
from datetime import date

import requests
from dotenv import load_dotenv

from common import API_URL, ENV_FILE, RAW_DIR, REGIONS

load_dotenv(ENV_FILE)
SERVICE_KEY = os.getenv("DATA_GO_KR_API_KEY", "")
MONTHS = int(os.getenv("CRAWL_MONTHS", "6"))
NUM_OF_ROWS = 1000


def recent_months(n: int) -> list[str]:
    """이번 달 제외 최근 n개월 (실거래 신고 지연 고려) → ['202609', ...]"""
    y, m = date.today().year, date.today().month
    result = []
    for _ in range(n):
        m -= 1
        if m == 0:
            y, m = y - 1, 12
        result.append(f"{y}{m:02d}")
    return result


def fetch_page(lawd_cd: str, deal_ymd: str, page: int) -> tuple[list[dict], int]:
    params = {
        "serviceKey": SERVICE_KEY,
        "LAWD_CD": lawd_cd,
        "DEAL_YMD": deal_ymd,
        "pageNo": page,
        "numOfRows": NUM_OF_ROWS,
    }
    res = requests.get(API_URL, params=params, timeout=20)
    res.raise_for_status()

    root = ET.fromstring(res.content)
    code = root.findtext(".//resultCode", "")
    if code not in ("00", "000"):
        raise RuntimeError(f"API 오류 [{code}] {root.findtext('.//resultMsg', '')}")

    items = [{el.tag: (el.text or "").strip() for el in item} for item in root.iter("item")]
    total = int(root.findtext(".//totalCount", "0") or 0)
    return items, total


def crawl(lawd_cd: str, deal_ymd: str) -> list[dict]:
    rows, page = [], 1
    while True:
        items, total = fetch_page(lawd_cd, deal_ymd, page)
        rows.extend(items)
        if len(rows) >= total or not items:
            return rows
        page += 1


def main():
    if not SERVICE_KEY:
        raise SystemExit("프로젝트 루트 .env 에 DATA_GO_KR_API_KEY 를 설정하세요.")

    RAW_DIR.mkdir(parents=True, exist_ok=True)
    months = recent_months(MONTHS)
    for lawd_cd, (sido, sgg) in REGIONS.items():
        for deal_ymd in months:
            path = RAW_DIR / f"{lawd_cd}_{deal_ymd}.json"
            if path.exists():  # 이미 받은 파일은 건너뜀 (재실행 시 호출량 절약)
                continue
            rows = crawl(lawd_cd, deal_ymd)
            path.write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
            print(f"{sido} {sgg} {deal_ymd}: {len(rows)}건")
            time.sleep(0.2)


if __name__ == "__main__":
    main()
