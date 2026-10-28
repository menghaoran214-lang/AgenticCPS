package com.qiji.cps.framework.mybatis.core.handler;

import com.qiji.cps.framework.mybatis.core.dataobject.BaseDO;
import com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 通用参数填充实现类
 *
 * 如果没有显式的对通用参数进行赋值，这里会对通用参数进行填充、赋值
 *
 * @author hexiaowu
 */
public class DefaultDBFieldHandler implements MetaObjectHandler {

    @Override
    @SuppressWarnings("PatternVariableCanBeUsed")
    public void insertFill(MetaObject metaObject) {
        if (Objects.nonNull(metaObject) && metaObject.getOriginalObject() instanceof BaseDO) {
            BaseDO baseDO = (BaseDO) metaObject.getOriginalObject();

            LocalDateTime current = LocalDateTime.now();
            // 创建时间为空，则以当前时间为插入时间
            if (Objects.isNull(baseDO.getCreateTime())) {
                baseDO.setCreateTime(current);
            }
            // 更新时间为空，则以当前时间为更新时间
            if (Objects.isNull(baseDO.getUpdateTime())) {
                baseDO.setUpdateTime(current);
            }

            // 登录前的认证/注册写入没有 SecurityContext；使用保留的 0 表示系统动作。
            // 不得将空值写入审计字段：正式 MySQL 8 结构强制 creator/updater NOT NULL。
            String actor = currentActor();
            if (Objects.isNull(baseDO.getCreator())) {
                baseDO.setCreator(actor);
            }
            if (Objects.isNull(baseDO.getUpdater())) {
                baseDO.setUpdater(actor);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时间为空，则以当前时间为更新时间
        Object modifyTime = getFieldValByName("updateTime", metaObject);
        if (Objects.isNull(modifyTime)) {
            setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }

        // 密码登录成功后的最后登录时间更新仍在 Token 签发前执行。
        // 未登录时也必须填写审计值，而不是向 NOT NULL 的 updater 列写入 NULL。
        Object modifier = getFieldValByName("updater", metaObject);
        if (Objects.isNull(modifier)) {
            setFieldValByName("updater", currentActor(), metaObject);
        }
    }

    private static String currentActor() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "0" : userId.toString();
    }
}
