package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.User;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.JwtUtil;
import com.eduexam.security.LoginUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Data
    static class RegisterRequest {
        private String username;
        private String email;
        private String password;
        private String realName;
        private String className;
        private String role = "student";
    }

    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    static class ProfileUpdateRequest {
        private String realName;
        private String className;
    }

    @Data
    static class PasswordChangeRequest {
        private String oldPassword;
        private String newPassword;
    }

    private Map<String, Object> buildTokenResponse(User user) {
        String token = jwtUtil.generateToken(user.getId());
        Map<String, Object> userMap = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "real_name", user.getRealName() != null ? user.getRealName() : "",
                "class_name", user.getClassName() != null ? user.getClassName() : "",
                "is_active", user.getIsActive() != null ? user.getIsActive() : true
        );
        return Map.of("access_token", token, "user", userMap);
    }

    private Map<String, Object> userToMap(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "real_name", user.getRealName() != null ? user.getRealName() : "",
                "class_name", user.getClassName() != null ? user.getClassName() : "",
                "is_active", user.getIsActive() != null ? user.getIsActive() : true
        );
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest req) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) > 0) {
            throw AppException.badRequest("用户名已存在");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail())) > 0) {
            throw AppException.badRequest("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setHashedPassword(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setClassName(req.getClassName());
        user.setRole(req.getRole() != null ? req.getRole() : "student");
        user.setIsActive(true);
        userMapper.insert(user);
        return buildTokenResponse(user);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getHashedPassword())) {
            throw AppException.unauthorized("用户名或密码错误");
        }
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw AppException.forbidden("账号已被禁用");
        }
        return buildTokenResponse(user);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal LoginUser loginUser) {
        return userToMap(loginUser.getUser());
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody ProfileUpdateRequest req,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        User user = loginUser.getUser();
        if (req.getRealName() != null) user.setRealName(req.getRealName());
        if (req.getClassName() != null) user.setClassName(req.getClassName());
        userMapper.updateById(user);
        User updated = userMapper.selectById(user.getId());
        return userToMap(updated);
    }

    @PutMapping("/password")
    public Map<String, Boolean> changePassword(@RequestBody PasswordChangeRequest req,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        User user = loginUser.getUser();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getHashedPassword())) {
            throw AppException.badRequest("原密码不正确");
        }
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            throw AppException.badRequest("新密码不能少于6位");
        }
        user.setHashedPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        return Map.of("ok", true);
    }
}
