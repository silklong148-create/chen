package com.mysqladmin.security;

import com.mysqladmin.api.ApiResponse;
import com.mysqladmin.auth.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper;
    public LoginInterceptor(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (request.getSession(false) != null && request.getSession(false).getAttribute(AuthService.SESSION_USER) != null) return true;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType(MediaType.APPLICATION_JSON_VALUE); response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("请先登录 Mysqladmin")); return false;
    }
}
