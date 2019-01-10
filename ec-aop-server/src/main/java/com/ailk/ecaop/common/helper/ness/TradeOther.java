package com.ailk.ecaop.common.helper.ness;

/**
 * 此工具类存放cbss统一预提交接口中TRADE_OTHER节点的相关属性
 * 注意按字母排序
 * 
 * @author Steven
 */
public class TradeOther {

    // 结束时间 通常为本月最后一天的23时59分59秒
    public static final String endDate = "END_DATE";

    // 状态属性：0－增加 1－删除 2－修改
    public static final String modifyTag = "MODIFY_TAG";

    // 通常为NEXP
    public static final String rsrvValueCode = "RSRV_VALUE_CODE";

    // 通常为USER_ID
    public static final String rsrvValue = "RSRV_VALUE";

    // 要退订的产品对应的ID
    public static final String rsrvStr1 = "RSRV_STR1";

    // 要退订的产品对应的PRODUCT_MODE
    public static final String rsrvStr2 = "RSRV_STR2";

    // 北六ESS给的报文样例多半为-9，不知何意
    public static final String rsrvStr3 = "RSRV_STR3";

    // 要退订的产品对应PRODUCT_TYPE_CODE
    public static final String rsrvStr4 = "RSRV_STR4";

    // 北六ESS给的样例是undefined
    public static final String rsrvStr5 = "RSRV_STR5";

    // 北六ESS给的样例是-1
    public static final String rsrvStr6 = "RSRV_STR6";

    // 北六ESS给的样例是0
    public static final String rsrvStr7 = "RSRV_STR7";
    //
    public static final String rsrvStr8 = "RSRV_STR8";
    // 要退订的产品对应的BRAND_CODE
    public static final String rsrvStr9 = "RSRV_STR9";

    public static final String rsrvStr10 = "RSRV_STR10";
    public static final String rsrvStr11 = "RSRV_STR11";
    public static final String rsrvStr12 = "RSRV_STR12";
    public static final String rsrvStr13 = "RSRV_STR13";
    public static final String rsrvStr14 = "RSRV_STR14";
    public static final String rsrvStr15 = "RSRV_STR15";
    public static final String rsrvStr16 = "RSRV_STR16";
    public static final String rsrvStr17 = "RSRV_STR17";
    public static final String rsrvStr18 = "RSRV_STR18";
    public static final String rsrvStr19 = "RSRV_STR19";
    public static final String rsrvStr20 = "RSRV_STR20";

    // 开始时间 三户接口可以返回
    public static final String startDate = "START_DATE";

    // 北六ESS给的样例是NULL
    public static final String xDatatype = "X_DATATYPE";
}
