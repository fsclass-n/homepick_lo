"""파이프라인 공통 설정 (경로, 수집 지역 코드)"""
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent
# 환경변수는 프로젝트 루트 .env 하나로 통합 관리 (Spring 과 공용, GitHub Actions 에서는 Secrets 로 주입)
ENV_FILE = PROJECT_DIR / ".env"
RAW_DIR = BASE_DIR / "data" / "raw"
PROCESSED_DIR = BASE_DIR / "data" / "processed"
REPORT_DIR = BASE_DIR / "data" / "reports"
PROCESSED_CSV = PROCESSED_DIR / "apartments.csv"

# 최종 결과물: Spring Boot 정적 리소스 → /data/apartments.json 으로 제공
OUTPUT_JSON = PROJECT_DIR / "src" / "main" / "resources" / "static" / "data" / "apartments.json"

# 국토교통부_아파트 매매 실거래가 자료
API_URL = "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade"

# LAWD_CD(법정동코드 앞 5자리) → (시도, 시군구)
REGIONS = {
    "11110": ("서울", "종로구"), "11140": ("서울", "중구"), "11170": ("서울", "용산구"),
    "11200": ("서울", "성동구"), "11215": ("서울", "광진구"), "11230": ("서울", "동대문구"),
    "11260": ("서울", "중랑구"), "11290": ("서울", "성북구"), "11305": ("서울", "강북구"),
    "11320": ("서울", "도봉구"), "11350": ("서울", "노원구"), "11380": ("서울", "은평구"),
    "11410": ("서울", "서대문구"), "11440": ("서울", "마포구"), "11470": ("서울", "양천구"),
    "11500": ("서울", "강서구"), "11530": ("서울", "구로구"), "11545": ("서울", "금천구"),
    "11560": ("서울", "영등포구"), "11590": ("서울", "동작구"), "11620": ("서울", "관악구"),
    "11650": ("서울", "서초구"), "11680": ("서울", "강남구"), "11710": ("서울", "송파구"),
    "11740": ("서울", "강동구"),
}


def estimate_rooms(area_m2: float) -> int:
    """실거래가 자료에는 방 수가 없어 전용면적으로 추정 (1~4방)"""
    if area_m2 <= 40:
        return 1
    if area_m2 <= 60:
        return 2
    if area_m2 <= 85:
        return 3
    return 4
