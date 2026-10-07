"""1단계: 공공데이터 API 크롤링 → data/raw/{지역코드}_{년월}.json"""
import json
import os
import time
import xml.etree.ElementTree as ET
from datetime import date

import requests
from dotenv import load_dotenv
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from common import API_URL, ENV_FILE, RAW_DIR, REGIONS

load_dotenv(ENV_FILE)
SERVICE_KEY = os.getenv("DATA_GO_KR_API_KEY", "")
MONTHS = int(os.getenv("CRAWL_MONTHS", "6"))
NUM_OF_ROWS = 1000
TIMEOUT = (10, 40)          # (연결, 응답) 초
MAX_FAIL_RATIO = 0.1        # 지역·월 요청의 10% 넘게 실패하면 학습 중단 (데이터 부족)

# 공공데이터 API 는 가끔 응답이 느리거나 5xx 를 반환 → 지수 백오프로 최대 4회 재시도
session = requests.Session()
session.mount("https://", HTTPAdapter(max_retries=Retry(
    total=4, connect=4, read=4, backoff_factor=2,
    status_forcelist=(429, 500, 502, 503, 504), allowed_methods=("GET",))))


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
    res = session.get(API_URL, params=params, timeout=TIMEOUT)
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
    total, failures = 0, []
    for lawd_cd, (sido, sgg) in REGIONS.items():
        for deal_ymd in months:
            path = RAW_DIR / f"{lawd_cd}_{deal_ymd}.json"
            if path.exists():  # 이미 받은 파일은 건너뜀 (재실행 시 호출량 절약)
                continue
            total += 1
            try:
                rows = crawl(lawd_cd, deal_ymd)
            except (requests.RequestException, RuntimeError, ET.ParseError) as e:
                # 재시도 후에도 실패한 지역·월은 건너뛰고 계속 (일부 누락은 학습에 큰 영향 없음)
                failures.append(f"{sido} {sgg} {deal_ymd}")
                print(f"[경고] {sido} {sgg} {deal_ymd} 수집 실패: {type(e).__name__}: {e}", flush=True)
                continue
            path.write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
            print(f"{sido} {sgg} {deal_ymd}: {len(rows)}건", flush=True)
            time.sleep(0.2)

    print(f"수집 완료: {total - len(failures)}/{total}건 성공", flush=True)
    if total and len(failures) / total > MAX_FAIL_RATIO:
        raise SystemExit(f"CrawlError: 공공데이터 API 요청 {len(failures)}/{total}건 실패 "
                         f"(허용 {int(MAX_FAIL_RATIO * 100)}% 초과) - {', '.join(failures[:5])}")


if __name__ == "__main__":
    main()
