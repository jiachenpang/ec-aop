package com.ailk.ecaop.common.utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.n3r.config.Config;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.alibaba.fastjson.JSON;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CallGZTSystemUtils {

    /**
     * 生成调国政通三个公共参数accessId，accessKey，timeStamp
     */
    public static Map createPublicParamForGZT() {
        /**
         * {"accessId":"test","accessKey":"6be35e2abd8ece9ad7b65a7268ffaeec",
         * "timeStamp":"2016-04-20 13:51:51.270","faceImg":"BASE64_DATA",
         * "faceImgType":"1" ,"baseImg":"BASE64_DATA","baseImgType":"5",
         * "trueNegativ eRate":"99.9"}
         * accessId Y 服务接入编号，由国政通分配
         * accessKey Y 服服务识别 KEY MD5(accessId+accessPwd+timeStamp)。 其中 accessPwd，由国政通分配
         * timeStamp Y 发送时间 格式：yyyy-MM-dd HH:mm:ss.SSS
         * faceImg Y 查询照 ※Base64.NO_WRAP
         * faceImgType Y 查询照的类型 1、身份证照片（正面） 2、身份证照片正面和 Usim 卡合照 3、手持身份证的照片（取生活照片） 4、手持身份证的照片（取身份证照片） 5、公安部照片
         * baseImg Y 登记照 ※Base64.NO_WRAP
         * baseImgType Y 登记照的类型 1、身份证照片（正面） 2、身份证照片正面和 Usim 卡合照 3、手持身份证的照片（取生活照片） 4、手持身份证的照片（取身份证照片） 5、公安部照片
         * trueNegativeRate N 误报率，只能填字符串 99.9 表示千分之一的误报率 99.99 表示 万分之一的误报率 99.999 表示 十万分之一的误报率 默认值为：99.9
         */
        // 服务接入编号，由国政通分配
        String accessId = Config.getStr("ecaop.global.param.gzt.aop.accessId", "cucc");
        // 由国政通分配
        String accessPwd = Config.getStr("ecaop.global.param.gzt.aop.accessPwd", "2496380B72");

        // 时间戳 当前的系统时间戳，单位为毫秒
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date();
        String timeStamp = sdf.format(date);
        // accessKey
        String temp = accessId + accessPwd + timeStamp;
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new EcAopServerBizException("9999", "生成accessKey失败：" + e.getMessage());
        }
        md.update(temp.getBytes());
        byte[] digest = md.digest();
        String accessKey = byte2hex(digest);

        Map sentMap = new HashMap();
        sentMap.put("accessId", accessId);
        sentMap.put("accessKey", accessKey);
        sentMap.put("timeStamp", timeStamp);

        return sentMap;
    }

    private static String byte2hex(byte[] b) {
        String hs = "";
        String stmp = "";
        for (byte element : b) {
            stmp = Integer.toHexString(element & 0XFF);
            if (stmp.length() == 1) {
                hs = hs + "0" + stmp;
            } else {
                hs = hs + stmp;
            }
        }
        return hs;
    }

    public String faceCheckResult(Exchange exchange, String methodName) {
        String errorMsg = "调国政通" + methodName + "接口";
        if (null == exchange.getOut()) {
            throw new EcAopServerBizException("9999", errorMsg + "失败,返回内容为空！");
        }
        Map out = exchange.getOut().getBody(Map.class);
        if (null == out || out.size() < 1) {
            out = (Map) JSON.parse(exchange.getOut().getBody(String.class));
        }
        if (null == out || out.size() < 1) {
            throw new EcAopServerBizException("9999", errorMsg + "失败,返回内容为空！");
        }
        String result = (String) out.get("result");

        return result;
    }

    public Map dealGZTReturn(Exchange exchange, String methodName) {
        String errorMsg = "调国政通" + methodName + "接口";
        if (null == exchange.getOut()) {
            throw new EcAopServerBizException("9999", errorMsg + "失败,返回内容为空！");
        }
        Map out = exchange.getOut().getBody(Map.class);
        if (null == out || out.size() < 1) {
            out = (Map) JSON.parse(exchange.getOut().getBody(String.class));
        }
        if (null == out || out.size() < 1) {
            throw new EcAopServerBizException("9999", errorMsg + "失败,返回内容为空！");
        }
        System.out.println(errorMsg + "返回toString：" + out.toString());
        /**
         * result string 是 返回结果值
         * message string 是 返回结果述 ※返回值中含有中文，使用 UTF-8 解码。
         * transaction_id string 否 对比服务交易编号
         * verify_result string 否 对比结果 0、表示认为是同一个人 1、表示认为不是同一个人
         * verify_similarity string 否 对比相似值 分数取值范围 0 – 100, 值越大越相似
         * 0 比对服务处理成功
         * 1 比对服务处理失败: {失败原因}
         * 9 比对服务器异常
         * 9990 请求参数错误，缺少必要的参数
         * 9991 请求的服务接入编号不存在
         * 9992 请求的服务识别 KEY 错误
         * 9999 请求处理异常，请联系客服！
         */
        String result = (String) out.get("result");
        String message = "";
        try {
            message = java.net.URLDecoder.decode((String) out.get("message"), "utf-8");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            message = (String) out.get("message");
        }

        if ("0".equals(result)) {
            return out;
        } else if ("1|9".contains(result)) {
            throw new EcAopServerBizException("1".equals(result) ? "0004" : "0005", errorMsg + "返回[" + message + "]");
        } else if ("9990|9991|9992|9999".contains(result)) {
            throw new EcAopServerBizException(result, errorMsg + "返回[" + message + "]");
        } else {
            throw new EcAopServerBizException("9999", errorMsg + "返回 [" + message + "]");
        }
    }
}
