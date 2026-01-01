# Spring Boot + Nginx + OAuth2 Template

Spring Boot 3.x, Spring Security 6.x, Google OAuth 2.0를 사용하는 재사용 가능한 보일러플레이트 프로젝트입니다.
Docker Compose를 사용하여 Nginx와 Spring Boot를 한 번에 실행할 수 있습니다.

## 🏗️ Architecture

```
Client (Browser)
    ↓ http://localhost:80
Nginx (Reverse Proxy)
    ↓ proxy_pass
Spring Boot :8080 (OAuth2 Client)
    ↓ OAuth2 Authorization Code Flow
Google OAuth2 Provider
```

## 🚀 Tech Stack

- **Java**: 17
- **Spring Boot**: 3.5.9
- **Spring Security**: 6.x
- **OAuth2 Client**: Google Provider
- **Template Engine**: Thymeleaf
- **Web Server**: Nginx (Reverse Proxy)
- **Build Tool**: Gradle 8.5
- **Container**: Docker & Docker Compose

## 📋 Prerequisites

- Docker & Docker Compose 설치
- Google Cloud Console 계정
- Google OAuth 2.0 Client ID & Secret

## 🔑 Google OAuth2 Setup

### 1. Google Cloud Console 설정

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 새 프로젝트 생성 또는 기존 프로젝트 선택
3. **API 및 서비스 > 사용자 인증 정보** 메뉴로 이동
4. **사용자 인증 정보 만들기 > OAuth 2.0 클라이언트 ID** 선택
5. 애플리케이션 유형: **웹 애플리케이션** 선택
6. 승인된 리디렉션 URI 추가:
   ```
   http://localhost/login/oauth2/code/google
   http://localhost:80/login/oauth2/code/google
   ```
7. **만들기** 클릭 후 **클라이언트 ID**와 **클라이언트 보안 비밀번호** 복사

### 2. 환경변수 파일 생성

프로젝트 루트 디렉토리에 `.env` 파일을 생성하고 Google OAuth2 Credentials를 설정합니다:

```bash
# .env
GOOGLE_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret-here
```

**⚠️ 주의**: `.env` 파일은 절대 Git에 커밋하지 마세요! (`.gitignore`에 추가되어 있음)

## 🐳 Running with Docker Compose

### 1. 프로젝트 빌드 및 실행

```bash
# Docker Compose로 Nginx + Spring Boot 실행
docker-compose up --build
```

### 2. 애플리케이션 접속

브라우저에서 http://localhost 접속

### 3. 로그인 테스트

1. 브라우저가 자동으로 `/login` 페이지로 리다이렉트됨
2. **"Google로 로그인"** 버튼 클릭
3. Google 계정으로 로그인 및 권한 동의
4. 로그인 성공 시 사용자 이름, 이메일, 프로필 사진이 표시됨

### 4. 종료

```bash
# Docker Compose 종료
docker-compose down

# 컨테이너 및 이미지 완전 삭제
docker-compose down --rmi all -v
```

## 🛠️ Local Development (Docker 없이 실행)

### 1. 환경변수 설정

```bash
export GOOGLE_CLIENT_ID=your-client-id-here
export GOOGLE_CLIENT_SECRET=your-client-secret-here
```

### 2. Spring Boot 실행

```bash
./gradlew bootRun
```

### 3. 접속

- Spring Boot는 `http://localhost:8080`에서 직접 실행됨
- Google OAuth2 Redirect URI도 `http://localhost:8080/login/oauth2/code/google`로 변경 필요

**⚠️ 주의**: 로컬 개발 시에는 Nginx를 사용하지 않으므로 포트 8080으로 직접 접속해야 합니다.

## 📂 Project Structure

```
security-template/
├── docker-compose.yml              # Docker Compose 설정
├── Dockerfile                      # Spring Boot 이미지 빌드
├── nginx/
│   └── conf.d/
│       └── default.conf            # Nginx 리버스 프록시 설정
├── build.gradle                    # Gradle 의존성 설정
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/oauth/
│       │       ├── OAuthApplication.java           # Main Class
│       │       ├── config/
│       │       │   └── SecurityConfig.java         # Security 설정
│       │       └── controller/
│       │           └── HomeController.java         # 페이지 컨트롤러
│       └── resources/
│           ├── application.yml                     # Spring Boot 설정
│           └── templates/
│               ├── login.html                      # 로그인 페이지
│               └── home.html                       # 메인 페이지
└── README.md
```

## 🔧 Key Configuration

### 1. Nginx Forwarded Headers

`nginx/conf.d/default.conf`:
```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

### 2. Spring Boot Forward Headers Strategy

`application.yml`:
```yaml
server:
  forward-headers-strategy: framework  # Nginx 헤더 인식
```

이 설정이 없으면 OAuth2 redirect URI가 `http://spring-boot:8080`으로 생성되어 콜백이 실패합니다.

## 🎯 OAuth2 Flow

1. **사용자 로그인 시도** → `/` 접속
2. **인증 필요** → `/login`으로 리다이렉트 (Spring Security)
3. **OAuth2 시작** → `/oauth2/authorization/google` 클릭
4. **Google 인가 서버** → 사용자 인증 및 동의
5. **Callback** → `http://localhost/login/oauth2/code/google?code=...`
6. **토큰 교환** → Spring이 인가 코드로 액세스 토큰 획득
7. **사용자 정보 조회** → Google UserInfo API 호출
8. **인증 완료** → 세션 생성 및 `/`로 리다이렉트

## 🐞 Troubleshooting

### 1. redirect_uri_mismatch 에러

**원인**: Google OAuth2 Console에 등록한 Redirect URI와 실제 요청 URI가 일치하지 않음

**해결**:
- Google Cloud Console에서 Redirect URI 확인:
  ```
  http://localhost/login/oauth2/code/google
  ```
- `application.yml`의 `forward-headers-strategy: framework` 설정 확인
- Nginx의 `proxy_set_header` 설정 확인

### 2. 무한 리다이렉트 루프

**원인**: `/login` 페이지에 대한 접근 권한이 없어서 계속 리다이렉트됨

**해결**:
- `SecurityConfig.java`에서 `.requestMatchers("/login").permitAll()` 확인

### 3. 환경변수 인식 실패

**원인**: `.env` 파일이 없거나 잘못된 형식

**해결**:
```bash
# .env 파일 확인
cat .env

# Docker Compose 재시작
docker-compose down
docker-compose up --build
```

## 📝 License

MIT License

## 🤝 Contributing

이슈 및 Pull Request를 환영합니다!

---

**Powered by Spring Boot 3.x, Spring Security 6.x, and Google OAuth 2.0**
