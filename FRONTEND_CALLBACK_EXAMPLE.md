# 프론트엔드 OAuth 콜백 페이지 구현 가이드

## 📋 개요

백엔드 OAuth2AuthenticationSuccessHandler가 **HTML Form Auto-Submit** 방식으로 Access Token을 POST body로 전달합니다.

### 토큰 전달 방식
- **Access Token**: POST body (form data) - 보안 강화
- **Refresh Token**: HttpOnly Cookie - XSS 방어

### 보안 이점
✅ Access Token이 URL에 노출되지 않음
✅ 브라우저 히스토리에 토큰 저장 안 됨
✅ 서버 access log에 토큰 기록 안 됨
✅ Referer 헤더를 통한 유출 방지

---

## 🎯 백엔드 동작 방식

OAuth2 로그인 성공 시:

```html
<!-- 백엔드가 반환하는 HTML 페이지 -->
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>OAuth2 로그인 성공</title>
</head>
<body>
    <div style="text-align: center; padding: 50px;">
        <h2>로그인 성공!</h2>
        <p>잠시만 기다려주세요...</p>
    </div>

    <!-- Access Token을 POST body로 전송하는 Form -->
    <form id="tokenForm" action="http://localhost/oauth/callback" method="POST" style="display: none;">
        <input type="hidden" name="accessToken" value="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...">
    </form>

    <script>
        // 페이지 로드 즉시 자동으로 form submit
        document.getElementById('tokenForm').submit();
    </script>
</body>
</html>
```

이 페이지가 자동으로 `/oauth/callback`에 POST 요청을 보냅니다:
```http
POST /oauth/callback HTTP/1.1
Host: localhost
Content-Type: application/x-www-form-urlencoded
Cookie: refresh_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

accessToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## 💻 프론트엔드 구현

### 1. React (권장)

#### 방법 1: 라우터 설정 (추천)

```jsx
// App.jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import OAuthCallback from './pages/OAuthCallback';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/oauth/callback" element={<OAuthCallback />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

```jsx
// pages/OAuthCallback.jsx
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

function OAuthCallback() {
  const navigate = useNavigate();

  useEffect(() => {
    // POST 요청으로 전달된 accessToken 처리
    // 이 컴포넌트가 렌더링될 때 이미 POST 요청이 완료되어 페이지가 로드됨

    // URL에서 form data 추출하는 대신, useSearchParams나 useLocation을 사용할 수 없음
    // POST body는 JavaScript로 직접 접근 불가능
    // 따라서 서버 사이드 렌더링을 사용하거나, 다른 방법 필요
  }, [navigate]);

  return (
    <div style={{ textAlign: 'center', padding: '50px' }}>
      <h2>로그인 처리 중...</h2>
      <p>잠시만 기다려주세요.</p>
    </div>
  );
}

export default OAuthCallback;
```

**❌ 문제점**: React는 클라이언트 사이드 렌더링이므로 POST body에 직접 접근할 수 없습니다.

#### 방법 2: Express.js 중간 서버 사용 (실무 권장)

프론트엔드 서버(Express.js)를 두고 POST 요청을 받은 후 클라이언트로 리다이렉트:

```javascript
// server.js (Express.js)
const express = require('express');
const app = express();

app.use(express.urlencoded({ extended: true }));

// OAuth 콜백 POST 요청 처리
app.post('/oauth/callback', (req, res) => {
  const { accessToken } = req.body;

  // Access Token을 쿼리 파라미터로 변환하여 React 앱으로 리다이렉트
  // (또는 세션에 저장 후 React 앱에서 API로 조회)
  res.redirect(`/oauth/callback-client?token=${accessToken}`);
});

// React 앱 서빙
app.use(express.static('build'));

app.listen(3000, () => {
  console.log('프론트엔드 서버 실행: http://localhost:3000');
});
```

```jsx
// pages/OAuthCallback.jsx (React)
import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

function OAuthCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const accessToken = searchParams.get('token');

    if (accessToken) {
      // localStorage에 토큰 저장
      localStorage.setItem('accessToken', accessToken);

      // Refresh Token은 이미 Cookie에 있음 (백엔드에서 설정)
      console.log('Access Token 저장 완료');

      // 대시보드로 이동
      navigate('/dashboard');
    } else {
      // 토큰이 없으면 로그인 실패
      navigate('/login?error=true');
    }
  }, [searchParams, navigate]);

  return (
    <div style={{ textAlign: 'center', padding: '50px' }}>
      <h2>로그인 처리 중...</h2>
      <p>잠시만 기다려주세요.</p>
    </div>
  );
}

export default OAuthCallback;
```

---

### 2. Next.js (SSR/SSG)

Next.js는 서버 사이드에서 POST 요청을 처리할 수 있습니다.

