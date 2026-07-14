package com.mysqladmin.controller;

import com.mysqladmin.api.ApiResponse;
import com.mysqladmin.auth.AuthRequest;
import com.mysqladmin.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/register") @Operation(summary = "注册 Mysqladmin 用户") public ApiResponse<Map<String, Object>> register(@Valid @RequestBody AuthRequest request, HttpSession session) { return ApiResponse.ok("注册成功，已进入工作台", auth.register(request, session)); }
    @PostMapping("/login") @Operation(summary = "登录 Mysqladmin") public ApiResponse<Map<String, Object>> login(@Valid @RequestBody AuthRequest request, HttpSession session) { return ApiResponse.ok("登录成功", auth.login(request, session)); }
    @GetMapping("/session") public ApiResponse<Map<String, Object>> session(HttpSession session) { return ApiResponse.ok(auth.current(session)); }
    @PostMapping("/logout") public ApiResponse<Void> logout(HttpSession session) { auth.logout(session); return ApiResponse.ok("已退出登录", null); }
}
