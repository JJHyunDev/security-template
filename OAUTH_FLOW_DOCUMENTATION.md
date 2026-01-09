# OAuth 2.0 + JWT 인증 흐름 문서

## 📚 목차
1. [시스템 아키텍처 개요](#시스템-아키텍처-개요)
2. [1단계: 초기 로그인 (OAuth2)](#1단계-초기-로그인-oauth2)
3. [2단계: JWT 토큰 발급](#2단계-jwt-토큰-발급)
4. [3단계: API 요청 (JWT 인증)](#3단계-api-요청-jwt-인증)
5. [4단계: Access Token 재발급](#4단계-access-token-재발급)
6. [5단계: 로그아웃](#5단계-로그아웃)
7. [에러 처리](#에러-처리)
8. [보안 고려사항](#보안-고려사항)

---

## 시스템 아키텍처 개요

### 전체 구성도
```
┌─────────────┐         ┌─────────────┐         ┌──────────────────┐
│             │         │             │         │                  │
│  Frontend   │◄───────►│   Backend   │◄───────►│  OAuth Provider  │
│             │         │             │         │  (Google/Kakao)  │
│             │         │             │         │                  │
└─────────────┘         └─────────────┘         └──────────────────┘
      │                       │
      │                       │
      │                 ┌─────▼─────┐
      │                 │           │
      └────────────────►│  Database │
        (간접 접근)       │  (H2/DB)  │
                        │           │
                        └───────────┘
```

### 기술 스택

#### 백엔드
- **Framework**: Spring Boot 3.5.9
- **Security**: Spring Security 6.x OAuth2 Client
- **JWT**: jjwt 0.12.6
- **Database**: H2 (개발), JPA/Hibernate
- **Language**: Java 21

#### 프론트엔드
- **현재**: Thymeleaf (서버 사이드 렌더링)
- **권장**: React, Vue, Angular 등 SPA
- **인증 방식**: JWT (Authorization: Bearer 헤더)

### 인증 메커니즘

1. **OAuth2 Authorization Code Flow**: 초기 로그인
2. **JWT (JSON Web Token)**: API 인증
3. **Refresh Token Rotation**: 보안 강화

---

## 1단계: 초기 로그인 (OAuth2)

### 📱 프론트엔드 역할

#### 1.1 로그인 페이지 렌더링
**파일**: `src/main/resources/templates/login.html`

**책임**:
- OAuth2 Provider 로그인 버튼 표시
- 사용자 클릭 이벤트 처리
- 로그인 실패 시 에러 메시지 표시

**구현**:
```html
<!-- Google 로그인 버튼 -->
<a href="/oauth2/authorization/google" class="oauth-login-btn google-login-btn">
    Google로 로그인
</a>

<!-- Kakao 로그인 버튼 -->
<a href="/oauth2/authorization/kakao" class="oauth-login-btn kakao-login-btn">
    Kakao로 로그인
</a>
```

**흐름**:
1. 사용자가 `/login` 페이지 접속
2. Google 또는 Kakao 로그인 버튼 클릭
3. `/oauth2/authorization/{provider}` 엔드포인트로 이동 (백엔드가 처리)

---

### 🖥️ 백엔드 역할

#### 1.2 OAuth2 인증 시작
**파일**: `src/main/java/com/example/oauth/config/SecurityConfig.java`

**책임**:
- Spring Security가 OAuth2 인증 프로세스 자동 처리
- Provider별 설정 로드 (Client ID, Secret, Redirect URI)
- 사용자를 OAuth Provider로 리다이렉트

**설정**:
```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: profile, email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
```

**엔드포인트**:
- **URL**: `/oauth2/authorization/{provider}`
- **Method**: GET (Spring Security 자동 생성)
- **동작**: 사용자를 OAuth Provider 인증 페이지로 리다이렉트

**예시**:
```
GET /oauth2/authorization/google

→ Redirect to:
https://accounts.google.com/o/oauth2/v2/auth
  ?client_id=...
  &redirect_uri=http://localhost/login/oauth2/code/google
  &response_type=code
  &scope=profile email
```

---

#### 1.3 사용자 인증 (OAuth Provider에서 진행)
**OAuth Provider**: Google, Kakao 등

**흐름**:
1. 사용자가 OAuth Provider 로그인 페이지에서 인증
2. 권한 동의 (이메일, 프로필 정보 제공 동의)
3. OAuth Provider가 Authorization Code 발급
4. 백엔드 Redirect URI로 콜백: `/login/oauth2/code/{provider}?code=...`

---

#### 1.4 Authorization Code 처리 및 사용자 정보 로드
**파일**: `src/main/java/com/example/oauth/domain/security/oauth/service/CustomOAuth2UserService.java`

**책임**:
- Authorization Code를 Access Token으로 교환 (Spring Security 자동)
- OAuth Provider의 UserInfo Endpoint 호출
- 사용자 정보 파싱 (Factory Pattern 적용)
- DB에 사용자 저장 또는 업데이트

**엔드포인트**:
- **URL**: `/login/oauth2/code/{provider}` (Spring Security 자동 처리)
- **Method**: GET
- **Parameter**: `code` (Authorization Code)

**처리 과정**:
```java
@Override
public OAuth2User loadUser(OAuth2UserRequest userRequest) {
    // 1. OAuth Provider에서 사용자 정보 가져오기
    OAuth2User oauth2User = super.loadUser(userRequest);

    // 2. Provider 식별
    String registrationId = userRequest.getClientRegistration().getRegistrationId();

    // 3. Factory Pattern으로 Provider별 정보 파싱
    OAuth2UserInfo oAuth2UserInfo = OAuth2Provider.getOAuth2UserInfo(
        registrationId,
        oauth2User.getAttributes()
    );

    // 4. DB에 사용자 저장 또는 업데이트
    User user = saveOrUpdate(oAuth2UserInfo);

    // 5. CustomOAuth2User 반환
    return new CustomOAuth2User(user.getRole(), attributes, userNameAttributeName);
}
```

**데이터베이스 작업**:
```sql
-- 기존 사용자 확인
SELECT * FROM users WHERE provider = 'google' AND provider_id = '1234567890';

-- 신규 사용자 INSERT
INSERT INTO users (name, email, provider, provider_id, role, picture)
VALUES ('홍길동', 'hong@gmail.com', 'google', '1234567890', 'USER', 'https://...');

-- 기존 사용자 UPDATE
UPDATE users
SET name = '홍길동', email = 'hong@gmail.com', picture = 'https://...'
WHERE provider = 'google' AND provider_id = '1234567890';
```

---

## 2단계: JWT 토큰 발급

### 🖥️ 백엔드 역할

#### 2.1 OAuth2 로그인 성공 핸들러
**파일**: `src/main/java/com/example/oauth/domain/security/oauth/handler/OAuth2AuthenticationSuccessHandler.java`

**책임**:
- OAuth2 로그인 성공 시 자동 호출
- JWT Access Token + Refresh Token 생성
- Refresh Token을 DB에 저장
- 프론트엔드로 리다이렉트 (토큰 전달)

**처리 과정**:
```java
@Override
public void onAuthenticationSuccess(HttpServletRequest request,
                                    HttpServletResponse response,
                                    Authentication authentication) {
    // 1. OAuth2 인증 정보 추출
    OAuth2AuthenticationToken oAuth2Token = (OAuth2AuthenticationToken) authentication;
    CustomOAuth2User oAuth2User = (CustomOAuth2User) oAuth2Token.getPrincipal();

    // 2. Provider 정보 추출
    String provider = oAuth2Token.getAuthorizedClientRegistrationId(); // "google"
    String providerId = oAuth2User.getName(); // "1234567890"

    // 3. DB에서 User 조회
    User user = userRepository.findByProviderAndProviderId(provider, providerId)
        .orElseThrow(...);

    // 4. JWT 토큰 생성
    JwtTokenDto jwtToken = jwtTokenProvider.generateToken(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.getProvider()
    );

    // 5. Refresh Token DB 저장
    saveRefreshToken(user.getId(), jwtToken.refreshToken(), ...);

    // 6. 프론트엔드로 리다이렉트
    String targetUrl = "http://localhost/oauth/callback"
        + "?accessToken=" + jwtToken.accessToken()
        + "&refreshToken=" + jwtToken.refreshToken();

    response.sendRedirect(targetUrl);
}
```

**생성되는 JWT 토큰**:
```json
// Access Token Payload
{
  "sub": "1",                           // userId
  "email": "hong@gmail.com",
  "role": "USER",
  "provider": "google",
  "iss": "yamoyo-application",
  "iat": 1704067200,                    // 발급 시간
  "exp": 1704067800                     // 만료 시간 (10분 후)
}

// Refresh Token Payload
{
  "sub": "1",
  "email": "hong@gmail.com",
  "role": "USER",
  "provider": "google",
  "iss": "yamoyo-application",
  "iat": 1704067200,
  "exp": 1704672000                     // 만료 시간 (7일 후)
}
```

**데이터베이스 작업**:
```sql
-- Refresh Token 저장 (userId가 unique이므로 중복 시 업데이트)
INSERT INTO refresh_tokens (user_id, token, expiry_date, created_at)
VALUES (1, 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...', '2024-01-14 12:00:00', NOW())
ON DUPLICATE KEY UPDATE
  token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
  expiry_date = '2024-01-14 12:00:00';
```

**리다이렉트**:
```
HTTP/1.1 302 Found
Location: http://localhost/oauth/callback?accessToken=eyJ...&refreshToken=eyJ...
```

---

### 📱 프론트엔드 역할

#### 2.2 토큰 수신 및 저장
**파일**: 프론트엔드 애플리케이션 (예: React, Vue)

**책임**:
- 리다이렉트된 URL의 쿼리 파라미터에서 토큰 추출
- localStorage 또는 sessionStorage에 토큰 저장
- 메인 페이지로 이동

**구현 예시 (React)**:
```javascript
// OAuth Callback 페이지 컴포넌트
// URL: http://localhost/oauth/callback?accessToken=...&refreshToken=...

import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

function OAuthCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    // 1. URL 쿼리 파라미터에서 토큰 추출
    const accessToken = searchParams.get('accessToken');
    const refreshToken = searchParams.get('refreshToken');

    if (accessToken && refreshToken) {
      // 2. localStorage에 토큰 저장
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);

      // 3. 메인 페이지로 리다이렉트
      navigate('/dashboard');
    } else {
      // 토큰이 없으면 로그인 실패 처리
      navigate('/login?error=true');
    }
  }, [searchParams, navigate]);

  return <div>로그인 처리 중...</div>;
}

export default OAuthCallback;
```

**구현 예시 (Vue)**:
```javascript
// OAuth Callback 페이지 컴포넌트
// URL: http://localhost/oauth/callback?accessToken=...&refreshToken=...

<script setup>
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

onMounted(() => {
  // 1. URL 쿼리 파라미터에서 토큰 추출
  const { accessToken, refreshToken } = route.query;

  if (accessToken && refreshToken) {
    // 2. localStorage에 토큰 저장
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    // 3. 메인 페이지로 리다이렉트
    router.push('/dashboard');
  } else {
    // 토큰이 없으면 로그인 실패 처리
    router.push('/login?error=true');
  }
});
</script>

<template>
  <div>로그인 처리 중...</div>
</template>
```

**구현 예시 (Vanilla JavaScript)**:
```javascript
// oauth-callback.html
window.addEventListener('DOMContentLoaded', () => {
  // 1. URL 쿼리 파라미터에서 토큰 추출
  const urlParams = new URLSearchParams(window.location.search);
  const accessToken = urlParams.get('accessToken');
  const refreshToken = urlParams.get('refreshToken');

  if (accessToken && refreshToken) {
    // 2. localStorage에 토큰 저장
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    // 3. 메인 페이지로 리다이렉트
    window.location.href = '/dashboard.html';
  } else {
    // 토큰이 없으면 로그인 실패 처리
    window.location.href = '/login.html?error=true';
  }
});
```

**저장된 토큰 구조**:
```javascript
// localStorage
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJob25nQGdtYWlsLmNvbSIsInJvbGUiOiJVU0VSIiwicHJvdmlkZXIiOiJnb29nbGUiLCJpc3MiOiJ5YW1veW8tYXBwbGljYXRpb24iLCJpYXQiOjE3MDQwNjcyMDAsImV4cCI6MTcwNDA2NzgwMH0.xyz",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJob25nQGdtYWlsLmNvbSIsInJvbGUiOiJVU0VSIiwicHJvdmlkZXIiOiJnb29nbGUiLCJpc3MiOiJ5YW1veW8tYXBwbGljYXRpb24iLCJpYXQiOjE3MDQwNjcyMDAsImV4cCI6MTcwNDY3MjAwMH0.abc"
}
```

---

## 3단계: API 요청 (JWT 인증)

### 📱 프론트엔드 역할

#### 3.1 API 요청 시 Authorization 헤더 추가
**책임**:
- localStorage에서 Access Token 조회
- HTTP 요청의 Authorization 헤더에 Bearer 토큰 추가
- API 응답 처리

**구현 예시 (Axios - React/Vue)**:
```javascript
import axios from 'axios';

// Axios 인터셉터 설정
const apiClient = axios.create({
  baseURL: 'http://localhost',
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터: 모든 요청에 Authorization 헤더 자동 추가
apiClient.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem('accessToken');

    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// API 호출 예시
async function fetchUserProfile() {
  try {
    const response = await apiClient.get('/api/user/profile');
    console.log('사용자 프로필:', response.data);
    return response.data;
  } catch (error) {
    console.error('API 요청 실패:', error);
    throw error;
  }
}
```

**구현 예시 (Fetch API)**:
```javascript
// Fetch API를 사용한 API 호출
async function fetchUserProfile() {
  const accessToken = localStorage.getItem('accessToken');

  try {
    const response = await fetch('http://localhost/api/user/profile', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${accessToken}`,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    console.log('사용자 프로필:', data);
    return data;
  } catch (error) {
    console.error('API 요청 실패:', error);
    throw error;
  }
}
```

**HTTP 요청 예시**:
```http
GET /api/user/profile HTTP/1.1
Host: localhost
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

### 🖥️ 백엔드 역할

#### 3.2 JWT 인증 필터
**파일**: `src/main/java/com/example/oauth/domain/security/jwt/filter/JwtAuthenticationFilter.java`

**책임**:
- HTTP 요청마다 실행되어 JWT 토큰 검증
- Authorization 헤더에서 Bearer 토큰 추출
- 토큰 유효성 검증 (서명, 만료 시간)
- SecurityContext에 인증 정보 설정

**처리 과정**:
```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) {
    try {
        // 1. Authorization 헤더에서 Bearer 토큰 추출
        String token = resolveToken(request);
        // token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

        // 2. 토큰이 존재하고 유효한 경우
        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 3. 토큰에서 사용자 정보(Claims) 추출
            JwtTokenClaims claims = jwtTokenProvider.parseClaims(token);
            // claims = { userId: 1, email: "hong@gmail.com", role: "USER", provider: "google" }

            // 4. JwtAuthenticationToken 생성 (인증 완료 상태)
            JwtAuthenticationToken authentication =
                JwtAuthenticationToken.authenticated(claims);

            // 5. SecurityContext에 설정
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT 인증 성공: userId={}, email={}",
                     claims.userId(), claims.email());
        }
    } catch (Exception e) {
        log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
        SecurityContextHolder.clearContext();
    }

    // 6. 다음 필터로 요청 전달
    filterChain.doFilter(request, response);
}

private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    // bearerToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7); // "Bearer " 이후의 토큰 문자열 추출
    }

    return null;
}
```

**토큰 검증 과정**:
```java
// JwtTokenProvider.validateToken()
public boolean validateToken(String token) {
    try {
        Jwts.parser()
            .verifyWith(key)              // HMAC SHA-256 서명 검증
            .build()
            .parseSignedClaims(token);    // 토큰 파싱 및 검증
        return true;
    } catch (ExpiredJwtException e) {
        log.warn("만료된 JWT 토큰");
        return false;
    } catch (UnsupportedJwtException e) {
        log.warn("지원하지 않는 JWT 토큰");
        return false;
    } catch (MalformedJwtException e) {
        log.warn("잘못된 형식의 JWT 토큰");
        return false;
    } catch (SignatureException e) {
        log.warn("JWT 서명 검증 실패");
        return false;
    } catch (IllegalArgumentException e) {
        log.warn("JWT 토큰이 비어있음");
        return false;
    }
}
```

---

#### 3.3 Controller에서 인증 정보 사용
**파일**: 예시 Controller

**책임**:
- @AuthenticationPrincipal로 현재 사용자 정보 주입
- 비즈니스 로직 수행
- 응답 반환

**구현 예시**:
```java
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 현재 로그인한 사용자 프로필 조회
     *
     * @param claims JWT 토큰에서 추출한 사용자 정보 (JwtAuthenticationFilter에서 설정)
     * @return 사용자 프로필 정보
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            @AuthenticationPrincipal JwtTokenClaims claims) {

        log.info("사용자 프로필 조회 - UserId: {}", claims.userId());

        // 1. DB에서 사용자 정보 조회
        User user = userService.findById(claims.userId());

        // 2. 응답 DTO 생성
        UserProfileResponse response = new UserProfileResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getProvider(),
            user.getPicture(),
            user.getRole().name()
        );

        return ResponseEntity.ok(response);
    }
}
```

**HTTP 응답 예시**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "name": "홍길동",
  "email": "hong@gmail.com",
  "provider": "google",
  "picture": "https://lh3.googleusercontent.com/...",
  "role": "USER"
}
```

---

### 📱 프론트엔드 역할

#### 3.4 API 응답 처리
**책임**:
- API 응답 데이터 처리
- UI 업데이트
- 에러 처리 (401 Unauthorized 등)

**구현 예시 (React)**:
```javascript
import { useState, useEffect } from 'react';
import apiClient from './apiClient';

function UserProfile() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchProfile();
  }, []);

  async function fetchProfile() {
    try {
      setLoading(true);
      const response = await apiClient.get('/api/user/profile');
      setProfile(response.data);
    } catch (err) {
      setError(err.message);
      console.error('프로필 조회 실패:', err);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>로딩 중...</div>;
  if (error) return <div>에러: {error}</div>;

  return (
    <div>
      <h1>{profile.name}</h1>
      <img src={profile.picture} alt="프로필" />
      <p>이메일: {profile.email}</p>
      <p>Provider: {profile.provider}</p>
      <p>권한: {profile.role}</p>
    </div>
  );
}
```

---

## 4단계: Access Token 재발급

### 📱 프론트엔드 역할

#### 4.1 401 에러 감지 및 토큰 재발급 요청
**책임**:
- API 응답에서 401 Unauthorized 감지
- Refresh Token으로 `/api/auth/refresh` 호출
- 새로운 Access Token 저장
- 실패한 API 재시도

**구현 예시 (Axios 인터셉터)**:
```javascript
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

const apiClient = axios.create({
  baseURL: 'http://localhost',
});

// 요청 인터셉터: Access Token 추가
apiClient.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem('accessToken');
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 응답 인터셉터: 401 에러 시 토큰 재발급
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // 1. 401 에러이고 아직 재시도하지 않은 요청인 경우
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // 2. Refresh Token으로 Access Token 재발급
        const refreshToken = localStorage.getItem('refreshToken');

        if (!refreshToken) {
          // Refresh Token이 없으면 로그인 페이지로 이동
          window.location.href = '/login';
          return Promise.reject(error);
        }

        const response = await axios.post('http://localhost/api/auth/refresh', {
          refreshToken: refreshToken,
        });

        // 3. 새로운 토큰 저장
        const { accessToken, refreshToken: newRefreshToken } = response.data;
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', newRefreshToken);

        // 4. 원래 요청에 새로운 Access Token 적용
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;

        // 5. 원래 요청 재시도
        return apiClient(originalRequest);

      } catch (refreshError) {
        // Refresh Token도 만료되었으면 로그인 페이지로 이동
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
```

**HTTP 요청 예시**:
```http
POST /api/auth/refresh HTTP/1.1
Host: localhost
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 🖥️ 백엔드 역할

#### 4.2 Refresh Token 검증 및 새 토큰 발급
**파일**:
- `src/main/java/com/example/oauth/domain/security/service/AuthService.java`
- `src/main/java/com/example/oauth/domain/security/controller/AuthController.java`

**책임**:
- Refresh Token 검증 (DB에 존재하는지, 만료되지 않았는지)
- 새로운 Access Token + Refresh Token 생성 (Refresh Token Rotation)
- DB의 Refresh Token 업데이트
- 새로운 토큰 세트 반환

**Controller**:
```java
@PostMapping("/refresh")
public ResponseEntity<JwtTokenDto> refresh(@RequestBody RefreshTokenRequest request) {
    log.info("POST /api/auth/refresh - Access Token 재발급 요청");

    JwtTokenDto tokens = authService.refresh(request.refreshToken());

    return ResponseEntity.ok(tokens);
}
```

**Service**:
```java
@Transactional
public JwtTokenDto refresh(String refreshToken) {
    // 1. DB에서 Refresh Token 조회
    RefreshToken storedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
        .orElseThrow(() -> new RefreshTokenException("유효하지 않은 Refresh Token입니다."));

    // 2. Refresh Token 만료 여부 확인
    if (storedRefreshToken.isExpired()) {
        refreshTokenRepository.delete(storedRefreshToken);
        throw new RefreshTokenException("만료된 Refresh Token입니다. 다시 로그인해주세요.");
    }

    // 3. Refresh Token에서 사용자 정보 추출
    JwtTokenClaims claims = jwtTokenProvider.parseClaimsFromExpiredToken(refreshToken);

    // 4. DB에서 사용자 조회
    User user = userRepository.findById(claims.userId())
        .orElseThrow(() -> new RefreshTokenException("사용자를 찾을 수 없습니다."));

    // 5. 새로운 Access Token + Refresh Token 생성 (Refresh Token Rotation)
    JwtTokenDto newTokens = jwtTokenProvider.generateToken(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.getProvider()
    );

    // 6. DB에 새로운 Refresh Token 저장 (기존 토큰 교체)
    LocalDateTime newExpiryDate = LocalDateTime.now()
        .plusSeconds(newTokens.accessTokenExpiration() / 1000);
    storedRefreshToken.updateToken(newTokens.refreshToken(), newExpiryDate);

    log.info("새로운 토큰 발급 완료 - UserId: {}", user.getId());

    return newTokens;
}
```

**데이터베이스 작업**:
```sql
-- Refresh Token 조회
SELECT * FROM refresh_tokens WHERE token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...';

-- 만료 여부 확인
SELECT expiry_date < NOW() AS is_expired FROM refresh_tokens WHERE token = '...';

-- Refresh Token 업데이트 (Refresh Token Rotation)
UPDATE refresh_tokens
SET token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...새토큰',
    expiry_date = '2024-01-21 12:00:00'
WHERE user_id = 1;
```

**HTTP 응답 예시**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "grantType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...새AccessToken",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...새RefreshToken",
  "accessTokenExpiration": 600000
}
```

---

### 📱 프론트엔드 역할

#### 4.3 새로운 토큰 저장 및 요청 재시도
**책임**:
- 새로운 Access Token과 Refresh Token을 localStorage에 저장
- 실패했던 원래 API 요청 재시도
- 사용자는 토큰 재발급 과정을 인지하지 못함 (투명한 처리)

**처리 흐름** (위 4.1 Axios 인터셉터 코드 참조):
1. 원래 API 요청 → 401 에러
2. Refresh Token으로 `/api/auth/refresh` 호출
3. 새로운 토큰 수신 및 저장
4. 원래 API 요청 재시도 → 성공
5. 사용자에게 정상 응답 전달

---

## 5단계: 로그아웃

### 📱 프론트엔드 역할

#### 5.1 로그아웃 요청
**책임**:
- 로그아웃 버튼 클릭 시 `/api/auth/logout` 호출
- localStorage에서 토큰 삭제
- 로그인 페이지로 리다이렉트

**구현 예시 (React)**:
```javascript
import { useNavigate } from 'react-router-dom';
import apiClient from './apiClient';

function LogoutButton() {
  const navigate = useNavigate();

  async function handleLogout() {
    try {
      // 1. 백엔드 로그아웃 API 호출 (Refresh Token 무효화)
      await apiClient.post('/api/auth/logout');

      // 2. localStorage에서 토큰 삭제
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');

      // 3. 로그인 페이지로 리다이렉트
      navigate('/login');

    } catch (error) {
      console.error('로그아웃 실패:', error);

      // 에러가 발생해도 로컬 토큰은 삭제
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      navigate('/login');
    }
  }

  return (
    <button onClick={handleLogout}>
      로그아웃
    </button>
  );
}
```

**HTTP 요청 예시**:
```http
POST /api/auth/logout HTTP/1.1
Host: localhost
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

### 🖥️ 백엔드 역할

#### 5.2 Refresh Token 무효화
**파일**:
- `src/main/java/com/example/oauth/domain/security/service/AuthService.java`
- `src/main/java/com/example/oauth/domain/security/controller/AuthController.java`

**책임**:
- 현재 로그인한 사용자의 Refresh Token을 DB에서 삭제
- Access Token은 stateless이므로 서버에서 무효화 불가 (만료까지 유효)

**Controller**:
```java
@PostMapping("/logout")
public ResponseEntity<LogoutResponse> logout(
        @AuthenticationPrincipal JwtTokenClaims claims) {

    log.info("POST /api/auth/logout - 로그아웃 요청, UserId: {}", claims.userId());

    authService.logout(claims.userId());

    return ResponseEntity.ok(new LogoutResponse("로그아웃 성공"));
}
```

**Service**:
```java
@Transactional
public void logout(Long userId) {
    log.info("로그아웃 처리 - UserId: {}", userId);

    // DB에서 Refresh Token 삭제
    refreshTokenRepository.deleteByUserId(userId);

    log.info("로그아웃 완료 - UserId: {}", userId);
}
```

**데이터베이스 작업**:
```sql
-- Refresh Token 삭제
DELETE FROM refresh_tokens WHERE user_id = 1;
```

**HTTP 응답 예시**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "message": "로그아웃 성공"
}
```

**중요 사항**:
- Access Token은 stateless JWT이므로 서버에서 무효화할 수 없음
- 만료 시간(10분)까지는 유효하게 사용 가능
- 보안이 중요한 경우 Access Token 만료 시간을 짧게 설정 (예: 5분)
- Refresh Token은 DB에서 삭제되므로 해당 토큰으로는 더 이상 Access Token 재발급 불가

---

## 에러 처리

### 📱 프론트엔드 에러 처리

#### 6.1 주요 에러 시나리오

**1. Access Token 만료 (401 Unauthorized)**
```javascript
// Axios 인터셉터에서 자동 처리 (4.1 참조)
// 1. Refresh Token으로 재발급 시도
// 2. 성공하면 원래 요청 재시도
// 3. 실패하면 로그인 페이지로 이동
```

**2. Refresh Token 만료 (401 Unauthorized)**
```javascript
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Refresh Token도 만료된 경우
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login?session_expired=true';
    }
    return Promise.reject(error);
  }
);
```

**3. 네트워크 에러**
```javascript
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!error.response) {
      // 네트워크 연결 실패
      alert('네트워크 연결을 확인해주세요.');
    }
    return Promise.reject(error);
  }
);
```

**4. OAuth2 로그인 실패**
```javascript
// URL: /login?error=true
const [searchParams] = useSearchParams();
const hasError = searchParams.get('error');

