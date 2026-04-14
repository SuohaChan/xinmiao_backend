package com.tree.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.dto.AddNoticeDto;
import com.tree.dto.NoticeQueryDto;
import com.tree.dto.NoticeSimpleShowDto;
import com.tree.entity.Notice;
import com.tree.entity.StudentClass;
import com.tree.exception.BusinessException;
import com.tree.mapper.NoticeMapper;
import com.tree.service.NoticeService;
import com.tree.service.StudentClassService;
import com.tree.util.CacheClient;
import com.tree.util.DbFallbackLimiter;
import com.tree.util.OssMultiImageUploadHelper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tree.utils.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.tree.constant.RedisConstants.CACHE_NOTICE_KEY;
import static com.tree.constant.RedisConstants.CACHE_NOTICE_TTL_MINUTES;
import static com.tree.constant.RedisConstants.CACHE_NULL_TTL_MINUTES;
import static com.tree.constant.RedisConstants.LOCK_CACHE_NOTICE_KEY;
import static com.tree.constant.RedisConstants.LOCK_CACHE_TTL_SECONDS;
import static com.tree.constant.RedisConstants.LOCK_CACHE_WAIT_SECONDS;
import java.util.List;

import com.tree.result.ErrorCode;

/**
 * 通知服务实现类
 * 处理通知相关的业务逻辑
 */
