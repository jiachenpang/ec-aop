package com.ailk.ecaop.common.utils;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

/**
 * 该工具类用于转换费用项
 * @author wangmc
 */
public class TransFeeUtils {

    /**
     * transLevel为小数点移动位数,增大为正,减小为负(如分转厘:transLevel=1)
     * @param fee
     * @param transLevel
     * @return
     */
    public static Object transFee(Object fee, int transLevel) {
        if (null == fee || "".equals(fee)) {
            throw new EcAopServerBizException("9999", "费用转化工具类报错:费用为空!");
        }
        return (int) (Math.pow(10, transLevel) * Integer.valueOf(fee.toString()));
    }

    public static void main(String[] args) {
        System.out.println(transFee("82800", -1));
    }
}
