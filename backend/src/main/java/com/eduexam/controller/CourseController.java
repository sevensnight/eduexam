package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduexam.common.AppException;
import com.eduexam.domain.Course;
import com.eduexam.domain.CourseEnrollment;
import com.eduexam.domain.User;
import com.eduexam.dto.CourseEnrollmentUpdateDTO;
import com.eduexam.mapper.CourseEnrollmentMapper;
import com.eduexam.mapper.CourseMapper;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseMapper courseMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final UserMapper userMapper;

    @Data
    static class CourseCreateRequest {
        private String name;
        private String description;
        private Long teacherId;
    }

    @Data
    static class CourseUpdateRequest {
        private String name;
        private String description;
        private Long teacherId;
    }

    private Map<String, Object> courseToMap(Course c, List<CourseEnrollment> enrollments, User teacher) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("teacher_id", c.getTeacherId());
        if (teacher != null)
            m.put("teacher_name", teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername());
        else m.put("teacher_name", null);
        m.put("student_count", enrollments.size());
        m.put("student_ids", enrollments.stream().map(CourseEnrollment::getStudentId).collect(Collectors.toList()));
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    @GetMapping
    public List<Map<String, Object>> listCourses(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .orderByDesc(Course::getCreatedAt).orderByDesc(Course::getId);
        if ("teacher".equals(loginUser.getRole())) wrapper.eq(Course::getTeacherId, loginUser.getUserId());
        List<Course> courses = courseMapper.selectList(wrapper);
        List<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toList());
        Map<Long, List<CourseEnrollment>> enrollMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>().in(CourseEnrollment::getCourseId, courseIds))
                    .forEach(e -> enrollMap.computeIfAbsent(e.getCourseId(), k -> new ArrayList<>()).add(e));
        }
        Set<Long> teacherIds = courses.stream().map(Course::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> teacherMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            userMapper.selectBatchIds(teacherIds).forEach(u -> teacherMap.put(u.getId(), u));
        }
        return courses.stream().map(c -> courseToMap(c,
                enrollMap.getOrDefault(c.getId(), List.of()),
                c.getTeacherId() != null ? teacherMap.get(c.getTeacherId()) : null))
                .collect(Collectors.toList());
    }

    @GetMapping("/classes")
    public List<String> listClasses(@AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student")
                .isNotNull(User::getClassName)
                .ne(User::getClassName, "")
                .orderByAsc(User::getClassName)
                .select(User::getClassName);
        if ("teacher".equals(loginUser.getRole())) {
            List<Long> studentIds = enrollmentMapper.selectList(
                    new LambdaQueryWrapper<CourseEnrollment>().apply(
                            "course_id IN (SELECT id FROM courses WHERE teacher_id = " + loginUser.getUserId() + ")"))
                    .stream().map(CourseEnrollment::getStudentId).distinct().collect(Collectors.toList());
            if (studentIds.isEmpty()) return List.of();
            wrapper.in(User::getId, studentIds);
        }
        return userMapper.selectList(wrapper).stream()
                .map(User::getClassName).filter(cn -> cn != null && !cn.isEmpty())
                .distinct().collect(Collectors.toList());
    }

    @GetMapping("/students")
    public List<Map<String, Object>> listStudentCandidates(
            @RequestParam(required = false) String className,
            @RequestParam(required = false) Long courseId,
            @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student").eq(User::getIsActive, true)
                .orderByAsc(User::getClassName).orderByAsc(User::getUsername);
        if ("teacher".equals(loginUser.getRole())) {
            List<Long> studentIds = enrollmentMapper.selectList(
                    new LambdaQueryWrapper<CourseEnrollment>().apply(
                            "course_id IN (SELECT id FROM courses WHERE teacher_id = " + loginUser.getUserId() + ")"))
                    .stream().map(CourseEnrollment::getStudentId).distinct().collect(Collectors.toList());
            if (studentIds.isEmpty()) return List.of();
            wrapper.in(User::getId, studentIds);
        }
        if (className != null && !className.isBlank()) wrapper.eq(User::getClassName, className);
        return userMapper.selectList(wrapper).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId()); m.put("username", u.getUsername());
            m.put("email", u.getEmail()); m.put("role", u.getRole());
            m.put("real_name", u.getRealName()); m.put("class_name", u.getClassName());
            m.put("is_active", u.getIsActive());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> createCourse(@RequestBody CourseCreateRequest req,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Long teacherId = "admin".equals(loginUser.getRole()) ? req.getTeacherId() : loginUser.getUserId();
        if (teacherId != null) {
            User teacher = userMapper.selectById(teacherId);
            if (teacher == null || !"teacher".equals(teacher.getRole())) throw AppException.badRequest("授课教师不存在");
        }
        Course c = new Course();
        c.setName(req.getName()); c.setDescription(req.getDescription()); c.setTeacherId(teacherId);
        courseMapper.insert(c);
        User teacher = teacherId != null ? userMapper.selectById(teacherId) : null;
        return courseToMap(c, List.of(), teacher);
    }

    @GetMapping("/{courseId}")
    public Map<String, Object> getCourse(@PathVariable Long courseId,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = courseMapper.selectById(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && c.getTeacherId() != null && !c.getTeacherId().equals(loginUser.getUserId()))
            throw AppException.forbidden("无权管理该课程");
        List<CourseEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        List<Long> studentIds = enrollments.stream().map(CourseEnrollment::getStudentId).collect(Collectors.toList());
        List<Map<String, Object>> students = studentIds.isEmpty() ? List.of() :
                userMapper.selectBatchIds(studentIds).stream().map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId()); m.put("username", u.getUsername());
                    m.put("email", u.getEmail()); m.put("role", u.getRole());
                    m.put("real_name", u.getRealName()); m.put("class_name", u.getClassName());
                    m.put("is_active", u.getIsActive());
                    return m;
                }).collect(Collectors.toList());
        User teacher = c.getTeacherId() != null ? userMapper.selectById(c.getTeacherId()) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course", courseToMap(c, enrollments, teacher));
        result.put("students", students);
        return result;
    }

    @PutMapping("/{courseId}")
    public Map<String, Object> updateCourse(@PathVariable Long courseId,
                                             @RequestBody CourseUpdateRequest req,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = courseMapper.selectById(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && c.getTeacherId() != null && !c.getTeacherId().equals(loginUser.getUserId()))
            throw AppException.forbidden("无权管理该课程");
        if (req.getName() != null) c.setName(req.getName());
        if (req.getDescription() != null) c.setDescription(req.getDescription());
        if ("admin".equals(loginUser.getRole()) && req.getTeacherId() != null) {
            User t = userMapper.selectById(req.getTeacherId());
            if (t == null || !"teacher".equals(t.getRole())) throw AppException.badRequest("授课教师不存在");
            c.setTeacherId(req.getTeacherId());
        }
        courseMapper.updateById(c);
        List<CourseEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        User teacher = c.getTeacherId() != null ? userMapper.selectById(c.getTeacherId()) : null;
        return courseToMap(c, enrollments, teacher);
    }

    @PutMapping("/{courseId}/enrollments")
    public Map<String, Object> replaceEnrollments(@PathVariable Long courseId,
                                                   @RequestBody CourseEnrollmentUpdateDTO req,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = courseMapper.selectById(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && c.getTeacherId() != null && !c.getTeacherId().equals(loginUser.getUserId()))
            throw AppException.forbidden("无权管理该课程");
        List<Long> studentIds = req.getStudentIds() != null ?
                req.getStudentIds().stream().distinct().sorted().collect(Collectors.toList()) : List.of();
        if (!studentIds.isEmpty()) {
            List<Long> validIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getId, studentIds).eq(User::getRole, "student").eq(User::getIsActive, true))
                    .stream().map(User::getId).collect(Collectors.toList());
            List<Long> invalid = studentIds.stream().filter(id -> !validIds.contains(id)).collect(Collectors.toList());
            if (!invalid.isEmpty()) throw AppException.badRequest("学生不存在或不可用: " + invalid);
        }
        enrollmentMapper.delete(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        for (Long sid : studentIds) {
            CourseEnrollment e = new CourseEnrollment();
            e.setCourseId(courseId); e.setStudentId(sid);
            enrollmentMapper.insert(e);
        }
        List<CourseEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        User teacher = c.getTeacherId() != null ? userMapper.selectById(c.getTeacherId()) : null;
        return courseToMap(c, enrollments, teacher);
    }

    @DeleteMapping("/{courseId}")
    public Map<String, Boolean> deleteCourse(@PathVariable Long courseId,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = courseMapper.selectById(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && c.getTeacherId() != null && !c.getTeacherId().equals(loginUser.getUserId()))
            throw AppException.forbidden("无权管理该课程");
        enrollmentMapper.delete(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        courseMapper.deleteById(courseId);
        return Map.of("ok", true);
    }
}
