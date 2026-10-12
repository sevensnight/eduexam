package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.User;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import com.eduexam.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_LIST_ROWS = 5_000;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ExcelService excelService;
    private final JdbcTemplate jdbcTemplate;

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
        wrapper.last("LIMIT " + (MAX_LIST_ROWS + 1));
        List<User> users = userMapper.selectList(wrapper);
        if (users.size() > MAX_LIST_ROWS)
            throw AppException.badRequest("用户结果超过 " + MAX_LIST_ROWS + " 条，请增加角色、班级或关键词筛选");
        return users.stream().map(this::userToMap).collect(Collectors.toList());
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
        wrapper.last("LIMIT " + (ExcelService.MAX_EXPORT_ROWS + 1));
        List<User> users = userMapper.selectList(wrapper);
        if (users.size() > ExcelService.MAX_EXPORT_ROWS)
            throw AppException.badRequest("导出结果超过 " + ExcelService.MAX_EXPORT_ROWS + " 条，请增加筛选条件");
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
        String normalizedFormat = excelService.normalizeFormat(format);
        String filename = "students_export_" + ts + "." + normalizedFormat;
        byte[] bytes = excelService.export(normalizedFormat, headers, rows);
        MediaType contentType = "csv".equals(normalizedFormat)
                ? MediaType.parseMediaType("text/csv;charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(bytes);
    }

    @PostMapping("/import/students")
    public Map<String, Object> importStudents(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser loginUser) throws Exception {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        if (file == null || file.isEmpty()) throw AppException.badRequest("请选择要导入的文件");
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
                String activeValue = coalesce(row, "is_active", "status", "状态");

                validateImportedStudent(username, email, password, realName, classNameVal);

                User existing = byUsername.get(username.toLowerCase());
                if (existing == null && !email.isEmpty()) existing = byEmail.get(email.toLowerCase());
                // Missing status means "use the default" for a new student,
                // but must not silently reactivate an existing disabled user.
                boolean isActive = parseBool(activeValue,
                        existing == null || Boolean.TRUE.equals(existing.getIsActive()));

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
                    boolean activeStatusChanged = !Objects.equals(existing.getIsActive(), isActive);
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
                    LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>()
                            .eq(User::getId, existing.getId())
                            .set(User::getEmail, existing.getEmail())
                            .set(User::getRealName, realName)
                            .set(User::getClassName, classNameVal)
                            .set(User::getIsActive, isActive);
                    if (!password.isEmpty()) {
                        update.set(User::getHashedPassword, existing.getHashedPassword())
                                .setSql("token_version = token_version + 1");
                    } else if (activeStatusChanged) {
                        // A disable/enable cycle is a security boundary. Revoke
                        // tokens issued before the import just as the dedicated
                        // toggle-active endpoint does.
                        update.setSql("token_version = token_version + 1");
                    }
                    userMapper.update(null, update);
                    updated++;
                }
            } catch (IllegalArgumentException ex) {
                errors.add(Map.of("row", lineNum, "message", ex.getMessage()));
            } catch (DataIntegrityViolationException ex) {
                errors.add(Map.of("row", lineNum, "message", "用户名或邮箱与其他用户冲突"));
            }
        }
        return Map.of("total", rows.size(), "created", created, "updated", updated,
                "errors", errors.size() > 20 ? errors.subList(0, 20) : errors);
    }

    @PutMapping("/{userId}/role")
    @Transactional
    public Map<String, Boolean> setRole(@PathVariable Long userId, @RequestParam String role,
                                         @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        role = role == null ? "" : role.strip().toLowerCase(Locale.ROOT);
        if (!List.of("admin", "teacher", "student").contains(role)) throw AppException.badRequest("角色值不正确");
        if (userId.equals(loginUser.getUserId())) throw AppException.badRequest("不能修改自己的角色");
        lockAdministrators();
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId).last("FOR UPDATE"));
        if (u == null) throw AppException.notFound("用户不存在");
        if (role.equals(u.getRole())) return Map.of("ok", true);
        if ("admin".equals(u.getRole()) && Boolean.TRUE.equals(u.getIsActive()) && countActiveAdministrators() <= 1)
            throw AppException.conflict("系统至少需要保留一个有效管理员");
        validateRoleDependencies(u, role);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getRole, role)
                .setSql("token_version = token_version + 1"));
        return Map.of("ok", true);
    }

    @PutMapping("/{userId}/toggle-active")
    @Transactional
    public Map<String, Object> toggleActive(@PathVariable Long userId,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole())) throw AppException.forbidden("无权访问");
        lockAdministrators();
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId).last("FOR UPDATE"));
        if (u == null) throw AppException.notFound("用户不存在");
        boolean nextActive = !Boolean.TRUE.equals(u.getIsActive());
        if (userId.equals(loginUser.getUserId()) && !nextActive)
            throw AppException.badRequest("不能停用当前登录的管理员账号");
        if (!nextActive && "admin".equals(u.getRole()) && countActiveAdministrators() <= 1)
            throw AppException.conflict("系统至少需要保留一个有效管理员");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getIsActive, nextActive)
                .setSql("token_version = token_version + 1"));
        return Map.of("ok", true, "is_active", nextActive);
    }

    private String coalesce(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String val = row.get(key);
            if (val != null) return val;
        }
        return "";
    }

    private String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }

    private void validateImportedStudent(String username, String email, String password,
                                         String realName, String className) {
        if (username.isEmpty()) throw new IllegalArgumentException("username is required");
        if (username.length() > 64 || !username.matches("^[A-Za-z0-9_.-]+$"))
            throw new IllegalArgumentException("username must be 1-64 letters, digits, dots, underscores or hyphens");
        if (!email.isEmpty() && (email.length() > 128 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
            throw new IllegalArgumentException("email format is invalid");
        if (!password.isEmpty() && (password.length() < 8 || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("^(?=.*\\p{L})(?=.*\\d).*$")))
            throw new IllegalArgumentException("password must be 8-72 characters (at most 72 UTF-8 bytes) and contain both letters and digits");
        if (!password.isEmpty() && (password.equalsIgnoreCase(username)
                || (!email.isEmpty() && password.equalsIgnoreCase(email))))
            throw new IllegalArgumentException("password cannot equal username or email");
        if (realName != null && realName.length() > 64) throw new IllegalArgumentException("real_name is too long");
        if (className != null && className.length() > 64) throw new IllegalArgumentException("class_name is too long");
    }

    private void lockAdministrators() {
        userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "admin").orderByAsc(User::getId).last("FOR UPDATE"));
    }

    private long countActiveAdministrators() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "admin").eq(User::getIsActive, true));
    }

    private void validateRoleDependencies(User user, String targetRole) {
        if ("student".equals(user.getRole()) && !"student".equals(targetRole)) {
            long dependencies = count("SELECT COUNT(*) FROM course_enrollments WHERE student_id = ?", user.getId())
                    + count("SELECT COUNT(*) FROM exam_records WHERE student_id = ?", user.getId())
                    + count("SELECT COUNT(*) FROM wrong_book WHERE student_id = ?", user.getId());
            if (dependencies > 0) throw AppException.conflict("该学生仍有关联课程、考试记录或错题，不能修改角色");
        }
        if ("student".equals(targetRole) && !"student".equals(user.getRole())) {
            long dependencies = count("SELECT COUNT(*) FROM courses WHERE teacher_id = ?", user.getId())
                    + count("SELECT COUNT(*) FROM exams WHERE creator_id = ?", user.getId())
                    + count("SELECT COUNT(*) FROM questions WHERE creator_id = ?", user.getId())
                    + count("SELECT COUNT(*) FROM categories WHERE creator_id = ?", user.getId())
                    + count("SELECT COUNT(*) FROM tags WHERE creator_id = ?", user.getId());
            if (dependencies > 0) throw AppException.conflict("该用户仍拥有课程、考试或题库内容，不能改为学生");
        }
    }

    private long count(String sql, Long userId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return value != null ? value : 0L;
    }

    private boolean parseBool(String val, boolean def) {
        if (val == null || val.isBlank()) return def;
        String v = val.strip().toLowerCase();
        if (List.of("true", "1", "yes", "y", "active").contains(v)) return true;
        if (List.of("false", "0", "no", "n", "inactive", "disabled").contains(v)) return false;
        throw new IllegalArgumentException("is_active must be true/false, 1/0, yes/no or active/inactive");
    }
}
