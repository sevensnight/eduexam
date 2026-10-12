package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.User;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.AuthRateLimiter;
import com.eduexam.security.JwtUtil;
import com.eduexam.security.LoginUser;
import com.eduexam.security.RolePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthRateLimiter authRateLimiter;

    @Data
    static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 20, message = "用户名长度需为 4-20 位")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、点、下划线或连字符")
        private String username;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 位")
        private String email;

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度需为 8-72 位")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "密码必须同时包含字母和数字")
        private String password;

        @Size(max = 64, message = "真实姓名长度不能超过 64 位")
        private String realName;

    }

    @Data
    static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过 64 位")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码长度不能超过 72 位")
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
        @Size(max = 72, message = "原密码长度不能超过 72 位")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度需为 8-72 位")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "新密码必须同时包含字母和数字")
        private String newPassword;
    }

    private Map<String, Object> buildTokenResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getTokenVersion());
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
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req,
                                        HttpServletRequest request) {
        authRateLimiter.consumeRegistration(request.getRemoteAddr());
        String username = req.getUsername().strip();
        String email = req.getEmail().strip().toLowerCase(Locale.ROOT);
        validateBcryptPasswordLength(req.getPassword());
        if (req.getPassword().equalsIgnoreCase(username) || req.getPassword().equalsIgnoreCase(email))
            throw AppException.badRequest("密码不能与用户名或邮箱相同");
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
        user.setRealName(req.getRealName() != null ? req.getRealName().strip() : null);
        // Class membership is assigned by an administrator.  Never trust a
        // self-reported class because it is used for exam eligibility checks.
        // Public registration must never be able to create privileged accounts.
        // Teachers and administrators are created by an authenticated administrator.
        user.setRole("student");
        user.setIsActive(true);
        user.setTokenVersion(0);
        userMapper.insert(user);
        return buildTokenResponse(user);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req,
                                     HttpServletRequest request) {
        String username = req.getUsername().strip();
        String remoteAddress = request.getRemoteAddr();
        try (AuthRateLimiter.LoginPermit ignored = authRateLimiter.acquireLoginPermit(remoteAddress, username)) {
            if (!fitsBcrypt(req.getPassword())) {
                authRateLimiter.recordLoginFailure(remoteAddress, username);
                throw AppException.unauthorized("用户名或密码错误");
            }
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user == null || !passwordEncoder.matches(req.getPassword(), user.getHashedPassword())) {
                authRateLimiter.recordLoginFailure(remoteAddress, username);
                throw AppException.unauthorized("用户名或密码错误");
            }
            if (Boolean.FALSE.equals(user.getIsActive())) {
                authRateLimiter.recordLoginFailure(remoteAddress, username);
                throw AppException.forbidden("账号已被禁用");
            }
            if (!RolePolicy.isSupported(user.getRole()))
                throw AppException.forbidden("账号角色配置无效，请联系管理员");
            authRateLimiter.recordLoginSuccess(remoteAddress, username);
            return buildTokenResponse(user);
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal LoginUser loginUser) {
        return userToMap(loginUser.getUser());
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@Valid @RequestBody ProfileUpdateRequest req,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        User user = loginUser.getUser();
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, user.getId());
        boolean changed = false;
        if (req.getRealName() != null) {
            update.set(User::getRealName, req.getRealName().strip());
            changed = true;
        }
        if (req.getClassName() != null) {
            String requestedClass = req.getClassName().strip();
            String currentClass = user.getClassName() != null ? user.getClassName().strip() : "";
            if ("student".equals(user.getRole())) {
                if (!java.util.Objects.equals(requestedClass, currentClass))
                    throw AppException.forbidden("学生班级由管理员维护");
            } else {
                update.set(User::getClassName, requestedClass.isEmpty() ? null : requestedClass);
                changed = true;
            }
        }
        if (changed) userMapper.update(null, update);
        User updated = userMapper.selectById(user.getId());
        return userToMap(updated);
    }

    @PutMapping("/password")
    @Transactional
    public Map<String, Object> changePassword(@Valid @RequestBody PasswordChangeRequest req,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        validateBcryptPasswordLength(req.getOldPassword());
        validateBcryptPasswordLength(req.getNewPassword());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, loginUser.getUserId()).last("FOR UPDATE"));
        if (user == null || !Boolean.TRUE.equals(user.getIsActive()))
            throw AppException.unauthorized("账号不存在或已被禁用");
        if (!passwordEncoder.matches(req.getOldPassword(), user.getHashedPassword())) {
            throw AppException.badRequest("原密码不正确");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getHashedPassword()))
            throw AppException.badRequest("新密码不能与原密码相同");
        if (req.getNewPassword().equalsIgnoreCase(user.getUsername())
                || req.getNewPassword().equalsIgnoreCase(user.getEmail()))
            throw AppException.badRequest("新密码不能与用户名或邮箱相同");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getHashedPassword, passwordEncoder.encode(req.getNewPassword()))
                .setSql("token_version = token_version + 1"));
        User updated = userMapper.selectById(user.getId());
        return buildTokenResponse(updated);
    }

    private void validateBcryptPasswordLength(String password) {
        if (!fitsBcrypt(password))
            throw AppException.badRequest("密码的 UTF-8 编码不能超过 72 字节");
    }

    private boolean fitsBcrypt(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_PASSWORD_BYTES;
    }
}
