package com.ailk.ecaop.common.utils;

import java.util.Map;

import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;

public class SuniTradeUtils {

    public Map createSubItem4UserAttr(String itemId, Object key, Object value) {
        return createSubItem4UserAttr(itemId, key, value, DateUtils.getDate());
    }

    public Map createSubItem4UserAttr(String itemId, Object key, Object value, String startDate, String endDate) {
        return MapUtils.asMap("attrTypeCode", "3", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createSubItem4UserAttr(String itemId, Object key, Object value, String startDate) {
        return createSubItem4UserAttr(itemId, key, value, startDate, MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
    }

    public Map createSubItem4SD(String itemId, Object key, Object value) {
        return createSubItem4SD(itemId, key, value, DateUtils.getDate());
    }

    public Map createSubItem4SD(String itemId, Object key, Object value, String startDate) {
        return createSubItem4SD(itemId, key, value, startDate, MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
    }

    public Map createSubItem4SD(String itemId, Object key, Object value, String startDate, String endDate) {
        return MapUtils.asMap("attrTypeCode", "S", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", endDate);
    }

}
