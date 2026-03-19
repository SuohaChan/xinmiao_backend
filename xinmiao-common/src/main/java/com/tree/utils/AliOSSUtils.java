package com.tree.utils;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyuncs.exceptions.ClientException;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * 阿里云 OSS 工具类。若容器中注入了 OSS 单例则复用，否则每次调用时创建并关闭客户端。
 */
@Data
@Component
public class AliOSSUtils {

    private final OSS sharedOssClient;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.bucketName}")
    private String bucketName;
    @Value("${aliyun.oss.region}")
    private String region;
    @Value("${aliyun.oss.allowed-content-types:image/jpeg,image/png,image/gif,image/webp}")
    private String allowedContentTypes;
    @Value("${aliyun.oss.max-file-size:5242880}")
    private long maxFileSize;

    public AliOSSUtils(@Autowired(required = false) OSS sharedOssClient) {
        this.sharedOssClient = sharedOssClient;
    }

    /** 允许的 Content-Type 集合（小写），用于白名单校验 */
    private Set<String> getAllowedTypesSet() {
        return Arrays.stream(allowedContentTypes.split("\\s*,\\s*"))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * 实现上传图片到OSS（校验白名单与大小后上传）
     */
    public String upload(MultipartFile file) throws IOException, ClientException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件大小不能超过 " + (maxFileSize / 1024 / 1024) + "MB");
        }
        Set<String> allowed = getAllowedTypesSet();
        String contentType = file.getContentType();
        if (contentType == null || !allowed.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("仅支持上传图片类型：jpeg、png、gif、webp");
        }

        InputStream inputStream = file.getInputStream();
        String originalFilename = file.getOriginalFilename();
        int lastDot = (originalFilename != null && originalFilename.length() > 0)
                ? originalFilename.lastIndexOf('.') : -1;
        String ext = (originalFilename != null && lastDot > 0 && lastDot < originalFilename.length() - 1)
                ? originalFilename.substring(lastDot)
                : "";
        String fileName = UUID.randomUUID().toString().substring(0, 8) + ext;

        OSS oss = sharedOssClient;
        boolean needShutdown = false;
        if (oss == null) {
            EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
            ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
            oss = OSSClientBuilder.create()
                    .endpoint(endpoint)
                    .credentialsProvider(credentialsProvider)
                    .clientConfiguration(clientBuilderConfiguration)
                    .region(region)
                    .build();
            needShutdown = true;
        }
        try {
            oss.putObject(bucketName, fileName, inputStream);
            String url = endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + fileName;
            return url;
        } finally {
            if (needShutdown) {
                oss.shutdown();
            }
        }
    }

    /**
     * 删除 OSS 上的文件（按 object key）
     *
     * @param fileName object key
     */
    public void deleteFile(String fileName) throws ClientException {
        OSS oss = sharedOssClient;
        boolean needShutdown = false;
        if (oss == null) {
            EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
            ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
            oss = OSSClientBuilder.create()
                    .endpoint(endpoint)
                    .credentialsProvider(credentialsProvider)
                    .clientConfiguration(clientBuilderConfiguration)
                    .region(region)
                    .build();
            needShutdown = true;
        }
        try {
            oss.deleteObject(bucketName, fileName);
        } finally {
            if (needShutdown) {
                oss.shutdown();
            }
        }
    }

    /**
     * 按访问 URL 删除 OSS 上的文件（用于部分成功时的回滚）
     *
     * @param url 上传时返回的完整访问 URL
     */
    public void deleteByUrl(String url) throws ClientException {
        if (url == null || url.isEmpty()) return;
        int lastSlash = url.lastIndexOf('/');
        String fileName = (lastSlash >= 0 && lastSlash < url.length() - 1) ? url.substring(lastSlash + 1) : url;
        if (!fileName.isEmpty()) {
            deleteFile(fileName);
        }
    }
}