```typescript
// pages/oauth/callback.tsx
import { GetServerSideProps } from 'next';
import { useRouter } from 'next/router';
import { useEffect } from 'react';

interface CallbackProps {
  accessToken: string | null;
  error: string | null;
}

export default function OAuthCallback({ accessToken, error }: CallbackProps) {
  const router = useRouter();

  useEffect(() => {
    if (accessToken) {
      // localStorage에 Access Token 저장
      localStorage.setItem('accessToken', accessToken);

      // Refresh Token은 이미 Cookie에 있음 (백엔드에서 설정)
      console.log('로그인 성공');

      // 대시보드로 이동
      router.push('/dashboard');
    } else if (error) {
      // 로그인 실패
      router.push('/login?error=true');
    }
  }, [accessToken, error, router]);

  return (
    <div style={{ textAlign: 'center', padding: '50px' }}>
      <h2>로그인 처리 중...</h2>
      <p>잠시만 기다려주세요.</p>
    </div>
  );
}

// 서버 사이드에서 POST 요청 처리
export const getServerSideProps: GetServerSideProps = async (context) => {
  const { req } = context;

  // POST 요청인 경우
  if (req.method === 'POST') {
    // body-parser 사용하여 POST body 파싱
    // Next.js는 기본적으로 body parsing을 하지 않으므로 설정 필요
    const body = await parseBody(req);

    return {
      props: {
        accessToken: body.accessToken || null,
        error: null,
      },
    };
  }

  // GET 요청인 경우 (에러 처리 등)
  return {
    props: {
      accessToken: null,
      error: context.query.error || null,
    },
  };
};

// POST body 파싱 헬퍼 함수
async function parseBody(req: any): Promise<any> {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk: any) => {
      body += chunk.toString();
    });
    req.on('end', () => {
      const parsed = new URLSearchParams(body);
      const result: any = {};
      for (const [key, value] of parsed.entries()) {
        result[key] = value;
      }
      resolve(result);
    });
    req.on('error', reject);
  });
}
```

**Next.js API Routes 활용 (더 간단한 방법)**:

```typescript
// pages/api/oauth/callback.ts
import type { NextApiRequest, NextApiResponse } from 'next';

export default function handler(req: NextApiRequest, res: NextApiResponse) {
  if (req.method === 'POST') {
    const { accessToken } = req.body;

    // Access Token을 쿼리 파라미터로 변환하여 클라이언트 페이지로 리다이렉트
    res.redirect(302, `/oauth/callback-client?token=${accessToken}`);
  } else {
    res.status(405).json({ message: 'Method not allowed' });
  }
}

// API Routes는 기본적으로 body parsing을 지원
export const config = {
  api: {
    bodyParser: {
      sizeLimit: '1mb',
    },
  },
};
```

```typescript
// pages/oauth/callback-client.tsx
import { useRouter } from 'next/router';
import { useEffect } from 'react';

export default function OAuthCallbackClient() {
  const router = useRouter();
  const { token } = router.query;

  useEffect(() => {
    if (token) {
      localStorage.setItem('accessToken', token as string);
      router.push('/dashboard');
    }
  }, [token, router]);

  return (
    <div style={{ textAlign: 'center', padding: '50px' }}>
      <h2>로그인 처리 중...</h2>
      <p>잠시만 기다려주세요.</p>
    </div>
  );
}
```

---

### 3. Vue.js

#### Vue Router 설정

```javascript
// router/index.js
import { createRouter, createWebHistory } from 'vue-router';
import OAuthCallback from '@/views/OAuthCallback.vue';
import Dashboard from '@/views/Dashboard.vue';
import Login from '@/views/Login.vue';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
  },
  {
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: OAuthCallback,
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard,
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
```

**문제점**: Vue도 React와 마찬가지로 클라이언트 사이드이므로 POST body에 직접 접근 불가.

#### 해결 방법: Nuxt.js 사용 (SSR)

```vue
<!-- pages/oauth/callback.vue -->
<template>
  <div style="text-align: center; padding: 50px;">
    <h2>로그인 처리 중...</h2>
    <p>잠시만 기다려주세요.</p>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { onMounted } from 'vue';

const router = useRouter();
const props = defineProps<{
  accessToken: string | null;
}>();

onMounted(() => {
  if (props.accessToken) {
    localStorage.setItem('accessToken', props.accessToken);
    router.push('/dashboard');
  } else {
    router.push('/login?error=true');
  }
});
</script>

<script lang="ts">
// 서버 사이드에서 POST 요청 처리
export async function getServerSideProps(context: any) {
  const { req } = context;

  if (req.method === 'POST') {
    // POST body 파싱
    const body = await parseBody(req);

    return {
      props: {
        accessToken: body.accessToken || null,
      },
    };
  }

  return {
    props: {
      accessToken: null,
    },
  };
}

async function parseBody(req: any): Promise<any> {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', (chunk: any) => {
      body += chunk.toString();
    });
    req.on('end', () => {
      const parsed = new URLSearchParams(body);
      const result: any = {};
      for (const [key, value] of parsed.entries()) {
        result[key] = value;
      }
      resolve(result);
    });
  });
}
</script>
```

