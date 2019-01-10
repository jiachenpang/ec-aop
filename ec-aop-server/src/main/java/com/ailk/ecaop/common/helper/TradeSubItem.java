package com.ailk.ecaop.common.helper;

/**
 * 此工具类存放cbss统一预提交接口中TRADE_SUB_ITEM节点的相关属性
 * 注意按字母排序
 * 
 * @author Steven
 */
public class TradeSubItem {

    // 接入方式
    public static final String ACCESS_TYPE = "ACCESS_TYPE";

    // 账户密码
    public static final String ACCT_PASSWD = "ACCT_PASSWD";

    // 联系人
    public static final String LINK_NAME = "LINK_NAME";

    // 联系电话
    public static final String LINK_PHONE = "LINK_PHONE";

    // 用于存放活动类型,如YCMP001
    public static final String SALE_PRODUCT_LIST = "SALE_PRODUCT_LIST";

    // 共线号码
    public static final String SHARE_NBR = "SHARE_NBR";

    // 速率
    public static final String SPEED = "SPEED";

    // 终端品牌
    public static final String TERMINAL_BRAND = "TERMINAL_BRAND";

    // 终端型号
    public static final String TERMINAL_MODEL = "TERMINAL_MODEL";

    // 终端类型
    public static final String TERMINAL_TYPE = "TERMINAL_TYPE";

    // 订单 itemid必须是合约资费对应的itemid attrCode=3
    public static final String tradeId = "tradeId";
}
