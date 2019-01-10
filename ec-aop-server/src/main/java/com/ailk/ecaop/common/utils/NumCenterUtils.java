package com.ailk.ecaop.common.utils;
/**
 * Created by Liu JiaDi on 2016/8/24.
 */

import com.alibaba.fastjson.JSON;

import org.n3r.config.Config;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.conf.EcAopConfigLoader;
/**
 * 用于处理号卡中心相关参数的公用方法
 *
 * @auther Liu JiaDi
 * @create 2016_08_24_9:34
 */
public class NumCenterUtils {

    //static Esql aopDao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/aop/common.esql");

    public Map changeNumState(String rscStateCode) {
        Map tempMap = new HashMap();
        /*
         * 01 空闲 02 预留 03 上架 04 选占
         * 05 待预配 06 预配 07 预配套包 08 预占
         * 09 占用未激活 10 占用 11 冷冻 12 携入
         * 13 携出 14 待审批
         * 0000 资源可用 0001 资源已被占 0002 无此资源信息 0003 资源不可售
         * 0004 资源状态是非可用0005 资源归属渠道错误0006 资源空闲，不能释放
         * 0007 资源已售，不能释放9999 其它失败原因
         */
        if ("03".equals(rscStateCode) || "3".equals(rscStateCode)) {
            tempMap.put("rscStateCode", "0000");
            tempMap.put("rscStateDesc", "资源可用");
        }
        else if ("04".equals(rscStateCode) || "4".equals(rscStateCode) || "08".equals(rscStateCode)
                || "8".equals(rscStateCode)) {
            tempMap.put("rscStateCode", "0001");
            tempMap.put("rscStateDesc", "资源已被占");
        }
        else if ("02".equals(rscStateCode) || "2".equals(rscStateCode)) {
            tempMap.put("rscStateCode", "0003");
            tempMap.put("rscStateDesc", "资源不可售");
        }
        else if ("01,05,06,07,09,10,11,12,13,14".contains(rscStateCode)) {
            tempMap.put("rscStateCode", "0004");
            tempMap.put("rscStateDesc", "资源状态是非可用");
        }
        else {
            tempMap.put("rscStateCode", "9999");
            tempMap.put("rscStateDesc", "其他失败原因");
        }
        return tempMap;
    }

    /**
     * 共用参数封装
     */
    public Map changeCommonImplement(Map msg) {
        Map commonMap = new HashMap();
        commonMap.put("STAFF_ID", msg.get("operatorId"));
        commonMap.put("PROVINCE_CODE", msg.get("province"));
        commonMap.put("CITY_CODE", msg.get("city"));
        commonMap.put("DISTRICT_CODE", msg.get("district"));
        commonMap.put("CHANNEL_ID", msg.get("channelId"));
        commonMap.put("CHANNEL_TYPE", msg.get("channelType"));
        List<Map> para = (List<Map>) msg.get("para");
        List<Map> paraList = new ArrayList<Map>();   
        if (null != para && para.size() > 0) {   	
            for (Map paraMap : para) {         
            	Map map = new HashMap();
            	map.put("PARA_ID", (String)paraMap.get("paraId"));
            	map.put("PARA_VALUE", (String)paraMap.get("paraValue"));
            	paraList.add(map);
            }
            commonMap.put("PARA", paraList);
        }
        return commonMap;
    }
	
	
    /**
     * 调用资源中心的共用参数封装 by wangmc 20180518
     */
    public Map changeCommonImplementByResCenter(Map msg) {
        Map commonMap = new HashMap();
        commonMap.put("OPERATOR_ID", msg.get("operatorId"));
        commonMap.put("PROVINCE", msg.get("province"));
        commonMap.put("EPARCHY_CODE", msg.get("city"));
        commonMap.put("CITY_CODE", msg.get("district"));
        commonMap.put("CHANNEL_ID", msg.get("channelId"));
        commonMap.put("CHANNEL_TYPE", msg.get("channelType"));
        List<Map> para = (List<Map>) msg.get("para");
        if (null != para && para.size() > 0) {
            List<Map> paraList = new ArrayList<Map>();
            for (Map paraMap : para) {
                Map map = new HashMap();
                map.put("PARA_ID", paraMap.get("paraId"));
                map.put("PARA_VALUE", paraMap.get("paraValue"));
                paraList.add(map);
            }
            commonMap.put("PARA", paraList);
        }
        return commonMap;
    }