---

### 4. Vanilla JavaScript (HTML + JS)

가장 간단한 방법: 서버 사이드 스크립트 (PHP, Node.js 등)를 사용하여 POST 요청 처리

#### Node.js + Express 예시

```javascript
// server.js
const express = require('express');
const path = require('path');
const app = express();

app.use(express.urlencoded({ extended: true }));
app.use(express.static('public'));

// OAuth 콜백 POST 요청 처리
app.post('/oauth/callback', (req, res) => {
  const { accessToken } = req.body;

  // HTML 페이지에 토큰을 JavaScript 변수로 삽입하여 반환
  res.send(`
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <title>로그인 처리 중</title>
    </head>
    <body>
      <div style="text-align: center; padding: 50px; font-family: Arial, sans-serif;">
        <h2>로그인 성공!</h2>
        <p>잠시만 기다려주세요...</p>
      </div>

      <script>
        // Access Token을 localStorage에 저장
        localStorage.setItem('accessToken', '${accessToken}');

        // Refresh Token은 이미 Cookie에 있음
        console.log('로그인 완료');

        // 대시보드로 리다이렉트
        window.location.href = '/dashboard.html';
      </script>
    </body>
    </html>
  `);
});

app.listen(3000, () => {
  console.log('서버 실행: http://localhost:3000');
});
```

---

## 🔧 백엔드 설정 (application.yml)

```yaml
oauth2:
  # 프론트엔드 OAuth 콜백 URL
  # POST 요청을 받을 수 있는 엔드포인트여야 함
  redirect-uri: http://localhost:3000/oauth/callback
```

### 개발 환경별 설정

**로컬 개발 (React/Vue SPA + Express 서버)**:
```yaml
redirect-uri: http://localhost:3000/oauth/callback
```

**Next.js (API Routes 사용)**:
```yaml
redirect-uri: http://localhost:3000/api/oauth/callback
```

**프로덕션**:
```yaml
redirect-uri: https://yourdomain.com/oauth/callback
```

---

## 🔒 보안 고려사항

### 1. HTTPS 사용 (프로덕션 필수)

```yaml
# application.yml (프로덕션)
oauth2:
  redirect-uri: https://yourdomain.com/oauth/callback
```

백엔드에서 Cookie 설정 시:
```java
cookie.setSecure(true); // HTTPS에서만 전송
```

### 2. CORS 설정

프론트엔드와 백엔드 도메인이 다른 경우:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000") // 프론트엔드 도메인
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true) // Cookie 전송 허용
            .maxAge(3600);
    }
}
```

### 3. SameSite Cookie 설정

```java
// OAuth2AuthenticationSuccessHandler.java
private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken, Long refreshTokenExpiration) {
    Cookie cookie = new Cookie("refresh_token", refreshToken);
    cookie.setHttpOnly(true);
    cookie.setSecure(true); // HTTPS에서만 전송
    cookie.setPath("/");
    cookie.setMaxAge((int) (refreshTokenExpiration / 1000));

    // SameSite 설정 (CSRF 방어)
    response.addHeader("Set-Cookie",
        String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; Secure; SameSite=Lax",
            cookie.getName(), cookie.getValue(), cookie.getMaxAge()));
}
```

---

## 📝 프론트엔드 체크리스트

- [ ] OAuth 콜백 엔드포인트 구현 (`/oauth/callback` POST 요청 처리)
- [ ] Access Token을 localStorage에 저장
- [ ] Refresh Token은 Cookie에 자동 저장됨 (백엔드에서 설정)
- [ ] 로그인 성공 시 대시보드로 리다이렉트
- [ ] 로그인 실패 시 에러 처리
- [ ] CORS 설정 (프론트엔드 ↔ 백엔드 도메인이 다른 경우)
- [ ] HTTPS 적용 (프로덕션)

---

## 🚀 권장 구조

### 개발 환경
```
프론트엔드 (React/Vue): http://localhost:3000
  ├─ Express.js 서버 (POST 처리)
  └─ OAuth 콜백: POST http://localhost:3000/oauth/callback

백엔드 (Spring Boot): http://localhost:8080
```

### 프로덕션 환경
```
프론트엔드: https://yourdomain.com
  ├─ Next.js/Nuxt.js (SSR)
  └─ OAuth 콜백: POST https://yourdomain.com/oauth/callback

백엔드: https://api.yourdomain.com
```

---

## 💡 추천 방법

1. **Next.js 사용** (SSR 지원, API Routes 활용)
2. **Nuxt.js 사용** (Vue 진영)
3. **Express.js 중간 서버** (React/Vue SPA)

이 방법들은 POST 요청을 서버 사이드에서 처리하여 Access Token을 안전하게 전달할 수 있습니다.

---

**문서 버전**: 1.0
**최종 수정일**: 2024-01-07
