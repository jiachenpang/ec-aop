package com.ailk.ecaop.common.faceheader;

import org.n3r.config.Config;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class NumFaceHeadHelper {
    /**
     * 号卡中心带参数版本
     */
    public static Map creatHead(String appkey) throws Exception {
        String appId = Config.getStr("ecaop.global.param.num.aop.appid");
        String secret = Config.getStr("ecaop.global.param.num.aop.secret");
        return creatHead(appId, secret);
    }

    /**
     * 号卡中心不带参数版本
     */
    public static Map creatHead() throws Exception {
        String appId = Config.getStr("ecaop.global.param.num.aop.appid");
        String secret = Config.getStr("ecaop.global.param.num.aop.secret");
        return creatHead(appId, secret);
    }

    /**
     * 生成调用自然人中心的报文头,
     * 其appId和secret与号卡中心防止不一样
     *
     * @return
     * @throws Exception
     */
    public static Map creatRealPersonHead() throws Exception {
        String appId = Config.getStr("ecaop.global.param.realPerson.aop.appid");
        String secret = Config.getStr("ecaop.global.param.realPerson.aop.secret");
        return creatHead(appId, secret);
    }

    public static Map creatHead(String appId, String secret) throws Exception {

        System.out.println("调用自然人的appid是：" + appId);
        // 时间戳 当前的系统时间戳，单位为毫秒
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS");
        Date date = new Date();
        String TIMESTAMP = sdf.format(date);
        // String TIMESTAMP = "2016-09-07 14:40:46 209";

        // 序列号 YYYYMMDDHHMMSS+毫秒(3) +6位随机数
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        Date date1 = new Date();
        String dateStr = sdf1.format(date1);

        Random random = new Random();
        int randNum = random.nextInt(899999) + 100000;
        String TRANS_ID = dateStr + randNum;
        // String TRANS_ID ="20160907144046209429712";

        // 访问令牌
        String preToken = "APP_ID" + appId + "TIMESTAMP" + TIMESTAMP + "TRANS_ID" + TRANS_ID + secret;
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(preToken.getBytes());
        byte[] digest = md.digest();
        String TOKEN = byte2hex(digest);
        // String TOKEN = RBase64.encode(digest);

        // reserved 节点没有特殊要求填空
        List reservedList = new ArrayList();

        Map HeadMap = new HashMap();
        HeadMap.put("RESERVED", reservedList); // reserved 节点没有特殊要求填空
        HeadMap.put("APP_ID", appId); // 接入标识码
        HeadMap.put("TIMESTAMP", TIMESTAMP); // 时间戳
        HeadMap.put("TRANS_ID", TRANS_ID); // 序列号
        HeadMap.put("TOKEN", TOKEN); // 访问令牌

        // 返回结果
        Map resultMap = new HashMap();
        resultMap.put("UNI_BSS_HEAD", HeadMap);

        return resultMap;

    }

    private static String byte2hex(byte[] b) {
        String hs = "";
        String stmp = "";
        for (byte element : b) {
            stmp = Integer.toHexString(element & 0XFF);
            if (stmp.length() == 1) {
                hs = hs + "0" + stmp;
            }
            else {
                hs = hs + stmp;
            }
        }
        return hs;
    }
}
