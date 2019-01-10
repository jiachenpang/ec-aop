package com.ailk.ecaop.common.helper.ness;

/**
 * 存放预提交接口中的TRADE_SUB_ITEM信息
 * "="右边的值为下发到北六侧对应的key值
 * 
 * @author Steven
 */
public class TradeSubItem {

    // 周期(月)
    public static final String CYCLE = "cycle";

    // 费用(元/周期)
    public static final String CYCLE_FEE = "cycleFee";

    // 选择周期数
    public static final String CYCLE_NUM = "cycleNum";

    // 包期执行方式 0 立即生效,1 次月生效
    public static final String EFFECT_MODE = "effectMode";

    // 到期处理方式 a 续包年,b 到期执行指定资费,t 到期停机
    public static final String EXPIRE_DEAL_MODE = "expireDealMode";
}
