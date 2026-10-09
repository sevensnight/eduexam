package com.eduexam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduexam.domain.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
