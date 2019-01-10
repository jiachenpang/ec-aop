package com.ailk.ecaop.common.helper.cbss;

import java.util.Map;

import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.utils.MapUtils;

public class TradeSubItemFactory {

    private final String attrTypeCode;

    protected TradeSubItemFactory(String attrTypeCode) {
        this.attrTypeCode = attrTypeCode;
    }

    public Map createItem(Object itemId, Object key) {
        return createItem(itemId, key, "");
    }

    public Map createItem(Object itemId, Object key, Object value) {
        return createItem(itemId, key, value, DateUtils.getDate());
    }

    public Map createItem(Object itemId, Object key, Object value, Object start) {
        return createItem(itemId, key, value, start, "20501231235959");
    }

    public Map createItem(Object itemId, Object key, Object value, Object start, Object end) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", attrTypeCode, "attrCode", key,
                "attrValue", value, "itemId", itemId, "startDate", start, "endDate", end);
    }
}