@Slf4j
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {
    private static final Integer DEFAULT_PAGE_SIZE = 10;

    private final OssMultiImageUploadHelper ossMultiImageUploadHelper;
    private final CacheClient cacheClient;
    private final RedissonClient redissonClient;
    private final StudentClassService studentClassService;
    private final DbFallbackLimiter dbFallbackLimiter;

    public NoticeServiceImpl(OssMultiImageUploadHelper ossMultiImageUploadHelper,
                             CacheClient cacheClient,
                             RedissonClient redissonClient,
                             StudentClassService studentClassService,
                             DbFallbackLimiter dbFallbackLimiter) {
        this.ossMultiImageUploadHelper = ossMultiImageUploadHelper;
        this.cacheClient = cacheClient;
        this.redissonClient = redissonClient;
        this.studentClassService = studentClassService;
        this.dbFallbackLimiter = dbFallbackLimiter;
    }

    /**
     * 添加通知
     * 
     * @param addNoticeDTO 通知文本参数
     * @param images 通知图片文件
     * @return Result对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addNotice(AddNoticeDto addNoticeDTO, MultipartFile[] images) {
        List<String> imageUrls = ossMultiImageUploadHelper.uploadImagesOrThrow(images);
        Notice notice = BeanUtil.copyProperties(addNoticeDTO, Notice.class);
        if (!imageUrls.isEmpty()) {
            notice.setImageUrls(String.join(",", imageUrls));
        }
        boolean saved = save(notice);
        if (!saved) {
            ossMultiImageUploadHelper.rollback(imageUrls);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "通知创建失败");
        }
    }

    /**
     * 删除通知
     * 
     * @param id 通知ID
     * @return Result对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNoticeById(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "ID不能为空");
        // 先删缓存再删 DB，避免先删 DB 后删缓存前的时间窗口内读到脏数据
        cacheClient.delete(CACHE_NOTICE_KEY, id);
        boolean removed = removeById(id);
        if (!removed) throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在或已删除");
    }

    /**
     * 条件分页查询通知
     * 
     * @param queryDTO 查询条件
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return Result对象
     */
    @Override
    public Page<Notice> page(NoticeQueryDto queryDTO, Integer pageNum, Integer pageSize) {
        pageNum = (pageNum == null || pageNum <= 0) ? 1 : pageNum;
        pageSize = (pageSize == null || pageSize <= 0) ? DEFAULT_PAGE_SIZE : pageSize;

        LambdaQueryWrapper<Notice> queryWrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getId() != null) {
            queryWrapper.eq(Notice::getId, queryDTO.getId());
        }
        if (queryDTO.getTitle() != null && !queryDTO.getTitle().trim().isEmpty()) {
            queryWrapper.like(Notice::getTitle, queryDTO.getTitle().trim());
        }
        if (queryDTO.getIsPublished() != null) {
            queryWrapper.eq(Notice::getIsPublished, queryDTO.getIsPublished());
        }

        LocalDateTime queryStart = DateTimeUtils.parse(queryDTO.getCreateTime());
        if (queryStart != null) {
            queryWrapper.ge(Notice::getCreateTime, queryStart);
        }

        Page<Notice> page = new Page<>(pageNum, pageSize);
        baseMapper.selectPage(page, queryWrapper);
        return page;
    }

    /**
     * 根据学生用户ID获取相关通知列表
     * 
     * @param userId 学生用户ID
     * @return Result对象
     */
    @Override
    public List<NoticeSimpleShowDto> getNoticeByUserId(Long userId) {
        StudentClass studentClass = studentClassService.searchClassByStudentId(userId);
        log.info("学生班级信息: {}", studentClass);

        LambdaQueryWrapper<Notice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Notice::getIsPublished, 1);
        if (studentClass == null) {
            queryWrapper.eq(Notice::getScope, "校级");
        } else {
            queryWrapper.and(wrapper -> wrapper
                    .eq(Notice::getScope, "校级")
                    .or(w -> w.eq(Notice::getScope, "院级")
                            .eq(Notice::getCollege, studentClass.getCollege()))
                    .or(w -> w.eq(Notice::getScope, "班级")
                            .eq(Notice::getCollege, studentClass.getCollege())
                            .eq(Notice::getClazz, studentClass.getClazz()))
            );
        }
        queryWrapper.select(
                Notice::getId, Notice::getTitle, Notice::getIsRequired,
                Notice::getUpdateTime, Notice::getImageUrls,
                Notice::getScope, Notice::getCollege, Notice::getClazz
        );

        List<Notice> noticeList = baseMapper.selectList(queryWrapper);
        List<NoticeSimpleShowDto> dtoList = noticeList.stream()
                .map(notice -> BeanUtil.copyProperties(notice, NoticeSimpleShowDto.class))
                .toList();
        log.info("查询到的通知数量: {}", dtoList.size());
        return dtoList;
    }

    /**
     * 根据通知ID获取通知详情。
     * 缓存未命中时使用 Redisson 分布式锁（有界等待 + 持有时长上限）防击穿，仅一个线程查 DB 并回填缓存。
     * 拿不到锁时降级直接查 DB，避免无限阻塞。
     *
     * @param id 通知ID
     * @return 通知详情，不存在时抛出 NOT_FOUND
     */
    @Override
    public Notice getNoticeById(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "参数id不能为空");
        try {
            // 1. 先查缓存，命中则直接返回
            if (cacheClient.exists(CACHE_NOTICE_KEY, id)) {
                Notice cached = cacheClient.get(CACHE_NOTICE_KEY, id, Notice.class);
                if (cached != null) return cached;
                throw new BusinessException(ErrorCode.NOT_FOUND, "未找到对应信息");
            }
            // 2. 未命中：Redisson 锁（有界等待 + 持有时长上限），拿不到锁时降级直接查 DB
            String lockKey = LOCK_CACHE_NOTICE_KEY + id;
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked;
            try {
                locked = lock.tryLock(LOCK_CACHE_WAIT_SECONDS, LOCK_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("通知缓存锁被中断，回源限流内查 DB id={}", id);
                Notice notice = dbFallbackLimiter.runWithFallbackPermit(() -> getById(id));
                if (notice == null) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到对应信息");
                return notice;
            }
            if (!locked) {
                log.debug("通知缓存锁等待超时，回源限流内查 DB id={}", id);
                Notice notice = dbFallbackLimiter.runWithFallbackPermit(() -> getById(id));
                if (notice == null) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到对应信息");
                return notice;
            }
            try {
                // 3. 双重检查
                if (cacheClient.exists(CACHE_NOTICE_KEY, id)) {
                    Notice cached = cacheClient.get(CACHE_NOTICE_KEY, id, Notice.class);
                    if (cached != null) return cached;
                    throw new BusinessException(ErrorCode.NOT_FOUND, "未找到对应信息");
                }
                Notice notice = dbFallbackLimiter.runWithFallbackPermit(() -> getById(id));
                if (notice == null) {
                    cacheClient.set(CACHE_NOTICE_KEY + id, "", CACHE_NULL_TTL_MINUTES, TimeUnit.MINUTES);
                    throw new BusinessException(ErrorCode.NOT_FOUND, "未找到对应信息");
                }
                cacheClient.set(CACHE_NOTICE_KEY + id, notice, CACHE_NOTICE_TTL_MINUTES, TimeUnit.MINUTES);
                return notice;
            } finally {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.debug("释放锁时异常（可能已过期）key={} {}", lockKey, e.getMessage());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询通知详情失败 id={}", id, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "查询信息失败");
        }
    }

    /**
     * 根据辅导员用户ID获取通知列表
     * 
     * @param userId 辅导员用户ID
     * @return Result对象
     */
    @Override
    public List<Notice> getNoticeByCounselorId(Long userId) {
        LambdaQueryWrapper<Notice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Notice::getIsPublished, 1);
        queryWrapper.select(
                Notice::getId, Notice::getTitle, Notice::getIsRequired,
                Notice::getUpdateTime, Notice::getDescription
        );
        return baseMapper.selectList(queryWrapper);
    }

}