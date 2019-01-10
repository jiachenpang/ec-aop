package com.ailk.ecaop.common.utils;

import java.util.Map;

public class SglUniTradeUtils {

    // 默认用户属性
    public Map createTradeSubItem(String itemId, Object key, Object value) {
        return createTradeSubItem(itemId, key, value, "0");
    }

    // 支持填写属性类型
    // 0：台帐用户子表1：台帐产品子表2：台帐服务子表3：台帐资费子表
    public Map createTradeSubItem(String itemId, Object key, Object value, String attrType) {
        return createTradeSubItem(itemId, key, value, attrType, GetDateUtils.getDate());
    }

    // 支持开始时间的设置
    public Map createTradeSubItem(String itemId, Object key, Object value, String attrType, String startDate) {
        return createTradeSubItem(itemId, key, value, attrType, startDate, "20501231235959");
    }

    // 支持结束时间的设置
    public Map createTradeSubItem(String itemId, Object key, Object value, String attrType, String startDate,
            String endDate) {
        return MapUtils.asMap("xDatatype", "NULL", "itemId", itemId, "attrCode", key,
                "attrValue", value, "attrTypeCode", attrType, "startDate", startDate, "endDate", endDate);
    }

    // 生效方式 0.立即 1.次月
    public String dealStartDate(Object flag) {
        if (null != flag && "1".equals(flag)) {
            return GetDateUtils.getNextMonthDate();
        }
        return GetDateUtils.getDate();
    }
}
