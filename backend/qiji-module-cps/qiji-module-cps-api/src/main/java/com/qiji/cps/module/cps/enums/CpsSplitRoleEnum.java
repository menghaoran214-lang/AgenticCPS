package com.qiji.cps.module.cps.enums;

import com.qiji.cps.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * CPS 多级分账角色枚举
 *
 * @author CPS System
 */
@Getter
@AllArgsConstructor
public enum CpsSplitRoleEnum implements ArrayValuable<String> {

    SELF("SELF", "自购返利"),
    PARENT_L1("PARENT_L1", "一级推广提成"),
    GRANDPARENT_L2("GRANDPARENT_L2", "二级推广提成"),
    PARTNER("PARTNER", "合伙人级差"),
    PLATFORM("PLATFORM", "平台留存"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values()).map(CpsSplitRoleEnum::getRole).toArray(String[]::new);

    /**
     * 分账角色代码
     */
    private final String role;
    /**
     * 角色名称
     */
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