if (hasError) {
  alert('로그인에 실패했습니다. 다시 시도해주세요.');
}
```

---

### 🖥️ 백엔드 에러 처리

#### 6.2 예외 처리 클래스

**JwtAuthenticationException**:
```java
// src/main/java/com/example/oauth/domain/security/exception/JwtAuthenticationException.java
public class JwtAuthenticationException extends RuntimeException {
    public JwtAuthenticationException(String message) {
        super(message);
    }
}
```

**RefreshTokenException**:
```java
// src/main/java/com/example/oauth/domain/security/exception/RefreshTokenException.java
public class RefreshTokenException extends RuntimeException {
    public RefreshTokenException(String message) {
        super(message);
    }
}
```

#### 6.3 Global Exception Handler (권장)

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Refresh Token 관련 예외 처리
     */
    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenException(
            RefreshTokenException e) {

        log.warn("Refresh Token 오류: {}", e.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            "REFRESH_TOKEN_ERROR",
            e.getMessage()
        );

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(errorResponse);
    }

    /**
     * JWT 인증 관련 예외 처리
     */
    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleJwtAuthenticationException(
            JwtAuthenticationException e) {

        log.warn("JWT 인증 오류: {}", e.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            "JWT_AUTHENTICATION_ERROR",
            e.getMessage()
        );

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(errorResponse);
    }

    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("서버 오류 발생", e);

        ErrorResponse errorResponse = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "서버에서 오류가 발생했습니다."
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse);
    }

    public record ErrorResponse(
        String errorCode,
        String message
    ) {}
}
```

