package com.tree.interceptor;

import com.tree.entity.StudentClass;
import com.tree.service.StudentClassService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订阅校验：学生只能订阅 /topic/task/school、本院的 /topic/task/college/{collegeId}、本班的 /topic/task/class/{classId}。
 * 辅导员暂不限制。未鉴权或越权订阅时拒绝该次 SUBSCRIBE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSubscribeInterceptor implements ChannelInterceptor {

    private final StudentClassService studentClassService;

    private static final String PREFIX_TOPIC_TASK = "/topic/task/";
    private static final String PREFIX_COLLEGE = "/topic/task/college/";
    private static final String PREFIX_CLASS = "/topic/task/class/";
    private static final String DEST_SCHOOL = "/topic/task/school";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(PREFIX_TOPIC_TASK)) {
            return message;
        }

        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null) {
            log.warn("Task subscribe rejected: no session attributes, destination={}", destination);
            return null;
        }

        Object userIdObj = sessionAttrs.get(JwtHandshakeInterceptor.ATTR_USER_ID);
        Object userTypeObj = sessionAttrs.get(JwtHandshakeInterceptor.ATTR_USER_TYPE);
        if (userIdObj == null || userTypeObj == null) {
            log.warn("Task subscribe rejected: not authenticated, destination={}", destination);
            return null;
        }

        String userType = userTypeObj.toString();
        if ("Counselor".equalsIgnoreCase(userType) || "Admin".equalsIgnoreCase(userType)) {
            return message;
        }

        if (!"Student".equalsIgnoreCase(userType)) {
            log.warn("Task subscribe rejected: unknown userType={}, destination={}", userType, destination);
            return null;
        }

        Long userId = userIdObj instanceof Long ? (Long) userIdObj : Long.parseLong(userIdObj.toString());
        StudentClass studentClass = studentClassService.searchClassByStudentId(userId);
        if (studentClass == null) {
            log.warn("Task subscribe rejected: student {} has no class info, destination={}", userId, destination);
            return null;
        }

        if (DEST_SCHOOL.equals(destination)) {
            return message;
        }
        if (destination.startsWith(PREFIX_COLLEGE)) {
            String idStr = destination.substring(PREFIX_COLLEGE.length()).trim();
            if (studentClass.getCollegeId() != null && idStr.equals(String.valueOf(studentClass.getCollegeId()))) {
                return message;
            }
            log.warn("Task subscribe rejected: student {} collegeId {} not match destination {}", userId, studentClass.getCollegeId(), destination);
            return null;
        }
        if (destination.startsWith(PREFIX_CLASS)) {
            String idStr = destination.substring(PREFIX_CLASS.length()).trim();
            if (studentClass.getClassId() != null && idStr.equals(String.valueOf(studentClass.getClassId()))) {
                return message;
            }
            log.warn("Task subscribe rejected: student {} classId {} not match destination {}", userId, studentClass.getClassId(), destination);
            return null;
        }

        log.warn("Task subscribe rejected: unknown destination={}", destination);
        return null;
    }
}
