package com.ailk.ecaop.common.utils;

import java.io.InputStream;

public class TransRspMsg {

    static char packageEnd = (char) 0x1a;
    static char spFiled = (char) 0x09;

    public static String getRspMsg(InputStream in) throws Exception {
        byte[] head = new byte[7];
        int readed = 0;
        readed += in.read(head, readed, 7 - readed);

        byte[] lengthArr = new byte[5];
        for (int j = 0; j < 5; j++) {
            lengthArr[j] = head[j + 2];
        }
        String lenStr = new String(lengthArr);
        int len = Integer.valueOf(lenStr.trim()).intValue();
        byte[] content = new byte[len];
        for (int j = 0; j < 7; j++) {
            content[j] = head[j];
        }
        in.read(content, readed, len - readed);
        return new String(content, "UTF-8");
    }
}