**에러 응답 예시**:
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "errorCode": "REFRESH_TOKEN_ERROR",
  "message": "만료된 Refresh Token입니다. 다시 로그인해주세요."
}
```

---

## 보안 고려사항

### 🔐 보안 모범 사례

#### 7.1 토큰 저장 위치

**localStorage vs sessionStorage vs Cookie**

| 저장소 | 장점 | 단점 | 권장 사용 |
|--------|------|------|-----------|
| **localStorage** | 브라우저 닫아도 유지 | XSS 공격에 취약 | 편의성 우선 시 |
| **sessionStorage** | 탭 닫으면 삭제 | XSS 공격에 취약, 영속성 없음 | 임시 데이터 |
| **HttpOnly Cookie** | XSS 공격 방어 | CSRF 공격 가능, SameSite 설정 필요 | 보안 우선 시 |

**현재 구현**: localStorage (프론트엔드 SPA 환경에 적합)

**보안 강화 방법**:
```javascript
// 1. Secure HttpOnly Cookie 사용 (백엔드에서 설정)
// - 프론트엔드에서 JavaScript로 접근 불가
// - HTTPS에서만 전송

// 2. XSS 방어
// - React의 경우 기본적으로 XSS 방어 (dangerouslySetInnerHTML 사용 지양)
// - 사용자 입력 sanitization

