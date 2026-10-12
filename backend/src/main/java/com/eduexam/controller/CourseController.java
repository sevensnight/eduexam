package com.eduexam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.eduexam.common.AppException;
import com.eduexam.domain.Course;
import com.eduexam.domain.CourseEnrollment;
import com.eduexam.domain.User;
import com.eduexam.dto.CourseEnrollmentUpdateDTO;
import com.eduexam.mapper.CourseEnrollmentMapper;
import com.eduexam.mapper.CourseMapper;
import com.eduexam.mapper.ExamMapper;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseMapper courseMapper;
    private final ExamMapper examMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final UserMapper userMapper;

    private Course lockCourse(Long courseId) {
        return courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getId, courseId).last("FOR UPDATE"));
    }

    private User lockActiveTeacher(Long teacherId) {
        if (teacherId == null) return null;
        User teacher = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, teacherId).last("FOR SHARE"));
        if (teacher == null || !"teacher".equals(teacher.getRole())
                || !Boolean.TRUE.equals(teacher.getIsActive()))
            throw AppException.badRequest("授课教师不存在或已停用");
        return teacher;
    }

    @Data
    static class CourseCreateRequest {
        @NotBlank(message = "课程名称不能为空")
        @Size(max = 128, message = "课程名称不能超过 128 个字符")
        private String name;

        @Size(max = 10000, message = "课程描述过长")
        private String description;

        @Positive(message = "教师编号必须为正整数")
        private Long teacherId;
    }

    @Data
    static class CourseUpdateRequest {
        @Size(min = 1, max = 128, message = "课程名称长度必须为 1-128 个字符")
        private String name;

        @Size(max = 10000, message = "课程描述过长")
        private String description;
        @JsonIgnore
        private boolean descriptionPresent;

        @Positive(message = "教师编号必须为正整数")
        private Long teacherId;

        @JsonSetter("description")
        public void setDescription(String description) {
            this.description = description;
            this.descriptionPresent = true;
        }
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
                .eq(User::getIsActive, true)
                .isNotNull(User::getClassName)
                .ne(User::getClassName, "")
                .orderByAsc(User::getClassName)
                .select(User::getClassName);
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

        // A teacher may manage the roster of a course they own.  Validate the
        // requested course instead of deriving candidates from existing
        // enrollments, which made it impossible to populate a brand-new course.
        if (courseId != null) {
            Course course = courseMapper.selectById(courseId);
            if (course == null) throw AppException.notFound("课程不存在");
            if ("teacher".equals(loginUser.getRole())
                    && !loginUser.getUserId().equals(course.getTeacherId()))
                throw AppException.forbidden("无权管理该课程");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student").eq(User::getIsActive, true)
                .orderByAsc(User::getClassName).orderByAsc(User::getUsername);
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
    @Transactional
    public Map<String, Object> createCourse(@Valid @RequestBody CourseCreateRequest req,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Long teacherId = "admin".equals(loginUser.getRole()) ? req.getTeacherId() : loginUser.getUserId();
        if (teacherId == null) throw AppException.badRequest("请选择授课教师");
        User teacher = lockActiveTeacher(teacherId);
        Course c = new Course();
        c.setName(req.getName().strip());
        c.setDescription(req.getDescription() != null ? req.getDescription().strip() : null);
        c.setTeacherId(teacherId);
        courseMapper.insert(c);
        return courseToMap(c, List.of(), teacher);
    }

    @GetMapping("/{courseId}")
    public Map<String, Object> getCourse(@PathVariable Long courseId,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = courseMapper.selectById(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(c.getTeacherId()))
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
    @Transactional
    public Map<String, Object> updateCourse(@PathVariable Long courseId,
                                             @Valid @RequestBody CourseUpdateRequest req,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = lockCourse(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(c.getTeacherId()))
            throw AppException.forbidden("无权管理该课程");
        if (req.getName() != null) {
            String name = req.getName().strip();
            if (name.isEmpty()) throw AppException.badRequest("课程名称不能为空");
            c.setName(name);
        }
        if (req.isDescriptionPresent()) c.setDescription(
                req.getDescription() != null && !req.getDescription().isBlank()
                        ? req.getDescription().strip() : null);
        if ("admin".equals(loginUser.getRole()) && req.getTeacherId() != null) {
            User t = lockActiveTeacher(req.getTeacherId());
            if (!Objects.equals(c.getTeacherId(), req.getTeacherId())
                    && examMapper.selectCount(new LambdaQueryWrapper<com.eduexam.domain.Exam>()
                    .eq(com.eduexam.domain.Exam::getCourseId, courseId)) > 0)
                throw AppException.conflict("该课程已有考试，不能更换授课教师");
            c.setTeacherId(req.getTeacherId());
        }
        courseMapper.updateById(c);
        if (req.isDescriptionPresent()) {
            courseMapper.update(null, new LambdaUpdateWrapper<Course>()
                    .eq(Course::getId, courseId)
                    .set(Course::getDescription, c.getDescription()));
        }
        List<CourseEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        User teacher = c.getTeacherId() != null ? userMapper.selectById(c.getTeacherId()) : null;
        return courseToMap(c, enrollments, teacher);
    }

    @PutMapping("/{courseId}/enrollments")
    @Transactional
    public Map<String, Object> replaceEnrollments(@PathVariable Long courseId,
                                                   @Valid @RequestBody CourseEnrollmentUpdateDTO req,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = lockCourse(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(c.getTeacherId()))
            throw AppException.forbidden("无权管理该课程");
        List<Long> studentIds = req.getStudentIds() != null ?
                req.getStudentIds().stream().distinct().sorted().collect(Collectors.toList()) : List.of();
        if (!studentIds.isEmpty()) {
            // Re-read with row locks in the same stable order. This serializes
            // enrollment with administrator role changes.
            List<Long> validIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getId, studentIds)
                    .eq(User::getRole, "student")
                    .eq(User::getIsActive, true)
                    .orderByAsc(User::getId)
                    .last("FOR SHARE"))
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
    @Transactional
    public Map<String, Boolean> deleteCourse(@PathVariable Long courseId,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        if (!"admin".equals(loginUser.getRole()) && !"teacher".equals(loginUser.getRole()))
            throw AppException.forbidden("无权访问");
        Course c = lockCourse(courseId);
        if (c == null) throw AppException.notFound("课程不存在");
        if ("teacher".equals(loginUser.getRole()) && !loginUser.getUserId().equals(c.getTeacherId()))
            throw AppException.forbidden("无权管理该课程");
        if (examMapper.selectCount(new LambdaQueryWrapper<com.eduexam.domain.Exam>()
                .eq(com.eduexam.domain.Exam::getCourseId, courseId)) > 0)
            throw AppException.badRequest("已有考试引用该课程，不能删除");
        enrollmentMapper.delete(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
        courseMapper.deleteById(courseId);
        return Map.of("ok", true);
    }
}
