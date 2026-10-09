package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.User;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.JwtUtil;
import com.eduexam.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Data
    static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 20, message = "用户名长度需为 4-20 位")
        private String username;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 位")
        private String email;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度需为 6-128 位")
        private String password;

        @Size(max = 64, message = "真实姓名长度不能超过 64 位")
        private String realName;

        @Size(max = 64, message = "班级长度不能超过 64 位")
        private String className;
    }

    @Data
    static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    static class ProfileUpdateRequest {
        @Size(max = 64, message = "真实姓名长度不能超过 64 位")
        private String realName;

        @Size(max = 64, message = "班级长度不能超过 64 位")
        private String className;
    }

    @Data
    static class PasswordChangeRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度需为 6-128 位")
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
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req) {
        String username = req.getUsername().strip();
        String email = req.getEmail().strip().toLowerCase(Locale.ROOT);
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw AppException.badRequest("用户名已存在");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw AppException.badRequest("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setHashedPassword(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setClassName(req.getClassName());
        // Public registration must never be able to create privileged accounts.
        // Teachers and administrators are created by an authenticated administrator.
        user.setRole("student");
        user.setIsActive(true);
        userMapper.insert(user);
        return buildTokenResponse(user);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername().strip()));
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
    public Map<String, Object> updateProfile(@Valid @RequestBody ProfileUpdateRequest req,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        User user = loginUser.getUser();
        if (req.getRealName() != null) user.setRealName(req.getRealName());
        if (req.getClassName() != null) user.setClassName(req.getClassName());
        userMapper.updateById(user);
        User updated = userMapper.selectById(user.getId());
        return userToMap(updated);
    }

    @PutMapping("/password")
    public Map<String, Boolean> changePassword(@Valid @RequestBody PasswordChangeRequest req,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        User user = loginUser.getUser();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getHashedPassword())) {
            throw AppException.badRequest("原密码不正确");
        }
        user.setHashedPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        return Map.of("ok", true);
    }
}
