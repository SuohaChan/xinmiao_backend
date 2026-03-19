package com.tree.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.AddNoticeDto;
import com.tree.dto.NoticeQueryDto;
import com.tree.dto.NoticeSimpleShowDto;
import com.tree.entity.Notice;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NoticeService extends IService<Notice> {

    void addNotice(AddNoticeDto addNoticeDTO, MultipartFile[] images);

    void deleteNoticeById(Long id);

    Page<Notice> page(NoticeQueryDto queryDTO, Integer pageNum, Integer pageSize);

    List<NoticeSimpleShowDto> getNoticeByUserId(Long userId);

    Notice getNoticeById(Long id);

    List<Notice> getNoticeByCounselorId(Long userId);
}
