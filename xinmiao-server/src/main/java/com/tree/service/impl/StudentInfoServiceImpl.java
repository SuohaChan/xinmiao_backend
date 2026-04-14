package com.tree.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.constant.SystemConstants;
import com.tree.context.StudentHolder;
import com.tree.dto.FaceDto;
import com.tree.dto.StudentInfoShowDto;
import com.tree.entity.StudentClass;
import com.tree.entity.StudentInfo;
import com.tree.exception.BusinessException;
import com.tree.mapper.StudentInfoMapper;
import com.tree.service.StudentClassService;
import com.tree.service.StudentInfoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import com.tree.result.ErrorCode;

/**
 * @author ljx
 * @description 针对表【tb_student_info(学生信息)】的数据库操作Service实现
 * @createDate 2024-02-17 14:26:22
 */
@Service
public class StudentInfoServiceImpl extends ServiceImpl<StudentInfoMapper, StudentInfo> implements StudentInfoService {

    private final RestTemplate restTemplate;
    private final StudentClassService studentClassService;

    public StudentInfoServiceImpl(RestTemplate restTemplate, StudentClassService studentClassService) {
        this.restTemplate = restTemplate;
        this.studentClassService = studentClassService;
    }

    @Override
    public void validate(HttpServletRequest request, StudentInfo studentInfo) {
        String idNumber = studentInfo.getIdNumber();
        String admissionNumber = studentInfo.getAdmissionNumber();
        boolean exists = lambdaQuery().eq(StudentInfo::getIdNumber, idNumber).eq(StudentInfo::getAdmissionNumber, admissionNumber).exists();
        if (!exists) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "身份证号码或录取通知书编号错误，验证失败！");
        }
        request.getSession().setAttribute("numberValidated", true);
        request.getSession().setAttribute("idNumber", idNumber);
    }

    @Override
    public Boolean checkFace(HttpServletRequest request, MultipartFile faceFile) throws IOException {
        FaceDto faceDto = new FaceDto();
        Long id = StudentHolder.getStudent().getId();

        StudentInfo info = getById(id);
        String storedFace = info.getFace();
        faceDto.setSrc(storedFace);
        String filename = UUID.randomUUID() + ".jpg";
        String faceToCheck = SystemConstants.TEMP_IMAGE_PATH_PREFIX + filename;
        faceFile.transferTo(new File(SystemConstants.TEMP_IMAGE_PATH_PREFIX + filename));
        faceDto.setCheck(faceToCheck);

        ResponseEntity<Boolean> response = restTemplate.postForEntity("http://localhost:8000/face", faceDto, Boolean.class);
        Boolean success = response.getBody();
        request.getSession().setAttribute("faceValidated", success);
        return success;
    }

    @Override
    public StudentInfo getByIdOrThrow(Long id) {
        StudentInfo info = getById(id);
        if (info == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生信息不存在");
        }
        return info;
    }

    @Override
    public StudentInfoShowDto getCurrentStudentInfo(Long studentId) {
        StudentInfo info = getByIdOrThrow(studentId);
        StudentInfoShowDto dto = BeanUtil.copyProperties(info, StudentInfoShowDto.class);
        StudentClass sc = studentClassService.searchClassByStudentId(studentId);
        if (sc != null) {
            dto.setCollegeId(sc.getCollegeId());
            dto.setClassId(sc.getClassId());
            dto.setSchool(sc.getCollege());
        }
        return dto;
    }

    @Override
    public void updateStudentInfo(StudentInfo studentInfo) {
        if (studentInfo.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "id不能为空");
        }
        getByIdOrThrow(studentInfo.getId());
        boolean updated = updateById(studentInfo);
        if (!updated) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "更新失败");
        }
    }

    @Override
    public void updateCurrentStudentInfo(Long studentId, StudentInfo studentInfo) {
        studentInfo.setId(studentId);
        getByIdOrThrow(studentId);
        boolean updated = updateById(studentInfo);
        if (!updated) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "更新失败");
        }
    }
}




