package com.example.oauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security OAuth2 Configuration
 *
 * Role:
 * - Spring Security의 보안 필터 체인을 구성하여 OAuth2 로그인 활성화
 * - 인증되지 않은 사용자는 로그인 페이지로 리다이렉트
 * - Google OAuth2 Provider를 통한 소셜 로그인 제공
 *
 * Complexity/Rationale:
 * 1. OAuth2 Login Flow:
 *    - 사용자가 보호된 리소스에 접근 시도
 *    - Spring Security가 미인증 사용자를 /login으로 리다이렉트
 *    - 사용자가 "Google로 로그인" 클릭
 *    - Spring이 Google OAuth2 인가 서버로 리다이렉트
 *    - 사용자가 Google에서 인증 및 권한 동의
 *    - Google이 콜백 URL(http://localhost:80/login/oauth2/code/google)로 인가 코드 전달
 *    - Spring이 인가 코드로 액세스 토큰 교환
 *    - Spring이 액세스 토큰으로 사용자 정보 조회 (UserInfo Endpoint)
 *    - Spring Security가 사용자를 인증 완료 처리 (세션 생성)
 *
 * 2. Nginx Proxy Environment:
 *    - Nginx가 X-Forwarded-* 헤더를 전달하고, application.yml의
 *      forward-headers-strategy: framework 설정으로 Spring이 이를 인식
 *    - OAuth2 redirect URI 생성 시 클라이언트가 접근한 주소(http://localhost:80) 기준으로 생성
 *    - 이를 통해 Google OAuth2 Console에 등록한 redirect URI와 정확히 일치
 *
 * 3. CSRF Protection:
 *    - OAuth2 로그인 시 CSRF 보호는 기본적으로 활성화되어 있음
 *    - state 파라미터를 통해 CSRF 공격 방지
 *
 * 4. Session Management:
 *    - 별도 설정이 없으므로 기본 인메모리 세션 사용
 *    - 프로덕션 환경에서는 Redis 등 외부 세션 저장소 사용 권장
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Security Filter Chain 구성
     *
     * Role:
     * - HTTP 요청에 대한 보안 규칙 정의
     * - OAuth2 로그인 엔드포인트 및 커스텀 로그인 페이지 설정
     *
     * @param http HttpSecurity 빌더
     * @return SecurityFilterChain 보안 필터 체인
     * @throws Exception 설정 중 발생하는 예외
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 권한 부여 규칙 설정
            .authorizeHttpRequests(authorize -> authorize
                // /login 페이지는 인증 없이 접근 가능 (무한 리다이렉트 방지)
                .requestMatchers("/login").permitAll()

                // 정적 리소스(CSS, JS, 이미지 등)에 대한 접근 허용 (필요시)
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()

                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )

            // OAuth2 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                // 커스텀 로그인 페이지 경로 지정
                // Role: 사용자가 직접 만든 로그인 UI를 제공하여 브랜딩 및 UX 개선
                // Complexity:
                //   - 기본 Spring Security 로그인 페이지 대신 /login으로 리다이렉트
                //   - 해당 페이지에서 "Google로 로그인" 버튼 제공
                //   - 실제 OAuth2 인증 시작점: /oauth2/authorization/google (자동 생성됨)
                .loginPage("/login")

                // OAuth2 로그인 성공 후 리다이렉트할 기본 경로
                // Role: 로그인 성공 시 사용자를 메인 페이지로 이동
                // Complexity:
                //   - 명시적으로 지정하지 않으면 이전 요청 페이지로 리다이렉트
                //   - 여기서는 명시적으로 홈("/")으로 설정
                .defaultSuccessUrl("/", true)

                // OAuth2 로그인 실패 시 리다이렉트할 경로
                // Role: 인증 실패 또는 권한 거부 시 에러 처리
                .failureUrl("/login?error=true")
            );

        return http.build();
    }
}
