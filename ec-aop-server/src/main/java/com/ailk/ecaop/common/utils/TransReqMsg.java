package com.ailk.ecaop.common.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * ClassName: TransferReqMsg
 * 
 * @Description: 转换成H2协议请求头
 * @author cuiminyi
 * @date 2016-7-29
 */
public class TransReqMsg {

    static char packageEnd = (char) 0x1a;
    static char spFiled = (char) 0x09;

    public static String getReqMsg(Map mapMsg) throws Exception {
        StringBuffer sb = new StringBuffer();
        String SeriralNum = getSeqFromCb();

        String number = String.format("%-20s", mapMsg.get("number").toString());
        String channelId = String.format("%-10s", mapMsg.get("channelId").toString());
        String operatorId = String.format("%-20s", mapMsg.get("operatorId").toString());
        sb.append(SeriralNum).append("1").append(mapMsg.get("servceName")).append(number)
                .append(mapMsg.get("busiType"))
                .append(channelId).append(operatorId).append("00001").append("1").append("00000");

        StringBuffer req = new StringBuffer();
        String msgtemp = req.toString();
        byte[] temp = msgtemp.getBytes("UTF-8");
        int lengthReal = temp.length + 102;
        String lengthStr = fillTotalLength(lengthReal);
        String msg = "11" + lengthStr + sb.toString() + msgtemp;
        return msg;
    }

    static String fillTotalLength(int length) {
        StringBuffer sb = null;
        String lengthStr = Integer.toString(length);
        if (lengthStr.length() > 5) {
            return null;
        }
        sb = new StringBuffer(lengthStr);
        for (int i = 0; i < 5 - lengthStr.length(); i++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * @Description: 获取流水号
     * @param @return
     * @return String
     * @throws
     * @author cuiminyi
     * @date 2016-7-29
     */
    public static String getSeqFromCb() {
        SimpleDateFormat format = new SimpleDateFormat("yyMMddHHmmssSSSSSSSS");
        return format.format(new Date());
    }
}
