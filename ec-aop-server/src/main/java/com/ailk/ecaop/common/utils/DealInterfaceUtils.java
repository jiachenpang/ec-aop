package com.ailk.ecaop.common.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

public class DealInterfaceUtils {

    private static String[] methodCodeArray = { "qcus", "qctc", "qobq", "qbcq", "mcba", "mcbd", "cbpn", "sccc" };
    private static Map authGrpCodeMap = MapUtils.asMap("1000", "0201", "1110", "0202", "8888", "9999");

    /**
     * 调用省份接口，超时编码的处理
     * 0110、0102转成0001
     * 
     * @param respCode
     * @return
     */
    public String dealTimeOutCode(String respCode) {
        if ("0102".equals(respCode) || "0110".equals(respCode)) {
            return "0001";
        }
        else if ("".equals(respCode)) {
            return "9999";
        }
        return respCode;
    }

    /**
     * 拼装返回描述
     * 
     * @param inMap
     * @return
     */
    public String dealRespDesc(Map inMap) {
        return "下游系统'" + inMap.get("actCode") + "'接口返回:" + inMap.get("detail") + ";TransIDO为:" + inMap.get("transIDO");
    }

    public String dealRespDesc4FBS(Map inMap, Object errmsg) {
        return inMap.get("operatorName") + "接口返回:" + errmsg + ";TRANSIDO为:" + inMap.get("transIDO") + ".";
    }

    /**
     * 对于接口规范中没有应答描述的接口,进行特殊处理
     * 
     * @param actCode
     * @param code
     * @return
     */
    public String dealDetail(String actCode, String code) {
        Map defaultDetail = new HashMap();
        if ("T3M00013".equals(actCode) || "T3M00008".equals(actCode)) {
            defaultDetail.put("0001", "该员工不存在");
            defaultDetail.put("0002", "该员工已删除");
        }
        if ("T8010005".equals(actCode)) {
            defaultDetail.put("0001", "交易记录不存在");
        }
        Object detail = null == defaultDetail.get(code) ? "其他错误" : defaultDetail.get(code);
        return detail.toString();
    }

    public String dealCode(String actCode, String code) {
        Map defaultDetail = new HashMap();
        if ("T8010005".equals(actCode)) {
            defaultDetail.put("0001", "1801");
        }
        Object detail = null == defaultDetail.get(code) ? code : defaultDetail.get(code);
        return detail.toString();
    }

    /**
     * 将编码8888转成9999
     * 
     * @param respCode
     * @param operatorName
     * @return
     */
    public String dealOthercode(String respCode, String operatorName) {
        if ("cmck|cuck".contains(operatorName)) {
            Object retCode = authGrpCodeMap.get(respCode);
            return null == retCode ? respCode : retCode.toString();
        }
        if (!"8888".equals(respCode)) {
            return respCode;
        }
        for (int i = 0; i < methodCodeArray.length; i++) {
            if (operatorName.equals(methodCodeArray[i])) {
                return "9999";
            }
        }
        return respCode;
    }

    public String dealCardData(String cardData) {
        try {
            return URLEncoder.encode(cardData, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new EcAopServerBizException("9999", "返回的报文格式有错，请检查:" + cardData);
        }
    }

    public void removeCodeDetail(Map bodyMap) {
        bodyMap.remove("code");
        bodyMap.remove("detail");
        bodyMap.remove("actCode");
        bodyMap.remove("transIDO");
    }
}
