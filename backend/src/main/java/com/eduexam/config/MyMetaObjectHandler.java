package com.eduexam.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private LocalDateTime now() {
        return LocalDateTime.now(APP_ZONE);
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "enrolledAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "addedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now());
    }
}
