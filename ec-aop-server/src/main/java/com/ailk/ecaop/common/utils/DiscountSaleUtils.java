package com.ailk.ecaop.common.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

/**
 * @author 代理商折扣销售共用方法
 */
public class DiscountSaleUtils {

    /**
     * 下发减免费用信息
     * msg中要有itemList和subItemList
     * by wangmc 20161230
     * 
     * @param msg
     * @param acceptDate
     */
    public static void preDiscountFeeForSubItem(Map msg, String acceptDate) {
        if (IsEmptyUtils.isEmpty(msg.get("discountFee"))) {
            return;
        }
        if (IsEmptyUtils.isEmpty(msg.get("batDeveStaffId")) || IsEmptyUtils.isEmpty(msg.get("batDeveDepatId"))) {
            throw new EcAopServerBizException("折扣销售必须传入[领用代理商工号]和[领用代理商部门],请检查!");
        }
        List<Map> subItemList = (List<Map>) msg.get("subItemList");

        Map tempMap = new HashMap();
        tempMap.put("xDatatype", "NULL");
        // tempMap.put("startDate", acceptDate);
        // tempMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        Object discountFee = TransFeeUtils.transFee(msg.get("discountFee"), -1);

        Map item = new HashMap(tempMap);
        item.put("attrTypeCode", "0");
        item.put("itemId", msg.get("userId"));
        item.put("attrValue", discountFee);
        item.put("attrCode", "DISCNT_DISCOUNT_SELL_BILL");
        subItemList.add(item);
        item = new HashMap(tempMap);
        item.put("attrTypeCode", "3");
        item.put("itemId", msg.get("userId"));
        item.put("attrValue", discountFee);
        item.put("attrCode", "DISCNT_DISCOUNT_SELL");
        subItemList.add(item);

        List<Map> itemList = (List<Map>) msg.get("itemList");
        item = new HashMap(tempMap);
        item.put("attrCode", "BAT_DEVE_STAFF_ID");
        item.put("attrValue", msg.get("batDeveStaffId"));
        itemList.add(item);
        item = new HashMap(tempMap);
        item.put("attrCode", "BAT_DEVE_DEPART_ID");
        item.put("attrValue", msg.get("batDeveDepatId"));
        itemList.add(item);
    }

    /**
     * 主副卡销售时使用
     * 
     * @param msg
     * @param ext
     */
    public static void preDiscountFeeForSubItem(Map msg, Map ext) {
        if (IsEmptyUtils.isEmpty(msg.get("discountFee"))) {
            return;
        }
        if (IsEmptyUtils.isEmpty(msg.get("batDeveStaffId")) || IsEmptyUtils.isEmpty(msg.get("batDeveDepatId"))) {
            throw new EcAopServerBizException("折扣销售必须传入[领用代理商工号]和[领用代理商部门],请检查!");
        }
        Map tradeSubItem = (Map) ext.get("tradeSubItem");
        List<Map> subItemList = (List<Map>) tradeSubItem.get("item");

        Map tempMap = new HashMap();
        tempMap.put("xDatatype", "NULL");
        // tempMap.put("startDate", msg.get("date"));
        // tempMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);

        Map item = new HashMap(tempMap);
        item.put("attrTypeCode", "0");
        item.put("itemId", msg.get("userId"));
        item.put("attrValue", msg.get("discountFee"));
        item.put("attrCode", "DISCNT_DISCOUNT_SELL_BILL");
        subItemList.add(item);
        item = new HashMap(tempMap);
        item.put("attrTypeCode", "3");
        item.put("itemId", msg.get("userId"));
        item.put("attrValue", msg.get("discountFee"));
        item.put("attrCode", "DISCNT_DISCOUNT_SELL");
        subItemList.add(item);
        tradeSubItem.put("item", subItemList);
        ext.put("tradeSubItem", tradeSubItem);

        Map tradeItem = (Map) ext.get("tradeItem");
        List<Map> itemList = (List<Map>) tradeItem.get("item");
        item = new HashMap(tempMap);
        item.put("attrCode", "BAT_DEVE_STAFF_ID");
        item.put("attrValue", msg.get("batDeveStaffId"));
        itemList.add(item);
        item = new HashMap(tempMap);
        item.put("attrCode", "BAT_DEVE_DEPART_ID");
        item.put("attrValue", msg.get("batDeveDepatId"));
        itemList.add(item);
        tradeItem.put("item", itemList);
        ext.put("tradeItem", tradeItem);
    }
}
