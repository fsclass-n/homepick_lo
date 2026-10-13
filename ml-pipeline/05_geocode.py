"""5단계: 단지 주소 → 좌표(위도·경도) 변환 → apartments.json 의 items 에 lat, lng 추가

- 지도 중심 실거래 조회 페이지(/map)에서 단지 위치를 표시하는 데 사용
- 카카오 로컬 API(주소 검색, 실패 시 키워드 검색) 사용: KAKAO_REST_API_KEY (없으면 KAKAO_CLIENT_ID = 같은 REST 키)
- 결과는 geocode_cache.json 에 저장 → 다음 재학습부터는 새로 생긴 단지만 조회
- 키가 없거나 API 를 쓸 수 없어도 학습 결과는 그대로 두고(캐시 좌표만 반영) 정상 종료
"""
import json
import os
import threading
from concurrent.futures import ThreadPoolExecutor

import requests
from dotenv import load_dotenv

from common import ENV_FILE, GEOCODE_CACHE, OUTPUT_JSON

load_dotenv(ENV_FILE)
API_KEY = os.getenv("KAKAO_REST_API_KEY") or os.getenv("KAKAO_CLIENT_ID", "")
ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json"
KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
WORKERS = 8
SIDO_NAMES = {"서울": "서울", "인천": "인천", "경기": "경기"}

_local = threading.local()


class ServiceDisabled(Exception):
    """앱에서 카카오맵(지도·로컬) 사용 설정이 꺼져 있거나 키가 잘못된 경우"""


def session() -> requests.Session:
    if not hasattr(_local, "s"):
        _local.s = requests.Session()
        _local.s.headers["Authorization"] = f"KakaoAK {API_KEY}"
    return _local.s


def search(url: str, query: str) -> list:
    res = session().get(url, params={"query": query, "size": 5}, timeout=10)
    if res.status_code in (401, 403):
        raise ServiceDisabled(res.text[:200])
    res.raise_for_status()
    return res.json().get("documents", [])


def in_sido(doc: dict, sido: str) -> bool:
    address = doc.get("address_name") or (doc.get("address") or {}).get("address_name", "")
    return address.startswith(SIDO_NAMES.get(sido, sido))


def geocode(item: dict):
    """지번 주소 → 좌표. 행정구역 개편(인천·화성 신설 구)으로 주소가 안 맞으면 구 이름을 빼고 재시도,
    지번이 없으면 '동 + 단지명' 키워드 검색. 실패하면 None"""
    sido, sgg, umd, jibun, name = item["sido"], item["sgg"], item["umd"], item.get("jibun", ""), item["name"]
    queries = []
    if jibun:
        queries.append(f"{sido} {sgg} {umd} {jibun}")
        if " " in sgg:
            queries.append(f"{sido} {sgg.split()[0]} {umd} {jibun}")
        queries.append(f"{sido} {umd} {jibun}")
    for q in queries:
        for doc in search(ADDRESS_URL, q):
            if in_sido(doc, sido):
                return [round(float(doc["y"]), 6), round(float(doc["x"]), 6)]
    for q in (f"{sgg} {umd} {name}", f"{umd} {name} 아파트"):
        for doc in search(KEYWORD_URL, q):
            if in_sido(doc, sido):
                return [round(float(doc["y"]), 6), round(float(doc["x"]), 6)]
    return None


def cache_key(item: dict) -> str:
    return "|".join([item["sido"], item["sgg"], item["umd"], item.get("jibun", ""), item["name"]])


def main():
    payload = json.loads(OUTPUT_JSON.read_text(encoding="utf-8"))
    items = payload["items"]
    cache = json.loads(GEOCODE_CACHE.read_text(encoding="utf-8")) if GEOCODE_CACHE.exists() else {}

    todo = {}
    for it in items:
        key = cache_key(it)
        if key not in cache:
            todo.setdefault(key, it)

    print(f"단지 {len(items):,}개 · 주소 {len({cache_key(i) for i in items}):,}개 · 새로 조회할 주소 {len(todo):,}개")
    if todo and not API_KEY:
        print("::warning::KAKAO_REST_API_KEY 가 없어 좌표 변환을 건너뜁니다 (캐시된 좌표만 반영).")
    elif todo:
        disabled, lock = threading.Event(), threading.Lock()

        def work(pair):
            key, it = pair
            if disabled.is_set():
                return key, "skip"
            try:
                return key, geocode(it)
            except ServiceDisabled as e:
                with lock:
                    if not disabled.is_set():
                        disabled.set()
                        print(f"::warning::카카오 로컬 API 를 사용할 수 없어 좌표 변환을 건너뜁니다 "
                              f"(카카오 디벨로퍼스 > 앱 > 카카오맵 사용 설정 확인): {e}")
                return key, "skip"
            except requests.RequestException:
                return key, "skip"  # 일시 오류는 캐시하지 않고 다음 재학습 때 다시 조회

        added = 0
        with ThreadPoolExecutor(max_workers=WORKERS) as pool:
            for n, (key, result) in enumerate(pool.map(work, todo.items()), 1):
                if result != "skip":
                    cache[key] = result  # None(찾지 못함)도 저장해 매번 다시 조회하지 않음
                    added += 1
                if n % 1000 == 0 and not disabled.is_set():
                    print(f"  {n:,}/{len(todo):,}")
        print(f"새로 조회한 주소 {added:,}개")
        if added:
            GEOCODE_CACHE.write_text(json.dumps(cache, ensure_ascii=False, separators=(",", ":"), sort_keys=True),
                                     encoding="utf-8")

    found = 0
    for it in items:
        pos = cache.get(cache_key(it))
        if pos:
            it["lat"], it["lng"] = pos
            found += 1
        else:
            it.pop("lat", None)
            it.pop("lng", None)
    OUTPUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"좌표 반영: {found:,}/{len(items):,}개 단지 ({found / max(len(items), 1):.1%})")


if __name__ == "__main__":
    main()
