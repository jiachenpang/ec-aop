package com.ailk.ecaop.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.n3r.config.Config;

import com.alibaba.fastjson.JSON;

public class AopTestUtilServlet {

    // public static void main(String[] args) throws KeeperException, InterruptedException, IOException {
    // ConfigUpdater config = new ConfigUpdater("132.35.81.197:2187");
    // config.getStore().write("/Config", "org.n3r.config.Config");
    // config.getStore().write("/EcAopConfigLoader", "org.n3r.ecaop.core.conf.EcAopConfigLoader");
    // System.out.println("刷新完成");
    // }

    public static void main() {
        List appCode = new ArrayList();
        String[] appkey = new String[] { "ahbc.sub", "ahbss.pre", "ahpre.sub", "apaas.sub", "apple.sub", "ass.sub",
                "autersys.sub", "b2b.sub", "bjaop.sub", "bjbss.pre", "bjpre.sub", "bopl.sub", "cbsp.sub", "cbss.sub",
                "cbtest.sub", "chan.sub", "cmall.sub", "cqbc.sub", "cqbss.pre", "cqpre.sub", "ecs.sub", "ecss.sub",
                "esal.sub", "fjbc.sub", "fjbss.pre", "fjpre.sub", "gdaop.sub", "gdbc.sub", "gdbss.pre", "gdinte.sub",
                "gdjk.sub", "gdmall.sub", "gdpre.sub", "gdreve.sub", "gsbc.sub", "gskl.sub", "gspre.sub", "gxbc.sub",
                "gxbss.pre", "gzbc.sub", "gzbss.pre", "gzch.sub", "gzds.sub", "gzop.sub", "gzpre.sub", "gzref.sub",
                "h2.sub", "habc.sub", "habss.pre", "hapre.sub", "hbbc.sub", "hbbss.pre", "hbpre.sub", "hebss.pre",
                "hibc.sub", "hibss.pre", "hipre.sub", "hlbss.pre", "hlline.sub", "hlperser.sub", "hlpre.sub",
                "hnbc.sub", "hnbss.pre", "hnpre.sub", "huord.sub", "hupre.sub", "intrp.sub", "jdmall.sub", "jkgrp.sub",
                "jkzy.sub", "jlbc.sub", "jlpre.sub", "jsbc.sub", "jsbss.pre", "jspre.sub", "jxbc.sub", "jxbss.sub",
                "jxpre.sub", "kfwx.sub", "lenovo.sub", "lnbss.pre", "lxltest.sub", "mall.sub", "mapphl.sub",
                "mappln.sub", "maras.sub", "mini.sub", "n6ess.sub", "nmbc.sub", "nmbss.pre", "nmpre.sub", "nxbc.sub",
                "nxbss.sub", "nxpre.sub", "partner.biz", "pay.sub", "qhbc.sub", "qhpre.sub", "scaop.sub", "scbc.sub",
                "scbss.pre", "scmo.sub", "scpre.sub", "scrs.sub", "sdapp.sub", "sdbss.pre", "sdpre.sub", "sdwu.sub",
                "shaip.sub", "shbc.sub", "shbss.sub", "shcp.sub", "shpre.sub", "snbc.sub", "snbss.pre", "snoto.sub",
                "snpre.sub", "suning.sub", "sxaop.sub", "sxbc.sub", "sxbss.pre", "sxpre.sub", "tjbc.sub",
                "tjintinn.sub", "tjpre.sub", "tsst.sub", "uss.sub", "wmctest.sub", "wo.sub", "wolm.sub", "wosabj.sub",
                "wsg.sub", "wxh.sub", "xjbc.sub", "xjbss.pre", "xjip.sub", "xjpre.sub", "xjwyd.sub", "xzbc.sub",
                "xzpre.sub", "ynbc.sub", "ynpre.sub", "zjbc.sub", "zjbss.pre", "zjmall.sub", "zjpre.sub", "ztest.sub",
                "zwkj.sub" };
        String appCodes = "{";
        for (int i = 0; i < appkey.length; i++) {
            appCode.add("\"" + Config.getStr("ecaop.core.app.map." + appkey[i]) + "\"");
            appCodes += "\"" + appkey[i] + "\":\"" + Config.getStr("ecaop.core.app.map." + appkey[i]) + "\",";
        }
        System.out.println(appCodes.substring(0, appCodes.length() - 1) + "}");
    }

    public static void main(String[] args) {
        String msgStr = "{\"operatorId\":\"LTHXY130\",\"province\":\"13\",\"city\":\"130\",\"district\":\"132003\",\"channelId\":\"13a0192\",\"channelType\":\"1010300\",\"tradeTypeCode\":\"9999\",\"serviceClassCode\":\"0000\",\"areaCode\":\"132542\",\"bssOrderId\":\"1318121161909356\",\"ordersId\":\"\",\"subOrderInfo\":[{\"provOrderId\":\"1318121161909358\"},{\"provOrderId\":\"1318121161909359\"}],\"origTotalFee\":\"0\",\"payInfo\":{\"payType\":\"1\",\"payOrg\":\"\",\"payNum\":\"\"},\"para\":[{\"paraId\":\"1\",\"paraValue\":\"1\"}]}";
        Map msg = JSON.parseObject(msgStr);
        Map newMsg = camelToUnderline(msg);
        System.out.println(JSON.toJSONString(newMsg));
    }

    private static Map camelToUnderline(Map m) {
        Set keySet = m.keySet();
        Map newMsg = new HashMap();
        for (Object key : keySet) {
            String putKey = key.toString();
            if ("operatorId".equals(key) || "channelId".equals(key) || "channelType".equals(key)) {
                putKey = putKey.toUpperCase();
            }
            else {
                putKey = "";
                for (int i = 0; i < ((String) key).length(); i++) {
                    char c = ((String) key).charAt(i);
                    if (Character.isUpperCase(c)) {
                        putKey += "_" + c;
                    }
                    else {
                        putKey += Character.toUpperCase(c);
                    }
                }
            }
            Object val = m.get(key);
            if (val instanceof List) {
                List<Map> temp = new ArrayList<Map>();
                for (Map valMap : (List<Map>) val) {
                    temp.add(camelToUnderline(valMap));
                }
                val = temp;
            }
            else if (val instanceof Map) {
                val = camelToUnderline((Map) val);
            }
            newMsg.put(putKey, val);
        }
        return newMsg;
    }

}
