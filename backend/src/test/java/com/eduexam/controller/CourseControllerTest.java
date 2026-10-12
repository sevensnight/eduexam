package com.eduexam.controller;

import com.eduexam.domain.Course;
import com.eduexam.domain.User;
import com.eduexam.mapper.CourseEnrollmentMapper;
import com.eduexam.mapper.CourseMapper;
import com.eduexam.mapper.ExamMapper;
import com.eduexam.mapper.UserMapper;
import com.eduexam.security.LoginUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    @Mock CourseMapper courseMapper;
    @Mock ExamMapper examMapper;
    @Mock CourseEnrollmentMapper enrollmentMapper;
    @Mock UserMapper userMapper;

    @Test
    void teacherCanSelectTheFirstStudentForANewCourse() {
        CourseController controller = new CourseController(courseMapper, examMapper, enrollmentMapper, userMapper);
        User teacher = user(10L, "teacher");
        Course course = new Course();
        course.setId(20L);
        course.setTeacherId(teacher.getId());
        User student = user(30L, "student");
        student.setUsername("student30");
        student.setEmail("student30@example.test");
        student.setIsActive(true);

        when(courseMapper.selectById(20L)).thenReturn(course);
        when(userMapper.selectList(any())).thenReturn(List.of(student));

        List<Map<String, Object>> candidates = controller.listStudentCandidates(
                null, 20L, new LoginUser(teacher));

        assertEquals(1, candidates.size());
        assertEquals(30L, candidates.get(0).get("id"));
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setIsActive(true);
        return user;
    }
}
