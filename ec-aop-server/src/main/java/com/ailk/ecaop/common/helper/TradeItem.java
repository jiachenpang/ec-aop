package com.ailk.ecaop.common.helper;

/**
 * 此工具类存放cbss统一预提交接口中TRADE_ITEM节点的相关属性
 * 注意按字母排序
 * 
 * @author Steven
 */
public class TradeItem {

    // 下标索引，CBSS用于计费，值为1时返回费用
    public static final String ALONE_TCS_COMP_INDEX = "ALONE_TCS_COMP_INDEX";

    // 预约标记 1预约 0非预约
    public static final String BOOK_FLAG = "BOOK_FLAG";

    // 是否为融合订单 0:为融合订单
    public static final String COMP_DEAL_STATE = "COMP_DEAL_STATE";

    // 发展人部门
    public static final String DEVELOP_DEPART_ID = "DEVELOP_DEPART_ID";

    // 发展人标识
    public static final String DEVELOP_STAFF_ID = "DEVELOP_STAFF_ID";

    // 标识广东虚拟串码 0-真实串码 1-虚拟串码
    public static final String GDTEST = "GDTEST";

    // 融合业务主号码标记
    public static final String MAIN_USER_TAG = "MAIN_USER_TAG";

    // 预约装机时间
    public static final String PRE_START_TIME = "PRE_START_TIME";

    // 融合业务中的融合产品ID,在成员预提交下发
    public static final String REL_COMP_PROD_ID = "REL_COMP_PROD_ID";

    // 是否共线 取值Y N
    public static final String SFGX_2060 = "SFGX_2060";

    // 地市编码,4位 如北京:0010 承德:0314
    public static final String STANDARD_KIND_CODE = "STANDARD_KIND_CODE";

    // 沃受理流水号
    public static final String WORK_TRADE_ID = "WORK_TRADE_ID";
}
