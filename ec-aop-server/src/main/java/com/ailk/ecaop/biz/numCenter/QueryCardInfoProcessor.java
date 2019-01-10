package com.ailk.ecaop.biz.numCenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * TODO cy: 号卡资源中心，卡信息查询，必须在2017-02-10上午之前完成<br/>
 * 
 * @author: crane[yuanxingnepu@gmail.com]
 * @date: 2017-2-9 上午10:22:14
 */
@EcRocTag("QueryCardInfo")
public class QueryCardInfoProcessor extends BaseAopProcessor {

    LanUtils lan = new LanUtils();
    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        System.out.println("Test msg:" + msg);

        // 获取sysCode
        new DealSysCodeExtractor().extract(exchange);

        dealReq(exchange);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.qryCardInfo");
        nc.dealReturnHead(exchange);
        dealRsp(exchange);
    }

    private void dealReq(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead(exchange.getAppkey()));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        requestTempBody.put("CARD_STATUS", msg.get("cardStatus"));
        requestTempBody.put("CARD_TYPE", msg.get("cardType"));
        requestTempBody.put("ICCID_START", msg.get("cardUseType"));
        requestTempBody.put("ICCID_END", msg.get("reDoTag"));       

        Map resultMap = new HashMap();
        resultMap.put("QRY_CARD_INFO_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);

        System.out.println("Test body:" + requestBody);

        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }

    private void dealRsp(Exchange exchange) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("QRY_CARD_INFO_RSP");

        System.out.println("Test resultMap:" + resultMap);

        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "号码状态查询接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {
            List<Map> resultInfoList = (List<Map>) resultMap.get("INFO");
            List<Map> tempInfoList = new ArrayList<Map>();
            if (resultInfoList != null && resultInfoList.size() > 0) {
                for (Map map : resultInfoList) {
                    Map tempMap = new HashMap();
                    
                    tempMap.put("iccid", map.get("ICCID"));
                    tempMap.put("imsi", map.get("IMSI"));
                    tempMap.put("ki", map.get("KI"));
                    tempMap.put("materialCode", map.get("MATERIAL_CODE"));
                    tempMap.put("cardBigType", map.get("CARD_BIG_TYPE"));
                    tempMap.put("cardType", map.get("CARD_TYPE"));
                    tempMap.put("cardName", map.get("CARD_NAME"));

                    tempMap.put("cardStatus", map.get("CARD_STATUS"));
                    tempMap.put("serialNumber", map.get("SERIAL_NUMBER"));
                    tempMap.put("packageId", map.get("PACKAGE_ID"));
                    tempMap.put("packageDes", map.get("PACKAGE_DES"));
                    tempMap.put("preMoney", map.get("PRE_MONEY"));
                    tempMap.put("endTime", map.get("END_TIME"));

                    tempInfoList.add(tempMap);
                }
            }

            List<Map> paraList = (List<Map>) resultMap.get("PARA");
            List<Map> tempParaList = new ArrayList<Map>();
            if (paraList != null && paraList.size() > 0) {
                for (Map map : paraList) {
                    Map tempMap = new HashMap();
                    tempMap.put("paraId", map.get("PARA_ID"));
                    tempMap.put("paraValue", map.get("PARA_VALUE"));

                    tempParaList.add(tempMap);
                }
            }
            Map rspMap = new HashMap();
            rspMap.put("returnAllNum", resultMap.get("RESULT_ALL_NUM"));
            rspMap.put("cardInfo", tempInfoList);
            rspMap.put("para", tempParaList);

            System.out.println("Test rspMap:" + rspMap);

            exchange.getOut().setBody(rspMap);

        }
        else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }
    }
}
