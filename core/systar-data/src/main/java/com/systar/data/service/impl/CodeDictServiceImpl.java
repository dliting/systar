package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.CodeDictEntity;
import com.systar.data.mapper.CodeDictMapper;
import com.systar.data.service.CodeDictService;
import org.springframework.stereotype.Service;

/**
 * Code dictionary service implementation.
 */
@Service
public class CodeDictServiceImpl extends ServiceImpl<CodeDictMapper, CodeDictEntity>
        implements CodeDictService {
}
