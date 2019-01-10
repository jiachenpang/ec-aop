package com.ailk.ecaop.common.helper.ness;

/**
 * 北六预提交接口TRADE_DISCNT节点
 * 1、存放新订购的资费信息
 * 2、存放需要退订的资费信息
 * 
 * @author Steven
 */
public class TradeDiscnt {

    // 资费编码
    public static final String DISCNT_CODE = "DISCNT_CODE";

    // 0:订购 1:退订
    public static final String MODIFY_TAG = "MODIFY_TAG";

    // 产品编码
    public static final String PRODUCT_ID = "PRODUCT_ID";
}
