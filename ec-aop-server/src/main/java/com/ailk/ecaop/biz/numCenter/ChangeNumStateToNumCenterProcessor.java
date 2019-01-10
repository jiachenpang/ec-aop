package com.ailk.ecaop.biz.numCenter;

/**
 * Created by Liu JiaDi on 2016/8/22.
 */


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * 号码状态变更简单版-号卡中心分枝逻辑
 *
 * @auther Liu JiaDi
 * @create 2016_08_22_11:11
 */
@EcRocTag("changeNumStateToNumCenter")
public class ChangeNumStateToNumCenterProcessor extends BaseAopProcessor {
    LanUtils lan = new LanUtils();
    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        //获取sysCode
        new DealSysCodeExtractor().extract(exchange);

        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        String occupiedFlag = (String) resourcesInfo.get(0).get("occupiedFlag");
        String resourcesCode = (String) resourcesInfo.get(0).get("resourcesCode");
        msg.put("serialNumber", resourcesCode);

        if ("0".equals(occupiedFlag)) {
            //不预占
            dealReqNoOcuppy(exchange);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.qryNumInfo");
            nc.dealReturnHead(exchange);
            dealReturnNoOcuppy(exchange);
        } else if ("1".equals(occupiedFlag)) {
            msg.put("ResourcesCode", resourcesCode);
            //选占
           /* 号码统一管理去掉一证两户校验 by liujd
           // 1、省份简单用户查询接口
            new SimpleCheckUserInfoProcessor().process(ExchangeUtils.ofCopy(exchange, msg));
            // 2、cbss简单用户查询接口 还没写
            new CbssSimpleCheckUserInfoProCessor().process(ExchangeUtils.ofCopy(exchange, msg));*/
            // 3、 号卡中心选占接口(还少sysCode转换)
            String occupiedTime = (String) resourcesInfo.get(0).get("occupiedTime");
            if (StringUtils.isEmpty(occupiedTime)) {
                throw new EcAopServerBizException("9999", "选占时选占时间不能为空");
            }
            msg.put("occupiedTime", nc.changeOccupyTime2Min(occupiedTime));
            dealReqOcuppy(exchange);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.selectedNum");
            nc.dealReturnHead(exchange);
            dealReturnOcuppy(exchange, msg);
        } else if ("2".equals(occupiedFlag)) {
            //预占
            //号卡中心预占接口(还少sysCode转换)
            dealReqPreemted(exchange);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.preemptedNum");
            nc.dealReturnHead(exchange);
            dealReturnPreemptedNum(exchange, msg);
        }
    }

    private void dealReturnPreemptedNum(Exchange exchange, Map msg) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("PREEMPTED_NUM_RSP");
        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "号码状态查询接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {
            List<Map> resourcesRsp = new ArrayList<Map>();
            Map resourceInfo = new HashMap();
            resourceInfo.put("resourcesType", "02");
            resourceInfo.put("resourcesCode", resultMap.get("SERIAL_NUMBER"));
            resourceInfo.put("rscStateCode", "0000");
            resourceInfo.put("rscStateDesc", "资源可用");
            resourcesRsp.add(resourceInfo);
            Map reMap = new HashMap();
            reMap.put("resourcesRsp", resourcesRsp);
            exchange.getOut().setBody(reMap);
            try {
                msg.put("numState", "2");
                nc.InsertTradeInfo(msg);
            } catch (Exception e) {
                exchange.getOut().setBody(reMap);
            }
        } else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }
    }

    private void dealReturnOcuppy(Exchange exchange, Map msg) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("SELECTED_NUM_RSP");
        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "号码状态查询接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {

            List<Map> resourcesRsp = new ArrayList<Map>();
            Map resourceInfo = new HashMap();
            resourceInfo.put("resourcesType", "02");
            resourceInfo.put("resourcesCode", resultMap.get("SERIAL_NUMBER"));
            resourceInfo.put("rscStateCode", "0000");
            resourceInfo.put("rscStateDesc", "资源可用");
            String ReservaPrice = (String) resultMap.get("ADVANCE_PAY");
            if (StringUtils.isNotEmpty(ReservaPrice)) {
                //转换单位成厘
                resourceInfo.put("ReservaPrice", Integer.parseInt(ReservaPrice) * 10);
            }
            List<Map> numInfo = new ArrayList<Map>();
            Map send = new HashMap();
            send.put("classId", resultMap.get("GOOD_LEVEL"));
            String lowCost = (String) resultMap.get("LOW_COST");
            if (StringUtils.isNotEmpty(lowCost)) {
                send.put("lowCostPro", Integer.parseInt(lowCost) * 10);
            }
            String timeDur = (String) resultMap.get("ONLINE_LENGTH");
            if (StringUtils.isNotEmpty(timeDur)) {
                send.put("timeDurPro", timeDur);
            }
            numInfo.add(send);
            resourceInfo.put("numInfo", numInfo);

            resourcesRsp.add(resourceInfo);
            Map reMap = new HashMap();
            reMap.put("resourcesRsp", resourcesRsp);
            exchange.getOut().setBody(reMap);
            try {
                msg.put("numState", "2");
                nc.InsertTradeInfo(msg);
            } catch (Exception e) {
                exchange.getOut().setBody(reMap);
            }
        } else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }
    }

    private void dealReturnNoOcuppy(Exchange exchange) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("QRY_NUM_INFO_RSP");
        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "号码状态查询接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {
            List<Map> resourcesRsp = (List<Map>) resultMap.get("RESOURCES_INFO");
            if (null != resourcesRsp && resourcesRsp.size() > 0) {
                for (Map resourceInfo : resourcesRsp) {
                    String rscStateCode = (String) resourceInfo.get("NUM_STATUS");
                    resourceInfo.put("resourcesType", "02");
                    resourceInfo.put("resourcesCode", resourceInfo.get("SERIAL_NUMBER"));
                    resourceInfo.putAll(nc.changeNumState(rscStateCode));
                    String ReservaPrice = (String) resourceInfo.get("ADVANCE_PAY");
                    if (StringUtils.isNotEmpty(ReservaPrice)) {
                        //转换单位成厘
                        resourceInfo.put("ReservaPrice", Integer.parseInt(ReservaPrice) * 10);
                    }
                    List<Map> numInfo = new ArrayList<Map>();
                    Map send = new HashMap();
                    send.put("classId", resourceInfo.get("GOOD_LEVEL"));
                    String lowCost = (String) resourceInfo.get("LOW_COST");
                    if (StringUtils.isNotEmpty(lowCost)) {
                        send.put("lowCostPro", Integer.parseInt(lowCost) * 10);
                    }
                    String timeDur = (String) resourceInfo.get("ONLINE_LENGTH");
                    if (StringUtils.isNotEmpty(timeDur)) {
                        send.put("timeDurPro", timeDur);
                    }
                    numInfo.add(send);
                    resourceInfo.put("numInfo", numInfo);
                }
            } else {
                throw new EcAopServerBizException("9999", "号卡中心未返回号码状态信息");
            }
            Map reMap = new HashMap();
            reMap.put("resourcesRsp", resourcesRsp);
            exchange.getOut().setBody(reMap);

        } else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }

    }

    private void dealReqNoOcuppy(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead(exchange.getAppkey()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<Map> SELNUM_LIST = new ArrayList();
        SELNUM_LIST.add(MapUtils.asMap("SERIAL_NUMBER", msg.get("serialNumber")));
        requestTempBody.put("SELNUM_LIST", SELNUM_LIST);

        requestTempBody.put("SEARCH_TYPE", "2");
        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        Map resultMap = new HashMap();
        resultMap.put("QRY_NUM_INFO_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }

    private void dealReqOcuppy(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead(exchange.getAppkey()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        requestTempBody.put("SERIAL_NUMBER", msg.get("serialNumber"));
        requestTempBody.put("SELECTION_TIME", msg.get("occupiedTime"));
        Map resultMap = new HashMap();
        resultMap.put("SELECTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }

    private void dealReqPreemted(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead(exchange.getAppkey()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        requestTempBody.put("SERIAL_NUMBER", msg.get("serialNumber"));
        Map resultMap = new HashMap();
        resultMap.put("PREEMPTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }
}