// 3. HTTPS 사용 (프로덕션 필수)
```

---

#### 7.2 Refresh Token Rotation

**현재 구현**:
- Access Token 재발급 시 Refresh Token도 함께 갱신
- 기존 Refresh Token은 DB에서 업데이트 (1회만 사용 가능)

**보안 효과**:
- Refresh Token 탈취 시 피해 최소화
- 탈취된 토큰은 1회만 사용 가능
- 정상 사용자가 재발급 시 탈취자의 토큰 무효화

**구현 코드** (AuthService.refresh()):
```java
// 새로운 Refresh Token 생성
JwtTokenDto newTokens = jwtTokenProvider.generateToken(...);

// 기존 Refresh Token을 새 토큰으로 교체 (DB UPDATE)
storedRefreshToken.updateToken(newTokens.refreshToken(), newExpiryDate);
```

---

#### 7.3 토큰 만료 시간 설정

**권장 설정**:
```yaml
jwt:
  access-token-expiration: 600000      # 10분 (짧게 설정)
  refresh-token-expiration: 604800000  # 7일 (길게 설정)
```

**설정 근거**:
- **Access Token**: 짧게 (5~15분)
  - 탈취 시 피해 최소화
  - Refresh Token으로 자동 재발급되므로 사용자 불편 없음

- **Refresh Token**: 길게 (7~30일)
  - 사용자가 자주 로그인하지 않아도 됨
  - DB에 저장되어 서버에서 무효화 가능

**보안 요구사항에 따른 조정**:
```yaml
# 보안 우선 (금융, 의료 등)
access-token-expiration: 300000   # 5분
refresh-token-expiration: 86400000 # 1일

