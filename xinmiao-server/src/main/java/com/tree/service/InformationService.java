package com.tree.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tree.dto.AddInformationDto;
import com.tree.dto.InformationQueryDto;
import com.tree.dto.InformationSimpleShowDto;
import com.tree.entity.Information;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InformationService {

    void deleteInformationById(Long id);

    Page<Information> page(InformationQueryDto queryDTO, Integer pageNum, Integer pageSize);

    void updateInformation(Information information);

    void addInformation(AddInformationDto addInformationDto, MultipartFile[] images);

    List<InformationSimpleShowDto> getInformationByUserId(Long id);

    Information getInformationById(Long id);
}
