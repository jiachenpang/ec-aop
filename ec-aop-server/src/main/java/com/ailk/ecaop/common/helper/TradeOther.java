package com.ailk.ecaop.common.helper;

/**
 * 此工具类存放cbss统一预提交接口中TRADE_OTHER节点的相关属性
 * 注意按字母排序
 * 
 * @author Steven
 */
public class TradeOther {

    // 结束时间 通常为本月最后一天的23时59分59秒
    public static final String endDate = "endDate";

    // 状态属性：0－增加 1－删除 2－修改
    public static final String modifyTag = "modifyTag";

    // 通常为NEXP
    public static final String rsrvValueCode = "rsrvValueCode";

    // 通常为USER_ID
    public static final String rsrvValue = "rsrvValue";

    // 要退订的产品对应的ID
    public static final String rsrvStr1 = "rsrvStr1";

    // 要退订的产品对应的PRODUCT_MODE
    public static final String rsrvStr2 = "rsrvStr2";

    // CBSS给的报文样例多半为-9，不知何意
    public static final String rsrvStr3 = "rsrvStr3";

    // 要退订的产品对应PRODUCT_TYPE_CODE
    public static final String rsrvStr4 = "rsrvStr4";

    // CBSS给的样例是undefined
    public static final String rsrvStr5 = "rsrvStr5";

    // CBSS给的样例是-1
    public static final String rsrvStr6 = "rsrvStr6";

    // CBSS给的样例是0
    public static final String rsrvStr7 = "rsrvStr7";
    //
    public static final String rsrvStr8 = "rsrvStr8";
    // 要退订的产品对应的BRAND_CODE
    public static final String rsrvStr9 = "rsrvStr9";

    public static final String rsrvStr10 = "rsrvStr10";
    public static final String rsrvStr11 = "rsrvStr11";
    public static final String rsrvStr12 = "rsrvStr12";
    public static final String rsrvStr13 = "rsrvStr13";
    public static final String rsrvStr14 = "rsrvStr14";
    public static final String rsrvStr15 = "rsrvStr15";
    public static final String rsrvStr16 = "rsrvStr16";
    public static final String rsrvStr17 = "rsrvStr17";
    public static final String rsrvStr18 = "rsrvStr18";
    public static final String rsrvStr19 = "rsrvStr19";
    public static final String rsrvStr20 = "rsrvStr20";

    // 开始时间 三户接口可以返回
    public static final String startDate = "startDate";

    // CBSS给的样例是NULL
    public static final String xDatatype = "xDatatype";
}