# 편의성 우선 (일반 서비스)
access-token-expiration: 900000    # 15분
refresh-token-expiration: 2592000000 # 30일
```

---

#### 7.4 CORS 설정

**백엔드 CORS 설정** (프론트엔드가 다른 도메인인 경우):
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000") // React 개발 서버
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

**프로덕션 설정**:
```java
.allowedOrigins("https://yourdomain.com")
```

---

#### 7.5 HTTPS 사용 (프로덕션 필수)

**이유**:
- JWT 토큰이 네트워크를 통해 전송됨
- HTTP에서는 중간자 공격(MITM)으로 토큰 탈취 가능
- HTTPS는 TLS/SSL로 암호화된 통신 제공

**Nginx 설정** (docker-compose.yml):
```yaml
nginx:
  image: nginx:alpine
  ports:
    - "443:443"
  volumes:
    - ./nginx/nginx.conf:/etc/nginx/nginx.conf
    - ./nginx/ssl:/etc/nginx/ssl  # SSL 인증서
```

---

#### 7.6 SQL Injection 방어

**현재 구현**:
- JPA의 PreparedStatement 사용 (자동 방어)
- 사용자 입력을 직접 쿼리에 포함하지 않음

**안전한 코드**:
```java
// JPA Query Method (안전)
Optional<User> findByProviderAndProviderId(String provider, String providerId);

