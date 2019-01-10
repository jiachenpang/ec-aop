package com.ailk.ecaop.common.utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.alibaba.fastjson.JSON;

public class GetSeqUtil {

    public static List getSeqFromCb(Exchange ex, String id, int count) {
        Map map = (Map) ex.getIn().getBody();
        Map msg = JSON.parseObject(map.get("msg").toString());
        msg.put("getSeqId", id);
        msg.put("getSeqCount", count);
        map.put("msg", msg);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.chph.gifa.ParametersMapping", ex);
        try {
            CallEngine.wsCall(ex, "ecaop.comm.conf.url.cbss.services.GetSeqIDForAOPSer");
            lan.xml2Json("ecaop.masb.chph.gifa.template", ex);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取cb侧序列失败！");
        }
        ArrayList<Map> ids = (ArrayList<Map>) ((Map) ex.getOut().getBody()).get("info");
        String s = "";
        for (Map m : ids) {
            s += m.get("id") + "/";
        }
        s = s.substring(0, s.length() - 1);
        return Arrays.asList(s.split("/"));
    }

    public static List getSeqFromCb(ParametersMappingProcessor pmp, Exchange ex, String id, int count) {
        Map map = (Map) ex.getIn().getBody();
        Map msg = JSON.parseObject(map.get("msg").toString());
        msg.put("getSeqId", id);
        msg.put("getSeqCount", count);
        map.put("msg", msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp, ex);
        try {
            CallEngine.wsCall(ex, "ecaop.comm.conf.url.cbss.services.GetSeqIDForAOPSer");
            lan.xml2Json("ecaop.masb.chph.gifa.template", ex);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取cb侧序列失败！");
        }
        ArrayList<Map> ids = (ArrayList<Map>) ((Map) ex.getOut().getBody()).get("info");
        String s = "";
        for (Map m : ids) {
            s += m.get("id") + "/";
        }
        s = s.substring(0, s.length() - 1);
        return Arrays.asList(s.split("/"));
    }

    /**
     * 从北六e获取串号（优化）
     * 参数 seqType值：TRADE_ID、USER_ID、 ACCT_ID、CUST_ID、ITEM_ID
     * eparchyCode 四位地市编码
     */
    public static String getSeqFromN6ess(ParametersMappingProcessor pmp, Exchange ex, String seqType, String eparchyCode) {
        Map map = (Map) ex.getIn().getBody();
        Map msg = JSON.parseObject(map.get("msg").toString());
        msg.put("seqType", seqType);
        msg.put("eparchyCode", eparchyCode);

        map.put("msg", msg);

        LanUtils lan = new LanUtils();

        lan.preData(pmp, ex);// ecaop.trades.seqid.ParametersMapping
        try {
            CallEngine.wsCall(ex, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer." + msg.get("province"));
            lan.xml2Json("ecaop.trades.seqid.template", ex);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取北六E侧序列失败！");
        }

        String id = (String) ((Map) ex.getOut().getBody()).get("id");
        return id;
    }

    /**
     * 从北六e获取串号
     * 参数 seqType值：TRADE_ID、USER_ID、 ACCT_ID、CUST_ID、ITEM_ID
     *eparchyCode 四位地市编码
     */
    public static String getSeqFromN6ess(Exchange ex, String seqType, String eparchyCode) {
        Map map = (Map) ex.getIn().getBody();
        Map msg = JSON.parseObject(map.get("msg").toString());
        msg.put("seqType", seqType);
        msg.put("eparchyCode", eparchyCode);

        map.put("msg", msg);

        LanUtils lan = new LanUtils();

        lan.preData("ecaop.trades.seqid.ParametersMapping", ex);
        try {
            CallEngine.wsCall(ex, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer."+msg.get("province"));
            lan.xml2Json("ecaop.trades.seqid.template", ex);
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取北六E侧序列失败！");
        }

        String id= (String) ((Map) ex.getOut().getBody()).get("id");
        return id;
    }


    public static String getSeqFromCb(Exchange ex, String id) {
        return getSeqFromCb(ex, id, 1).get(0).toString();
    }

    public static String getSeqFromCb() {
        SimpleDateFormat format = new SimpleDateFormat("yyMMddHHmmssSSS");
        return "8" + format.format(new Date());
    }

    public static Queue<String> getSeqFromCbRetQ(Exchange ex, String id, int count) {
        Map map = (Map) ex.getIn().getBody();
        Map msg = JSON.parseObject(map.get("msg").toString());
        msg.putAll(MapUtils.asMap("getSeqId", id, "getSeqCount", count));

        map.put("msg", msg);

        try {
            new LanUtils().preData("ecaop.masb.chph.gifa.ParametersMapping", ex);
            CallEngine.wsCall(ex, "ecaop.comm.conf.url.cbss.services.GetSeqIDForAOPSer");
            new LanUtils().xml2Json("ecaop.masb.chph.gifa.template", ex);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "获取cb侧序列失败！");
        }

        List<Map> ids = (ArrayList<Map>) ((Map) ex.getOut().getBody()).get("info");

        Queue<String> idsQue = new LinkedList<String>();
        for (Map m : ids) {
            idsQue.offer(m.get("id").toString());
        }
        return idsQue;
    }
}
