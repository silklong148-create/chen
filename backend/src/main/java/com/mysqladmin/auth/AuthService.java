package com.mysqladmin.auth;

import com.mysqladmin.api.ApiException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Service
public class AuthService {
    public static final String SESSION_USER = "MYSQLADMIN_USER";
    private final AppUserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AuthService(AppUserRepository users) { this.users = users; }

    public Map<String, Object> register(AuthRequest request, HttpSession session) {
        String username = request.username().trim();
        if (users.existsByUsername(username)) throw new ApiException("该用户名已被注册");
        AppUser user = users.save(new AppUser(username, encoder.encode(request.password())));
        loginSession(user, session);
        return profile(user);
    }
    public Map<String, Object> login(AuthRequest request, HttpSession session) {
        AppUser user = users.findByUsername(request.username().trim()).orElseThrow(() -> new ApiException("用户名或密码错误"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) throw new ApiException("用户名或密码错误");
        loginSession(user, session); return profile(user);
    }
    public Map<String, Object> current(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER);
        return value instanceof Map<?, ?> map ? Map.of("authenticated", true, "username", map.get("username")) : Map.of("authenticated", false);
    }
    public void logout(HttpSession session) { session.invalidate(); }
    private void loginSession(AppUser user, HttpSession session) { session.setAttribute(SESSION_USER, profile(user)); }
    private Map<String, Object> profile(AppUser user) { return Map.of("id", user.getId(), "username", user.getUsername()); }
}
