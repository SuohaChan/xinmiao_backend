package com.tree.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.dto.AddInformationDto;
import com.tree.dto.InformationQueryDto;
import com.tree.dto.InformationSimpleShowDto;
import com.tree.entity.Information;
import com.tree.entity.StudentClass;
import com.tree.exception.BusinessException;
import com.tree.mapper.InformationMapper;
import com.tree.service.InformationService;
import com.tree.service.StudentClassService;
import com.tree.util.CacheClient;
import com.tree.util.OssMultiImageUploadHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.tree.constant.RedisConstants.CACHE_INFORMATION_KEY;
import static com.tree.constant.RedisConstants.CACHE_INFORMATION_TTL_MINUTES;
import static com.tree.constant.RedisConstants.CACHE_NULL_TTL_MINUTES;
import static com.tree.constant.RedisConstants.LOCK_CACHE_INFORMATION_KEY;
import static com.tree.constant.RedisConstants.LOCK_CACHE_MUTEX_MAX_RETRIES;
import static com.tree.constant.RedisConstants.LOCK_CACHE_TTL_SECONDS;

import com.tree.result.ErrorCode;
import com.tree.utils.DateTimeUtils;

import java.util.List;

@Slf4j
@Service
public class InformationServiceImpl extends ServiceImpl<InformationMapper, Information> implements InformationService {
    // 每页默认10条数据（可通过参数覆盖）
    private static final Integer DEFAULT_PAGE_SIZE = 10;

    private final OssMultiImageUploadHelper ossMultiImageUploadHelper;
    private final CacheClient cacheClient;
    private final StudentClassService studentClassService;

    public InformationServiceImpl(OssMultiImageUploadHelper ossMultiImageUploadHelper,
                                  CacheClient cacheClient,
                                  StudentClassService studentClassService) {
        this.ossMultiImageUploadHelper = ossMultiImageUploadHelper;
        this.cacheClient = cacheClient;
        this.studentClassService = studentClassService;
    }

    /**
     * 添加资讯
     *
     * @param addInformationDto images 资讯实体
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addInformation(AddInformationDto addInformationDto, MultipartFile[] images) {
        List<String> imageUrls = ossMultiImageUploadHelper.uploadImagesOrThrow(images);
        Information information = BeanUtil.copyProperties(addInformationDto, Information.class);
        if (!imageUrls.isEmpty()) {
            information.setImageUrls(String.join(",", imageUrls));
        }
        boolean saved = save(information);
        if (!saved) {
            ossMultiImageUploadHelper.rollback(imageUrls);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资讯创建失败");
        }
    }

    /**
     * 根据ID删除资讯
     *
     * @param id 资讯ID
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInformationById(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "ID不能为空");
        boolean removed = removeById(id);
        if (removed) cacheClient.delete(CACHE_INFORMATION_KEY, id);
        if (!removed) throw new BusinessException(ErrorCode.NOT_FOUND, "资讯不存在或已删除");
    }

    /**
     * 条件分页查询资讯
     *
     * @param queryDTO 查询条件
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页查询结果
     */
    @Override
    public Page<Information> page(InformationQueryDto queryDTO, Integer pageNum, Integer pageSize) {
        // 处理默认分页参数
        pageNum = (pageNum == null || pageNum <= 0) ? 1 : pageNum;
        pageSize = (pageSize == null || pageSize <= 0) ? DEFAULT_PAGE_SIZE : pageSize;

        // 构建查询条件
        LambdaQueryWrapper<Information> queryWrapper = new LambdaQueryWrapper<>();

        // ID精确查询
        if (queryDTO.getId() != null) {
            queryWrapper.eq(Information::getId, queryDTO.getId());
        }

        // 标题模糊查询
        if (queryDTO.getTitle() != null && !queryDTO.getTitle().trim().isEmpty()) {
            queryWrapper.like(Information::getTitle, queryDTO.getTitle().trim());
        }

        // 发布状态查询
        if (queryDTO.getIsPublished() != null) {
            queryWrapper.eq(Information::getIsPublished, queryDTO.getIsPublished());
        }

        // 创建时间范围查询（大于等于传入时间）
        LocalDateTime queryStartTime = DateTimeUtils.parse(queryDTO.getCreateTime());
        if (queryStartTime != null) {
            queryWrapper.ge(Information::getCreateTime, queryStartTime);
        }

        Page<Information> page = new Page<>(pageNum, pageSize);
        baseMapper.selectPage(page, queryWrapper);
        return page;
    }

    /**
     * 更新资讯
     *
     * @param information 资讯实体（需包含ID）
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInformation(Information information) {
        if (information.getId() == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "ID不能为空");
        Information existing = getById(information.getId());
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "资讯不存在");
        boolean updated = updateById(information);
        if (updated) cacheClient.delete(CACHE_INFORMATION_KEY, information.getId());
        if (!updated) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资讯更新失败");
    }

    @Override
    public List<InformationSimpleShowDto> getInformationByUserId(Long id) {
        StudentClass studentClass = studentClassService.searchClassByStudentId(id);
        log.info("学生班级信息: {}", studentClass);

        LambdaQueryWrapper<Information> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Information::getIsPublished, 1);
        if (studentClass == null) {
            queryWrapper.eq(Information::getScope, "校级");
        } else {
            queryWrapper.and(wrapper -> wrapper
                    .eq(Information::getScope, "校级")
                    .or(w -> w.eq(Information::getScope, "院级")
                            .eq(Information::getCollege, studentClass.getCollege()))
                    .or(w -> w.eq(Information::getScope, "班级")
                            .eq(Information::getCollege, studentClass.getCollege())
                            .eq(Information::getClazz, studentClass.getClazz()))
            );
        }
        queryWrapper.select(Information::getId, Information::getTitle, Information::getIsRequired,
                Information::getUpdateTime, Information::getImageUrls, Information::getScope, Information::getCollege, Information::getClazz);

        List<Information> informationList = baseMapper.selectList(queryWrapper);
        List<InformationSimpleShowDto> dtoList = informationList.stream()
                .map(information -> BeanUtil.copyProperties(information, InformationSimpleShowDto.class))
                .toList();
        log.info("查询到的资讯数量: {}", dtoList.size());
        return dtoList;
    }

    @Override
    public Information getInformationById(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "参数id不能为空");
        try {
            Information information = cacheClient.queryWithMutex(
                    CACHE_INFORMATION_KEY,
                    id,
                    Information.class,
                    this::getById,
                    CACHE_INFORMATION_TTL_MINUTES,
                    TimeUnit.MINUTES,
                    CACHE_NULL_TTL_MINUTES,
                    TimeUnit.MINUTES,
                    LOCK_CACHE_INFORMATION_KEY,
                    LOCK_CACHE_TTL_SECONDS,
                    LOCK_CACHE_MUTEX_MAX_RETRIES
            );
            if (information == null) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到对应信息");
            return information;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询资讯详情失败 id={}", id, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "查询信息失败");
        }
    }
}
