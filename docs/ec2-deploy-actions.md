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

## EC2_HOST 에 도메인 사용 (IP 를 넣으면 재시작 후 배포 실패)

- 2026-10-08 14:44 인스턴스 재시작으로 퍼블릭 IP 가 `52.79.239.2` → `13.124.134.6` 으로 바뀜
  → IP 로 등록된 `EC2_HOST` 로는 SSH 시간 초과 (보안 그룹은 정상이었음)
- 워크플로가 `EC2_HOST` 가 IP 형식이면 경고를 출력함

- 가능: `EC2_HOST=homepickkr.duckdns.org` (ssh 가 도메인을 IP 로 변환)
- IP 가 바뀌어도 DuckDNS 만 갱신되면 Secrets 수정 불필요
- SSH(22번)는 HTTPS 와 별개 → **보안 그룹 인바운드 22번** 필요 (GitHub Actions 서버 IP 는 고정되지 않음)

## 배포 흐름 (`deploy` 작업)

```
main 에 push → build 작업(빌드·테스트·Docker 검사) 성공
  → deploy 작업
     1. Secrets 확인 (없으면 경고 후 건너뜀)
     2. SSH 키 설정, EC2 22번 연결 확인 (실패 시 원인 출력)
     3. GitHub Secrets → env 파일 생성 → EC2 ~/homepick.env 업로드 (필수 값 누락 시 이름 출력 후 중단)
     4. EC2 에서 실행:
        - ~/homepick_lo 에 저장소 clone(최초) / 해당 커밋으로 갱신
        - docker build → 기존 homepick 컨테이너 제거 → 새 컨테이너 실행
          --env-file ~/homepick.env, -p 127.0.0.1:8080:8080, --restart unless-stopped
        - 최대 120초 동안 http://127.0.0.1:8080/ 응답 확인
          실패 시 컨테이너 로그 80줄을 Actions 로그에 출력
     5. https://EC2_HOST/ 응답 코드 확인
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

### 2) 앱 환경변수도 GitHub Secrets 로 등록 (EC2 에서 env 파일을 직접 만들 필요 없음)

배포 때마다 `Upload app env from Secrets` 단계가 Secrets 로 env 파일을 만들어
EC2 `~/homepick.env`(권한 600)로 업로드하고, 컨테이너가 `--env-file` 로 읽음.

| 구분 | Secret 이름 |
|---|---|
| **필수** (없으면 배포 중단, 이름 목록 출력) | `DB_HOST` `DB_PORT` `DB_DATABASE` `DB_USERNAME` `DB_PASSWORD` `GOOGLE_CLIENT_ID` `NAVER_CLIENT_ID` `KAKAO_CLIENT_ID` |
| 선택 (없으면 경고만) | `GOOGLE_CLIENT_SECRET` `NAVER_CLIENT_SECRET` `KAKAO_CLIENT_SECRET` `FIREBASE_WEB_API_KEY` `FIREBASE_WEB_AUTH_DOMAIN` `FIREBASE_WEB_PROJECT_ID` `FIREBASE_WEB_STORAGE_BUCKET` `FIREBASE_WEB_MESSAGING_SENDER_ID` `FIREBASE_WEB_APP_ID` `GH_ACTIONS_TOKEN` |
| 자동 | `GITHUB_REPO` = 현재 저장소(`fsclass-n/homepick_lo`) |

- `DB_*` 5개는 CI 빌드용으로 이미 등록되어 있으므로 그대로 함께 사용
- **GitHub 는 `GITHUB_` 로 시작하는 Secret 이름을 허용하지 않음** → AI 재학습용 토큰은 `GH_ACTIONS_TOKEN` 으로 등록,
  워크플로가 앱에는 `GITHUB_ACTIONS_TOKEN` 으로 넘김
- 값은 로그에 `***` 로 가려지고, 러너에서 만든 임시 파일은 업로드 후 삭제
- 소셜 로그인 `CLIENT_ID` 가 비어 있으면 **앱이 시작되지 않음** (CI 테스트 실패와 같은 원인) → 필수로 검사
- Secrets 를 수정한 뒤에는 Actions 에서 Re-run(또는 main 에 push) 해야 EC2 에 반영됨

### 3) EC2 준비 (SSH 접속 후 1회)

```bash
# Docker·git 설치 (Amazon Linux 2023 예시, Ubuntu 는 apt 사용)
sudo dnf install -y docker git
sudo systemctl enable --now docker
```
- t2.micro 등 메모리 1GB 이하면 EC2 안의 Gradle 빌드가 메모리 부족으로 실패할 수 있음 → 스왑 2GB 권장

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 4) nginx 프록시 대상 확인

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

### 5) 그 밖의 확인

- TiDB Cloud 에 IP 허용 목록이 있다면 EC2 IP(`52.79.239.2`) 추가
- 소셜 로그인 콘솔에 리다이렉트 URI 추가
  `https://homepickkr.duckdns.org/sns/google-callback` · `/sns/naver-callback` · `/sns/kakao-callback`
- `server.forward-headers-strategy=native` 로 nginx 의 `X-Forwarded-Proto` 를 신뢰
  → `{baseUrl}` 이 `https://homepickkr.duckdns.org` 로 만들어져 위 URI 와 일치

## 첫 자동 배포 결과 (커밋 a08b6a8)

- Secrets → env 12개 항목 업로드 → EC2 Docker 빌드 → **"배포 성공: 앱이 8080 포트에서 응답"**
- `https://homepickkr.duckdns.org/` 200, `/ai/recommend` 200 (이전 502 해결)
- 구글 로그인 리다이렉트: `redirect_uri=https://homepickkr.duckdns.org/sns/google-callback` (https 로 정상 생성)
- 경고로 남은 항목
  - `FIREBASE_WEB_*` 6개 없음 → 회원가입 휴대폰 인증 동작 안 함
  - `GH_ACTIONS_TOKEN` 없음 → `/api/ai-data/status` 가 `configured:false`, AI 재학습 버튼 사용 불가 (추천·인사이트는 정상)
- 공개 HTTPS 확인 단계는 기동 직후 일시적으로 000 이 나와 최대 5회 재시도로 보완

## 검증 (작업 시점)

- `deploy.yml` YAML 구조 확인 (build → deploy, 단계 4개), EC2 에서 실행할 스크립트 `bash -n` 문법 검사 통과
- 새 설정 포함 `./gradlew build` (CI 와 같은 조건) 통과
- 실제 EC2 접속·배포는 Secrets·EC2 준비 후 Actions 실행 결과로 확인 필요