// @Query with named parameter (안전)
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);
```

**위험한 코드** (사용 금지):
```java
// 문자열 연결로 쿼리 생성 (위험 - SQL Injection 가능)
String query = "SELECT * FROM users WHERE email = '" + email + "'";
```

---

#### 7.7 XSS (Cross-Site Scripting) 방어

**프론트엔드 방어**:
```javascript
// React는 기본적으로 XSS 방어 (자동 이스케이핑)
<div>{user.name}</div>  // 안전: < > & 등이 자동 이스케이프

// 위험: HTML을 직접 렌더링할 때만 주의
<div dangerouslySetInnerHTML={{__html: userInput}} />  // 위험!

// 안전한 방법: DOMPurify 라이브러리 사용
import DOMPurify from 'dompurify';
<div dangerouslySetInnerHTML={{__html: DOMPurify.sanitize(userInput)}} />
```

**백엔드 응답 헤더 설정**:
```java
@Configuration
public class SecurityHeadersConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                   HttpServletResponse response,
                                   Object handler) {
                // XSS 방어 헤더
                response.setHeader("X-XSS-Protection", "1; mode=block");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                return true;
            }
        });
    }
}
```

---

## 📊 시퀀스 다이어그램

### 전체 인증 플로우

```mermaid
sequenceDiagram
    participant U as 사용자
    participant F as 프론트엔드
    participant B as 백엔드
    participant O as OAuth Provider
    participant DB as Database

    Note over U,DB: 1단계: 초기 로그인 (OAuth2)
    U->>F: 로그인 페이지 접속
    F->>U: 로그인 버튼 표시
    U->>F: Google 로그인 클릭
    F->>B: GET /oauth2/authorization/google
    B->>O: Redirect to Google 인증 페이지
    O->>U: 로그인 & 권한 동의 요청
    U->>O: 로그인 & 동의
    O->>B: Callback: /login/oauth2/code/google?code=...
    B->>O: Authorization Code → Access Token 교환
    O->>B: Access Token 발급
    B->>O: UserInfo API 호출
    O->>B: 사용자 정보 반환
    B->>DB: 사용자 저장/업데이트

    Note over U,DB: 2단계: JWT 토큰 발급
    B->>B: JWT Access Token + Refresh Token 생성
    B->>DB: Refresh Token 저장
    B->>F: Redirect: /oauth/callback?accessToken=...&refreshToken=...
    F->>F: localStorage에 토큰 저장
    F->>U: 메인 페이지로 이동

    Note over U,DB: 3단계: API 요청 (JWT 인증)
    U->>F: API 요청 (프로필 조회 등)
    F->>B: GET /api/user/profile<br/>Authorization: Bearer {accessToken}
    B->>B: JWT 토큰 검증
    B->>DB: 사용자 정보 조회
    DB->>B: 사용자 데이터 반환
    B->>F: 사용자 프로필 반환
    F->>U: 프로필 표시

    Note over U,DB: 4단계: Access Token 재발급
    U->>F: API 요청
    F->>B: GET /api/...<br/>Authorization: Bearer {만료된 accessToken}
    B->>F: 401 Unauthorized
    F->>B: POST /api/auth/refresh<br/>{refreshToken}
    B->>DB: Refresh Token 조회 및 검증
    DB->>B: Refresh Token 데이터
    B->>B: 새 Access Token + Refresh Token 생성
    B->>DB: 새 Refresh Token으로 업데이트
    B->>F: 새 토큰 반환
    F->>F: localStorage에 새 토큰 저장
    F->>B: 원래 API 재요청<br/>Authorization: Bearer {새 accessToken}
    B->>F: API 응답
    F->>U: 결과 표시

    Note over U,DB: 5단계: 로그아웃
    U->>F: 로그아웃 버튼 클릭
    F->>B: POST /api/auth/logout<br/>Authorization: Bearer {accessToken}
    B->>DB: Refresh Token 삭제
    B->>F: 로그아웃 성공
    F->>F: localStorage에서 토큰 삭제
    F->>U: 로그인 페이지로 이동
