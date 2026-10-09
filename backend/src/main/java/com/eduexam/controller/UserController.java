package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.User;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import com.eduexam.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ExcelService excelService;

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("real_name", u.getRealName() != null ? u.getRealName() : "");
        m.put("class_name", u.getClassName() != null ? u.getClassName() : "");
        m.put("is_active", u.getIsActive() != null ? u.getIsActive() : true);
        m.put("created_at", u.getCreatedAt());
        return m;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String className,
            @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().orderByAsc(User::getId);
        if (role != null && !role.isBlank()) wrapper.eq(User::getRole, role);
        if (className != null && !className.isBlank()) wrapper.eq(User::getClassName, className);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.strip() + "%";
            wrapper.and(w -> w.like(User::getUsername, like)
                    .or().like(User::getRealName, like)
                    .or().like(User::getEmail, like)
                    .or().like(User::getClassName, like));
        }
        return userMapper.selectList(wrapper).stream().map(this::userToMap).collect(Collectors.toList());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportUsers(
            @RequestParam(defaultValue = "student") String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String className,
            @RequestParam(defaultValue = "xlsx") String format,
            @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().orderByAsc(User::getId);
        if (role != null && !role.isBlank()) wrapper.eq(User::getRole, role);
        if (className != null && !className.isBlank()) wrapper.eq(User::getClassName, className);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.strip() + "%";
            wrapper.and(w -> w.like(User::getUsername, like)
                    .or().like(User::getRealName, like)
                    .or().like(User::getEmail, like)
                    .or().like(User::getClassName, like));
        }
        List<User> users = userMapper.selectList(wrapper);
        List<String> headers = List.of("username", "email", "password", "real_name", "class_name", "is_active", "role", "created_at");
        List<Map<String, Object>> rows = users.stream().map(u -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", u.getUsername());
            row.put("email", u.getEmail());
            row.put("password", "");
            row.put("real_name", u.getRealName());
            row.put("class_name", u.getClassName());
            row.put("is_active", u.getIsActive());
            row.put("role", u.getRole());
            row.put("created_at", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
            return row;
        }).collect(Collectors.toList());

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "students_export_" + ts + ".xlsx";
        byte[] bytes = excelService.export(headers, rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/import/students")
    public Map<String, Object> importStudents(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        List<Map<String, String>> rows = excelService.parse(file.getOriginalFilename(), file.getBytes());
        if (rows.isEmpty()) return Map.of("total", 0, "created", 0, "updated", 0, "errors", List.of());

        List<User> allUsers = userMapper.selectList(null);
        Map<String, User> byUsername = new HashMap<>();
        Map<String, User> byEmail = new HashMap<>();
        for (User u : allUsers) {
            byUsername.put(u.getUsername().toLowerCase(), u);
            byEmail.put(u.getEmail().toLowerCase(), u);
        }

        int created = 0, updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int lineNum = i + 2;
            try {
                String username = coalesce(row, "username", "student_no", "学号").strip();
                String email = coalesce(row, "email", "邮箱").strip();
                String password = coalesce(row, "password", "密码").strip();
                String realName = emptyToNull(coalesce(row, "real_name", "name", "姓名").strip());
                String classNameVal = emptyToNull(coalesce(row, "class_name", "class", "班级").strip());
                boolean isActive = parseBool(coalesce(row, "is_active", "status", "状态"), true);

                if (username.isEmpty()) throw new IllegalArgumentException("username is required");

                User existing = byUsername.get(username.toLowerCase());
                if (existing == null && !email.isEmpty()) existing = byEmail.get(email.toLowerCase());

                if (existing != null && !"student".equals(existing.getRole()))
                    throw new IllegalArgumentException("existing non-student users cannot be overwritten by student import");

                if (existing == null) {
                    if (email.isEmpty()) throw new IllegalArgumentException("email is required for new students");
                    if (password.isEmpty()) throw new IllegalArgumentException("password is required for new students");
                    if (byEmail.containsKey(email.toLowerCase())) throw new IllegalArgumentException("email already belongs to another user");
                    User u = new User();
                    u.setUsername(username); u.setEmail(email);
                    u.setHashedPassword(passwordEncoder.encode(password));
                    u.setRole("student"); u.setRealName(realName);
                    u.setClassName(classNameVal); u.setIsActive(isActive);
                    userMapper.insert(u);
                    byUsername.put(u.getUsername().toLowerCase(), u);
                    byEmail.put(u.getEmail().toLowerCase(), u);
                    created++;
                } else {
                    if (!email.isEmpty() && !email.equalsIgnoreCase(existing.getEmail())) {
                        User conflict = byEmail.get(email.toLowerCase());
                        if (conflict != null && !conflict.getId().equals(existing.getId()))
                            throw new IllegalArgumentException("email already belongs to another user");
                        byEmail.remove(existing.getEmail().toLowerCase());
                        existing.setEmail(email);
                        byEmail.put(existing.getEmail().toLowerCase(), existing);
                    }
                    existing.setRole("student"); existing.setRealName(realName);
                    existing.setClassName(classNameVal); existing.setIsActive(isActive);
                    if (!password.isEmpty()) existing.setHashedPassword(passwordEncoder.encode(password));
                    userMapper.updateById(existing);
                    updated++;
                }
            } catch (IllegalArgumentException ex) {
                errors.add(Map.of("row", lineNum, "message", ex.getMessage()));
            }
        }
        return Map.of("total", rows.size(), "created", created, "updated", updated,
                "errors", errors.size() > 20 ? errors.subList(0, 20) : errors);
    }

    @PutMapping("/{userId}/role")
    public Map<String, Boolean> setRole(@PathVariable Long userId, @RequestParam String role,
                                         @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        if (!List.of("admin", "teacher", "student").contains(role)) throw AppException.badRequest("Invalid role");
        if (userId.equals(loginUser.getUserId())) throw AppException.badRequest("You cannot change your own role");
        User u = userMapper.selectById(userId);
        if (u == null) throw AppException.notFound("User not found");
        u.setRole(role);
        userMapper.updateById(u);
        return Map.of("ok", true);
    }

    @PutMapping("/{userId}/toggle-active")
    public Map<String, Object> toggleActive(@PathVariable Long userId,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        User u = userMapper.selectById(userId);
        if (u == null) throw AppException.notFound("User not found");
        u.setIsActive(!Boolean.TRUE.equals(u.getIsActive()));
        userMapper.updateById(u);
        return Map.of("ok", true, "is_active", u.getIsActive());
    }

    private String coalesce(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String val = row.get(key);
            if (val != null) return val;
        }
        return "";
    }

    private String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }

    private boolean parseBool(String val, boolean def) {
        if (val == null || val.isBlank()) return def;
        String v = val.strip().toLowerCase();
        if (List.of("true", "1", "yes", "y", "active").contains(v)) return true;
        if (List.of("false", "0", "no", "n", "inactive", "disabled").contains(v)) return false;
        return def;
    }
}
