package com.tree.util;

import com.aliyun.oss.ClientException;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.utils.AliOSSUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 多图上传 OSS 公共逻辑：依次上传、收集 URL；任一步失败则回滚已上传文件并重新抛出异常。
 * 供资讯、通知等「添加 + 多图上传 + 失败回滚」场景复用。
 */
@Slf4j
@Component
public class OssMultiImageUploadHelper {

    private final AliOSSUtils aliOSSUtils;

    public OssMultiImageUploadHelper(AliOSSUtils aliOSSUtils) {
        this.aliOSSUtils = aliOSSUtils;
    }

    /**
     * 上传多张图片，返回成功上传的 URL 列表。任一张上传失败则删除已上传的并重新抛出异常。
     *
     * @param images 图片文件数组，可为 null 或空
     * @return 上传成功的 URL 列表，无图时返回空列表
     */
    public List<String> uploadImages(MultipartFile[] images) throws Exception {
        List<String> imageUrls = new ArrayList<>();
        if (images == null || images.length == 0) {
            return imageUrls;
        }
        try {
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    String url = aliOSSUtils.upload(image);
                    imageUrls.add(url);
                    log.info("图片上传成功：{}", url);
                }
            }
            return imageUrls;
        } catch (Exception e) {
            rollback(imageUrls);
            throw e;
        }
    }

    /**
     * 上传多张图片，异常统一转换为 BusinessException。供资讯、通知等业务 Service 直接调用，避免重复 try-catch。
     *
     * @param images 图片文件数组，可为 null 或空
     * @return 上传成功的 URL 列表，无图时返回空列表
     */
    public List<String> uploadImagesOrThrow(MultipartFile[] images) {
        try {
            return uploadImages(images);
        } catch (IOException e) {
            log.error("图片文件读取失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败：文件无法读取");
        } catch (ClientException e) {
            log.error("阿里云OSS上传失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败：OSS服务错误");
        } catch (com.aliyuncs.exceptions.ClientException e) {
            log.error("阿里云OSS异常", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败");
        } catch (IllegalArgumentException e) {
            log.warn("图片上传校验未通过: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PARAM_INVALID, e.getMessage() != null ? e.getMessage() : "图片上传校验未通过");
        } catch (Exception e) {
            log.error("图片上传异常", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败");
        }
    }

    /** 回滚：删除已上传到 OSS 的 URL，失败仅打日志 */
    public void rollback(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        for (String url : imageUrls) {
            try {
                aliOSSUtils.deleteByUrl(url);
                log.info("回滚删除OSS文件: {}", url);
            } catch (Exception ex) {
                log.warn("回滚删除OSS文件失败，请人工清理: {}", url, ex);
            }
        }
    }
}