```

---

## 📝 체크리스트

### 프론트엔드 구현 체크리스트

- [ ] OAuth 콜백 페이지 구현 (`/oauth/callback`)
- [ ] localStorage에 토큰 저장 로직 구현
- [ ] Axios 요청 인터셉터: Authorization 헤더 자동 추가
- [ ] Axios 응답 인터셉터: 401 에러 시 토큰 재발급
- [ ] 로그아웃 기능 구현
- [ ] 토큰 없이 보호된 페이지 접근 시 로그인 페이지로 리다이렉트
- [ ] XSS 방어: 사용자 입력 sanitization

### 백엔드 구현 체크리스트

- [x] OAuth2 클라이언트 설정 (application.yml)
- [x] CustomOAuth2UserService 구현
- [x] OAuth2AuthenticationSuccessHandler 구현
- [x] JwtTokenProvider 구현
- [x] JwtAuthenticationFilter 구현
- [x] RefreshToken Entity 및 Repository 구현
- [x] AuthService 구현 (refresh, logout)
- [x] AuthController 구현 (/api/auth/refresh, /api/auth/logout)
- [x] SecurityConfig 업데이트
- [ ] GlobalExceptionHandler 구현
- [ ] CORS 설정
- [ ] HTTPS 설정 (프로덕션)
- [ ] 보안 헤더 설정

---

## 🚀 다음 단계

1. **프론트엔드 SPA 개발**
   - React, Vue, Angular 중 선택
   - OAuth 콜백 페이지 구현
   - API 클라이언트 설정 (Axios 인터셉터)

2. **GlobalExceptionHandler 구현**
   - 일관된 에러 응답 형식
   - 로깅 및 모니터링

3. **프로덕션 배포**
   - HTTPS 설정 (Let's Encrypt)
   - 환경 변수 관리 (.env 파일)
   - 데이터베이스 변경 (H2 → PostgreSQL/MySQL)
   - 로깅 및 모니터링 (ELK, Prometheus)

4. **추가 Provider 지원**
   - Naver OAuth2 추가
   - GitHub OAuth2 추가
   - Factory Pattern으로 쉽게 확장 가능

---

## 📚 참고 자료

- [Spring Security OAuth2 Client 공식 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [JWT 공식 사이트](https://jwt.io/)
- [OAuth 2.0 RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)

---

**문서 버전**: 1.0
**최종 수정일**: 2024-01-07
**작성자**: Spring Boot OAuth2 + JWT Template
