# GitHub Actions → EC2 자동 배포 (A 방식: SSH 접속 후 EC2 에서 Docker 빌드)

- 작업일: 2026-10-08
- 대상 파일
  - `.github/workflows/deploy.yml` (`deploy` 작업 추가)
  - `src/main/resources/application.properties` (`server.forward-headers-strategy=native`)
- 서버: EC2 `52.79.239.2` → 도메인 `https://homepickkr.duckdns.org` (DuckDNS + SSL, nginx 리버스 프록시)

## 연결이 안 되던 원인

| 확인 | 결과 |
|---|---|
| 기존 `deploy.yml` | 빌드·테스트·Docker 이미지 생성 검사만 하고 **EC2 로 보내는 단계가 없었음** (GitHub 임시 서버에서 끝남) |
| "EC2 첫 배포" 커밋 | `test.txt` 1개 추가뿐 |
| `https://homepickkr.duckdns.org/` | **502 Bad Gateway** → 도메인·SSL·nginx 는 동작, 뒤에서 응답할 앱이 없음 |

## EC2_HOST 에 도메인 사용

- 가능: `EC2_HOST=homepickkr.duckdns.org` (ssh 가 도메인을 IP 로 변환)
- IP 가 바뀌어도 DuckDNS 만 갱신되면 Secrets 수정 불필요
- SSH(22번)는 HTTPS 와 별개 → **보안 그룹 인바운드 22번** 필요 (GitHub Actions 서버 IP 는 고정되지 않음)

## 배포 흐름 (`deploy` 작업)

```
main 에 push → build 작업(빌드·테스트·Docker 검사) 성공
  → deploy 작업
     1. Secrets 확인 (없으면 경고 후 건너뜀)
     2. SSH 키 설정, EC2 22번 연결 확인 (실패 시 원인 출력)
     3. EC2 에서 실행:
        - ~/homepick_lo 에 저장소 clone(최초) / 해당 커밋으로 갱신
        - docker build → 기존 homepick 컨테이너 제거 → 새 컨테이너 실행
          --env-file ~/homepick.env, -p 127.0.0.1:8080:8080, --restart unless-stopped
        - 최대 120초 동안 http://127.0.0.1:8080/ 응답 확인
          실패 시 컨테이너 로그 80줄을 Actions 로그에 출력
     4. https://EC2_HOST/ 응답 코드 확인
```

- PR 에서는 배포하지 않음, 동시 배포는 한 번에 하나(`concurrency: ec2-deploy`)
- 컨테이너는 `127.0.0.1:8080` 에만 열림 → 외부는 nginx(HTTPS)로만 접속
- AI 재학습 데이터 커밋(Actions 봇)은 워크플로를 다시 실행시키지 않음 → 재배포 없음 (앱이 GitHub 에서 최신 데이터를 직접 받음)

## 해야 할 설정

### 1) GitHub Secrets (저장소 Settings → Secrets and variables → Actions)

| 이름 | 값 |
|---|---|
| `EC2_HOST` | `homepickkr.duckdns.org` |
| `EC2_USER` | Amazon Linux: `ec2-user`, Ubuntu: `ubuntu` |
| `EC2_SSH_KEY` | EC2 키 페어 `.pem` 파일 전체 내용 (`-----BEGIN ...` 부터 `-----END ...` 까지) |

### 2) EC2 준비 (SSH 접속 후 1회)

```bash
# Docker·git 설치 (Amazon Linux 2023 예시, Ubuntu 는 apt 사용)
sudo dnf install -y docker git
sudo systemctl enable --now docker

# 앱 환경변수 파일 (따옴표 없이 KEY=VALUE)
nano ~/homepick.env
chmod 600 ~/homepick.env
```

`~/homepick.env` 에 넣을 항목 (로컬 `.env` 와 같은 이름):

```
DB_HOST=...
DB_PORT=4000
DB_DATABASE=...
DB_USERNAME=...
DB_PASSWORD=...
GOOGLE_CLIENT_ID=...      GOOGLE_CLIENT_SECRET=...
NAVER_CLIENT_ID=...       NAVER_CLIENT_SECRET=...
KAKAO_CLIENT_ID=...       KAKAO_CLIENT_SECRET=...
FIREBASE_WEB_API_KEY=...  (FIREBASE_WEB_* 6개)
GITHUB_ACTIONS_TOKEN=...
GITHUB_REPO=fsclass-n/homepick_lo
```

- 소셜 로그인 `CLIENT_ID` 가 비어 있으면 **앱이 시작되지 않음** (CI 테스트 실패와 같은 원인)
- t2.micro 등 메모리 1GB 이하면 EC2 안의 Gradle 빌드가 메모리 부족으로 실패할 수 있음 → 스왑 2GB 권장

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 3) nginx 프록시 대상 확인

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

### 4) 그 밖의 확인

- TiDB Cloud 에 IP 허용 목록이 있다면 EC2 IP(`52.79.239.2`) 추가
- 소셜 로그인 콘솔에 리다이렉트 URI 추가
  `https://homepickkr.duckdns.org/sns/google-callback` · `/sns/naver-callback` · `/sns/kakao-callback`
- `server.forward-headers-strategy=native` 로 nginx 의 `X-Forwarded-Proto` 를 신뢰
  → `{baseUrl}` 이 `https://homepickkr.duckdns.org` 로 만들어져 위 URI 와 일치

## 검증 (작업 시점)

- `deploy.yml` YAML 구조 확인 (build → deploy, 단계 4개), EC2 에서 실행할 스크립트 `bash -n` 문법 검사 통과
- 새 설정 포함 `./gradlew build` (CI 와 같은 조건) 통과
- 실제 EC2 접속·배포는 Secrets·EC2 준비 후 Actions 실행 결과로 확인 필요