    /**
     * 该方法用于对系统编码进行转码
     * 转成对应号卡中心的对应编码
     * 对应文件在changeCode.props中
     * @param exchange
     * @return 号卡中 心sysCode
     */
    public String changeSysCode(Exchange exchange) {
        Object sysCode = "5600";
        String isChangeSysCode = EcAopConfigLoader.getStr("ecaop.global.param.flag.isChangeSysCode");
        if ("1".equals(isChangeSysCode)) {
            Map redisSysCodeMap = (Map) JSON.parse(EcAopConfigLoader
                    .getStr("ecaop.global.param.change.sysCode.toNumCenter"));
            sysCode = redisSysCodeMap.get(exchange.getAppkey());
            if (IsEmptyUtils.isEmpty(sysCode)) {
                sysCode = "5600";
            }
        }
        return sysCode.toString();
    }

    /**
     * 转换预占时间为具体分钟数
     */
    public String changeOccupyTime2Min(String occupiedTime) {
        long min = 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        String sysDate = format.format(new Date());
        try {
            min = (format.parse(occupiedTime).getTime() - format.parse(sysDate).getTime()) / 60000;
        } catch (ParseException e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预占时间格式错误，请检查日期occupiedTime:" + occupiedTime + "是否符合yyyyMMddHHmmss格式");
        }
        return String.valueOf(min) + "";
    }

    /**
     * 该方法用于记录
     *
     * @param 号码 serialNumber
     *           订单号 ordersId
     *           号码状态 numState
     */
    public static void InsertTradeInfo(Map msg) {
        new Esql("ecaop_connect_MySql").useSqlFile("/com/ailk/ecaop/sql/aop/common.esql").id("insertTradeInfo").params(msg).execute();
    }

    /**
     * 正式提交时根据订单号查询是否走号卡中心
     * @param 订单号:SUBSCRIBE_ID
     * @return
     */
    public static List qryNumSwitchByProvOrderId(Map inMap) {
        return new Esql("ecaop_connect_MySql").useSqlFile("/com/ailk/ecaop/sql/aop/common.esql").id("qryNumSwitchByProvOrderId").params(inMap).execute();
    }

    /**
     * 该方法用于处理返回报文头
     */
    public void dealReturnHead(Exchange exchange) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map date = (Map) outMap.get("data");
        if (!IsEmptyUtils.isEmpty(date)) {
            throw new EcAopServerBizException("9999", (String) date.get("message"));
        }
        Map headMap = (Map) outMap.get("UNI_BSS_HEAD");
        if (IsEmptyUtils.isEmpty(headMap)) {
            throw new EcAopServerBizException("9999", "能力平台返回报文格式不符合规范");
        } else {
            if (!"00000".equals(headMap.get("RESP_CODE"))) {
                throw new EcAopServerBizException("9999", "能力平台返回报错：" + headMap.get("RESP_DESC") + ",流水号是" + headMap.get("TRANS_ID"));
            }
        }
    }

    /**
     * 该方法用于判断报文头以及报文体是否有错 by wangmc 20170904
     * @param exchange
     * @param bodyRspKey - 返回报文对应的rspkey
     */
    public void dealReturnHead(Exchange exchange, String bodyRspKey) {
        // 判断报文头是否有错
        dealReturnHead(exchange);
        // 判断报文体是否有错
        if (!IsEmptyUtils.isEmpty(bodyRspKey)) {
            Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
            Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
            Map keyMap = (Map) bodyMap.get(bodyRspKey);
            if (IsEmptyUtils.isEmpty(keyMap)) {
                throw new EcAopServerBizException("9999", "号卡中心返回报文格式不符合规范");
            }
            if (!"0000".equals(keyMap.get("RESP_CODE"))) {
                throw new EcAopServerBizException("9999", "号卡中心返回报错：" + keyMap.get("RESP_DESC"));
            }
        }
    }
}
