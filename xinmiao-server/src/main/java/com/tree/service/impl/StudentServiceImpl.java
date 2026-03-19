package com.tree.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.dto.LoginDto;
import com.tree.dto.RefreshTokenDto;
import com.tree.dto.RegisterDto;
import com.tree.dto.StudentDto;
import com.tree.dto.StudentShowDto;
import com.tree.entity.Clazz;
import com.tree.entity.College;
import com.tree.entity.Student;
import com.tree.entity.StudentClass;
import com.tree.entity.StudentTask;
import com.tree.entity.Task;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.tree.constant.RedisConstants;
import com.tree.exception.BusinessException;
import com.tree.mapper.StudentClassMapper;
import com.tree.mapper.StudentMapper;
import com.tree.mapper.StudentTaskMapper;
import com.tree.mapper.TaskMapper;
import com.tree.service.ClassService;
import com.tree.service.CollegeService;
import com.tree.service.StudentService;
import com.tree.util.JwtUtils;
import com.tree.result.ErrorCode;
import com.tree.utils.LoginUserUtils;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author ljx
 * @description 针对表【tb_student(学生)】的数据库操作Service实现
 * @createDate 2024-02-17 14:26:22
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final StudentTaskMapper studentTaskMapper;
    private final StudentClassMapper studentClassMapper;
    private final TaskMapper taskMapper;
    private final CollegeService collegeService;
    private final ClassService classService;

    public StudentServiceImpl(StringRedisTemplate stringRedisTemplate,
                              JwtUtils jwtUtils,
                              PasswordEncoder passwordEncoder,
                              StudentClassMapper studentClassMapper,
                              StudentTaskMapper studentTaskMapper,
                              TaskMapper taskMapper,
                              CollegeService collegeService,
                              ClassService classService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.studentClassMapper = studentClassMapper;
        this.studentTaskMapper = studentTaskMapper;
        this.taskMapper = taskMapper;
        this.collegeService = collegeService;
        this.classService = classService;
    }

    /**
     * 注册
     *
     * @param request
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(HttpServletRequest request, RegisterDto registerDto) {
//        Object numberValidated = request.getSession().getAttribute("numberValidated");
//        Object faceValidated = request.getSession().getAttribute("faceValidated");
//        if (numberValidated == null || !((Boolean) numberValidated)
//                || faceValidated == null || !(Boolean) faceValidated) {
//            return Result.fail("您还未验证或验证还未通过，请先验证");
//        }

        String username = registerDto.getUsername();
        if (StringUtils.isEmpty(username)) {
            Object idNumber = request.getSession().getAttribute("idNumber");
            registerDto.setUsername((String) idNumber);
            username = registerDto.getUsername();
        }
        // 校验用户名唯一性（登录依靠用户名，不可重复）
        LambdaQueryWrapper<Student> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(Student::getUsername, username);
        Student existingUser = getOne(userWrapper);
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.DUPLICATE, "用户名已存在，请更换用户名");
        }

        // 密码 BCrypt 哈希（防撞库、带盐）
        String encodedPassword = passwordEncoder.encode(registerDto.getPassword());
        registerDto.setPassword(encodedPassword);

        Student student = BeanUtil.copyProperties(registerDto, Student.class, "id");
        save(student);

        //学生班级信息关联（学院/班级名称与 tb_college/tb_class 的 id 保持一致）
        StudentClass studentClass = new StudentClass();
        studentClass.setStudentId(student.getId());
        String college = request.getParameter("college");
        String clazz = request.getParameter("clazz");
        if (StringUtils.isNotBlank(college)) studentClass.setCollege(college);
        if (StringUtils.isNotBlank(clazz)) studentClass.setClazz(clazz);
        if (StringUtils.isNotBlank(college)) {
            College co = collegeService.getOne(Wrappers.<College>lambdaQuery().eq(College::getName, college));
            if (co != null) studentClass.setCollegeId(co.getId());
        }
        if (StringUtils.isNotBlank(clazz) && studentClass.getCollegeId() != null) {
            Clazz cz = classService.getOne(Wrappers.<Clazz>lambdaQuery()
                    .eq(Clazz::getName, clazz)
                    .eq(Clazz::getCollegeId, studentClass.getCollegeId()));
            if (cz != null) studentClass.setClassId(cz.getId());
        }
        int insertClass = studentClassMapper.insert(studentClass);
        if (insertClass <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "班级信息关联失败");
        }

        Long studentId = student.getId();

        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getIsPublished, 1)
                .and(w -> w
                        .eq(Task::getLevel, "校级")
                        .or(q -> q.eq(Task::getLevel, "院级").eq(Task::getCollegeId, studentClass.getCollegeId()))
                        .or(q -> q.eq(Task::getLevel, "班级").eq(Task::getClassId, studentClass.getClassId()))
                );
        List<Task> tasks = taskMapper.selectList(queryWrapper);

        if (!tasks.isEmpty()) {
            List<StudentTask> studentTasks = tasks.stream().map(task -> {
                StudentTask st = new StudentTask();
                st.setStudentId(studentId);
                st.setTaskId(task.getId());
                st.setStatus(0); // 初始状态：未完成
                st.setCreateTime(LocalDateTime.now());
                st.setUpdateTime(LocalDateTime.now());
                return st;
            }).collect(Collectors.toList());
            studentTaskMapper.batchInsert(studentTasks); // 使用已有的批量插入方法
        }

        String refreshToken = UUID.fastUUID().toString(true);
        Map<String, Object> userMap = BeanUtil.beanToMap(BeanUtil.copyProperties(student, StudentDto.class));
        userMap.put("id", student.getId().toString());
        userMap.put("type", "Student");
        // refreshToken 会话中冗余存储学院/班级 id：便于刷新后前端继续订阅 WebSocket（topic/college、topic/class）
        if (studentClass.getCollegeId() != null) userMap.put("collegeId", String.valueOf(studentClass.getCollegeId()));
        if (studentClass.getClassId() != null) userMap.put("classId", String.valueOf(studentClass.getClassId()));
        long accessTtlMs = RedisConstants.ACCESS_TOKEN_TTL.toMillis();
        String accessToken = jwtUtils.generateAccessToken(student.getId(), "Student", accessTtlMs);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, userMap);
        stringRedisTemplate.expire(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, RedisConstants.REFRESH_TOKEN_TTL);

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", RedisConstants.ACCESS_TOKEN_TTL.getSeconds());
        return result;
    }

    /**
     *  登录
     * @param loginDto
     * @return
     */
    @Override
    public Map<String, Object> login(LoginDto loginDto) {
        String username = loginDto.getUsername();
        String rawPassword = loginDto.getPassword();
        Student stu = lambdaQuery().eq(Student::getUsername, username).one();
        if (stu == null || !passwordEncoder.matches(rawPassword, stu.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "用户名或密码错误");
        }
        if (stu.getId() == null) {
            log.error("学生ID为空，无法登录");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户信息不完整");
        }

        String refreshToken = UUID.fastUUID().toString(true);
        Map<String, Object> userMap = BeanUtil.beanToMap(BeanUtil.copyProperties(stu, StudentDto.class), new HashMap<>(), CopyOptions.create().setFieldValueEditor(
                (fieldName, fieldValue) -> fieldName.equals("id") ? fieldValue.toString() : fieldValue
        ));
        userMap.put("id", stu.getId().toString());
        userMap.put("type", "Student");

        long accessTtlMs = RedisConstants.ACCESS_TOKEN_TTL.toMillis();
        String accessToken = jwtUtils.generateAccessToken(stu.getId(), "Student", accessTtlMs);

        // refreshToken 会话中冗余存储学院/班级 id
        StudentClass sc = studentClassMapper.selectOne(Wrappers.<StudentClass>lambdaQuery().eq(StudentClass::getStudentId, stu.getId()));
        if (sc != null) {
            if (sc.getCollegeId() != null) userMap.put("collegeId", String.valueOf(sc.getCollegeId()));
            if (sc.getClassId() != null) userMap.put("classId", String.valueOf(sc.getClassId()));
        }
        stringRedisTemplate.opsForHash().putAll(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, userMap);
        stringRedisTemplate.expire(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, RedisConstants.REFRESH_TOKEN_TTL);

        StudentShowDto userInfo = BeanUtil.copyProperties(stu, StudentShowDto.class);
        if (sc != null) {
            userInfo.setCollegeId(sc.getCollegeId());
            userInfo.setClassId(sc.getClassId());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", RedisConstants.ACCESS_TOKEN_TTL.getSeconds());
        result.put("userInfo", userInfo);
        return result;
    }

    @Override
    public Map<String, Object> refreshToken(RefreshTokenDto dto) {
        if (dto == null || dto.getRefreshToken() == null || dto.getRefreshToken().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "refreshToken 不能为空");
        }
        String rt = dto.getRefreshToken().trim();
        String refreshKey = RedisConstants.REFRESH_TOKEN_KEY + rt;
        Map<Object, Object> userMap;
        try {
            userMap = stringRedisTemplate.opsForHash().entries(refreshKey);
        } catch (Exception e) {
            log.warn("Redis unavailable during refreshToken, fail with 503. refreshKey={}", refreshKey, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        }
        if (userMap == null || userMap.isEmpty()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "refreshToken 无效或已过期");
        }
        if (!"Student".equalsIgnoreCase(String.valueOf(userMap.get("type")))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "refreshToken 类型不匹配");
        }
        try {
            stringRedisTemplate.delete(refreshKey);
        } catch (Exception e) {
            log.warn("Redis unavailable during refreshToken cleanup, fail with 503. refreshKey={}", refreshKey, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        }

        String idStr = String.valueOf(userMap.get("id"));
        Long userId = Long.parseLong(idStr.replace("\"", "").trim());
        long accessTtlMs = RedisConstants.ACCESS_TOKEN_TTL.toMillis();
        String newAccessToken = jwtUtils.generateAccessToken(userId, "Student", accessTtlMs);
        String newRefreshToken = UUID.fastUUID().toString(true);
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<Object, Object> e : userMap.entrySet()) {
            map.put(String.valueOf(e.getKey()), e.getValue());
        }
        try {
            stringRedisTemplate.opsForHash().putAll(RedisConstants.REFRESH_TOKEN_KEY + newRefreshToken, map);
            stringRedisTemplate.expire(RedisConstants.REFRESH_TOKEN_KEY + newRefreshToken, RedisConstants.REFRESH_TOKEN_TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable during refreshToken rotate, fail with 503. userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("token", newAccessToken);
        result.put("refreshToken", newRefreshToken);
        result.put("expiresIn", RedisConstants.ACCESS_TOKEN_TTL.getSeconds());
        return result;
    }

    @Override
    public void updateStudent(Student student) {
        updateById(student);
    }

    @Override
    public void logout(String refreshTokenFromBody) {
        String accessToken = LoginUserUtils.getCurrentToken();
        if (accessToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未找到登录信息");
        }
        jwtUtils.blacklist(accessToken);
        if (refreshTokenFromBody != null && !refreshTokenFromBody.isBlank()) {
            try {
                stringRedisTemplate.delete(RedisConstants.REFRESH_TOKEN_KEY + refreshTokenFromBody.trim());
            } catch (Exception e) {
                log.warn("Redis unavailable during logout refreshToken delete, fail with 503.", e);
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
            }
        }
        log.info("用户登出成功，JWT 已加入黑名单");
    }
}




