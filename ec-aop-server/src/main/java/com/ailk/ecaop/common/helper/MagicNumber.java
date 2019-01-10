package com.ailk.ecaop.common.helper;

public class MagicNumber {

    public final static int CERT_TYPE_CODE_OTHER = 18;

    public final static String DEFAULT_NO_VALUE = "-1";

    // 4G转2G/3G的OPT_TYPE
    public final static String CHG_2G3G_FROM_4G_OPT = "1";

    // 2G/3G转4G的OPT_TYPE
    public final static String CHG_4G_FROM_2G3G_OPT = "2";

    // 订单受理
    public final static String OPER_TYPE_ORDER_COMMIT = "0";

    // 业务返销
    public final static String OPER_TYPE_ORDER_CANCEL = "1";

    // 撤单
    public final static String OPER_TYPE_ORDER_BACK = "2";

    // 改单
    public final static String OPER_TYPE_ORDER_CHANGE = "3";

    // 未返销
    public final static String TO_BE_CANCEL = "0";

    // 被返销
    public final static String BE_CANCELED = "1";

    // 返销
    public final static String BE_CANCELING = "2";

    // 数据来源系统为ESS
    public final static String SYS_TYPE_ESS = "1";

    // 数据来源系统为CBSS
    public final static String SYS_TYPE_CBSS = "2";

    // 移动号码对应的SERVICE_CLASS_CODE
    public final static String MOB_SERVICE_CLASS = "0000";

    // 固话号码对应的SERVICE_CLASS_CODE
    public final static String LAN_SERVICE_CLASS = "0100";

    // 宽带开户的业务TRADE_TYPE_CODE
    public final static String BROAD_BAND_OPEN = "0010";

    // 2G/3G转4G的TRADE_TYPE_CODE
    public final static String CHG_4G_FROM_2G3G = "0440";

    // 4G转2G/3G的TRADE_TYPE_CODE
    public final static String CHG_2G3G_FROM_4G = "0441";

    // 全业务规范的其他错误码
    public final static String FBS_ERROR_CODE = "8888";

    // 枢纽规范的其他错误码
    public final static String ONS_ERROR_CODE = "9999";

    // 客户信息
    public final static String CUSTOMER_INFO = "客户信息";

    // 用户信息
    public final static String USER_INFO = "用户信息";

    // 换卡业务bizkey
    public final static String CHANGE_CARD_BIZKEY = "SS-CS-007";

    public final static String N6ESS = "N6ESS";

    public final static String N25ESS = "N25ESS";

    public final static String THREEPART_NO_ERROR_BIZKEY = "CS-017";

    // CBSS默认失效时间
    public final static String CBSS_DEFAULT_EXPIRE_TIME = "20501231235959";

    public final static String STRING_OF_NULL = "NULL";

    // 调用CBSSSEQ_ID
    public final static String USER_ID = "SEQ_USER_ID";

    public final static String CUST_ID = "SEQ_CUST_ID";

    public final static String PAYRELA_ID = "SEQ_PAYRELA_ID";// 集团用户

    public final static String ACCT_ID = "SEQ_ACCT_ID";

    public final static String CYCLE_ID = "CYCLE_ID";

    public final static String[] COPYARRAY = { "operatorId", "province", "city", "district", "channelId",
            "channelType", "eModeCode" };
}
