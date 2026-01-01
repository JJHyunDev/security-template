package com.example.oauth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home & Login Controller
 *
 * Role:
 * - 메인 페이지 및 로그인 페이지를 처리하는 컨트롤러
 * - OAuth2 인증된 사용자 정보를 뷰로 전달
 *
 * Complexity/Rationale:
 * 1. OAuth2User Principal:
 *    - Spring Security OAuth2가 인증 완료 후 OAuth2User 객체를 생성
 *    - 이 객체는 Google UserInfo API에서 받은 사용자 정보를 담고 있음
 *    - @AuthenticationPrincipal 어노테이션으로 현재 인증된 사용자 정보에 접근
 *
 * 2. Attributes 구조:
 *    - OAuth2User.getAttributes()는 Map<String, Object> 형태
 *    - Google Provider의 경우 주요 필드:
 *      * sub: Google 사용자 고유 ID
 *      * name: 사용자 이름
 *      * email: 이메일 주소
 *      * picture: 프로필 이미지 URL
 *      * email_verified: 이메일 인증 여부
 *
 * 3. View Rendering:
 *    - Thymeleaf 템플릿 엔진을 사용하여 사용자 정보를 HTML로 렌더링
 *    - Model 객체에 사용자 정보를 담아 뷰로 전달
 */
@Controller
public class HomeController {

    /**
     * 메인 페이지 (인증 필요)
     *
     * Role:
     * - 인증된 사용자의 메인 페이지를 제공
     * - Google OAuth2로부터 받은 사용자 정보를 화면에 표시
     *
     * Complexity/Rationale:
     * - @AuthenticationPrincipal로 현재 인증된 OAuth2User 객체를 주입받음
     * - principal이 null이면 Spring Security가 자동으로 로그인 페이지로 리다이렉트
     *   (SecurityConfig에서 anyRequest().authenticated() 설정)
     * - OAuth2User의 attributes에서 사용자 정보 추출하여 Model에 추가
     * - Thymeleaf가 src/main/resources/templates/home.html을 렌더링
     *
     * @param principal OAuth2 인증된 사용자 정보 (Google UserInfo)
     * @param model 뷰로 전달할 데이터 모델
     * @return 뷰 이름 (home.html)
     */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal != null) {
            // Google OAuth2 사용자 정보 추출
            String name = principal.getAttribute("name");
            String email = principal.getAttribute("email");
            String picture = principal.getAttribute("picture");

            // 모델에 사용자 정보 추가 (Thymeleaf 템플릿에서 사용)
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            model.addAttribute("picture", picture);
        }

        return "home";
    }

    /**
     * 커스텀 로그인 페이지
     *
     * Role:
     * - Google OAuth2 로그인 버튼이 있는 커스텀 로그인 페이지 제공
     *
     * Complexity/Rationale:
     * - SecurityConfig에서 .loginPage("/login")으로 설정한 경로와 매핑
     * - 이 페이지는 인증 없이 접근 가능 (.requestMatchers("/login").permitAll())
     * - 실제 OAuth2 인증 시작점: /oauth2/authorization/google (Spring Security가 자동 생성)
     * - 로그인 페이지에서 "Google로 로그인" 링크를 /oauth2/authorization/google로 설정
     *
     * @return 뷰 이름 (login.html)
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
