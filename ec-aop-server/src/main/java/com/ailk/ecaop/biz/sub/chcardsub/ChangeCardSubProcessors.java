package com.ailk.ecaop.biz.sub.chcardsub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("changeCardSub")
public class ChangeCardSubProcessors extends BaseAopProcessor {
    private final Logger log = LoggerFactory
            .getLogger(ChangeCardSubProcessors.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        
        msg.putAll(preBaseData(exchange));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.occs.sureSub.paramtersmapping", exchange);
        CallEngine.wsCall(exchange,
                "ecaop.comm.conf.url.cbss.services.orderSub");
        /*
         * String reback = "<?xml version=\"1.0\"?>" +
         * "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
         * + "<SOAP-ENV:Header />" + "<SOAP-ENV:Body>" +
         * "<m:ORDERSUB_OUTPUT xmlns:m=\"http://ws.chinaunicom.cn/OrdSer/unibssBody\">"
         * + "<h:UNI_BSS_HEAD xmlns:h=\"http://ws.chinaunicom.cn/unibssHead\">"
         * + "<h:ORIG_DOMAIN>ULTE</h:ORIG_DOMAIN>" +
         * "<h:SERVICE_NAME>OrdSer</h:SERVICE_NAME>" +
         * "<h:OPERATE_NAME>orderSub</h:OPERATE_NAME>" +
         * "<h:ACTION_CODE>1</h:ACTION_CODE>" +
         * "<h:ACTION_RELATION>0</h:ACTION_RELATION>" + "<h:ROUTING>" +
         * "<h:ROUTE_TYPE>00</h:ROUTE_TYPE>" +
         * "<h:ROUTE_VALUE>76</h:ROUTE_VALUE>" + "</h:ROUTING>" +
         * "<h:PROC_ID>2014122666472026</h:PROC_ID>" +
         * "<h:TRANS_IDO>2014122605299190</h:TRANS_IDO>" + "<h:TRANS_IDH />" +
         * "<h:PROCESS_TIME>20141226170105</h:PROCESS_TIME>" + "<h:RESPONSE>" +
         * "<h:RSP_TYPE>0</h:RSP_TYPE>" + "<h:RSP_CODE>0000</h:RSP_CODE>" +
         * "<h:RSP_DESC>ok</h:RSP_DESC>" + "</h:RESPONSE>" + "<h:COM_BUS_INFO>"
         * + "<h:OPER_ID>hasc-guoli6</h:OPER_ID>" +
         * "<h:PROVINCE_CODE>76</h:PROVINCE_CODE>" +
         * "<h:EPARCHY_CODE>771</h:EPARCHY_CODE>" +
         * "<h:CITY_CODE>762158</h:CITY_CODE>" +
         * "<h:CHANNEL_ID>76b08kc</h:CHANNEL_ID>" +
         * "<h:CHANNEL_TYPE>2010100</h:CHANNEL_TYPE>" +
         * "<h:ACCESS_TYPE>01</h:ACCESS_TYPE>" +
         * "<h:ORDER_TYPE>01</h:ORDER_TYPE>" + "</h:COM_BUS_INFO>" +
         * "<h:SP_RESERVE>" + "<h:TRANS_IDC>1111111111111</h:TRANS_IDC>" +
         * "<h:CUTOFFDAY>20110322</h:CUTOFFDAY>" + "<h:OSNDUNS>9900</h:OSNDUNS>"
         * + "<h:HSNDUNS>9900</h:HSNDUNS>" +
         * "<h:CONV_ID>20110322202800100</h:CONV_ID>" + "</h:SP_RESERVE>" +
         * "<h:TEST_FLAG>0</h:TEST_FLAG>" + " <h:MSG_SENDER>0003</h:MSG_SENDER>"
         * + "<h:MSG_RECEIVER>CBSS</h:MSG_RECEIVER>" + "</h:UNI_BSS_HEAD>" +
         * "<m:UNI_BSS_BODY>" +
         * "<n-1665002764:ORDERSUB_RSP xmlns:n-1665002764=\"http://ws.chinaunicom.cn/OrdSer/unibssBody/orderSubRsp\">"
         * + "<n-1665002764:RESP_CODE>0000</n-1665002764:RESP_CODE>" +
         * "<n-1665002764:RESP_DESC>TradeOk</n-1665002764:RESP_DESC>" +
         * "</n-1665002764:ORDERSUB_RSP>" + " </m:UNI_BSS_BODY>" +
         * "<a:UNI_BSS_ATTACHED xmlns:a=\"http://ws.chinaunicom.cn/unibssAttached\" />"
         * + " </m:ORDERSUB_OUTPUT>" + " </SOAP-ENV:Body>" +
         * "</SOAP-ENV:Envelope>"; exchange.getOut().setBody(reback);
         */
        lan.xml2Json("ecaop.trades.occs.sureSub.template", exchange);
        Map ordersId = new HashMap();
        ordersId.put("bssOrderId", msg.get("ordersId"));
        exchange.getOut().setBody(ordersId);
        log.debug(exchange.getOut().getBody().toString());
    }

    private Map preBaseData(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        
        List<Map> feeInfolist = (List<Map>) msg.get("feeInfo");
        Map subOrderSubReq = new HashMap();
        List<Map> subOrderSubReqlist = new ArrayList<Map>();

        boolean isOlnyOneFee = true;
        // 将多缴的预存和[预存]营业厅收入(营业缴费)_普通预存款合并
        int addMoney = 0;
        int oldAddMoney = 0;
        for(Map feeInfo : feeInfolist){
            // 在提交的时候预写的预存
            if ("100000".equals(feeInfo.get("feeId"))) {
                addMoney += Integer.valueOf(feeInfo.get("realFee") + "");
                oldAddMoney += Integer.valueOf(feeInfo.get("origFee") + "");
            }
        }
        for (Map feeInfo : feeInfolist) {
            feeInfo.put("operateType", "1");
            if (!"100000".equals(feeInfo.get("feeId"))){
                String feeId = feeInfo.get("feeId")+"";
                String feeMod = feeInfo.get("feeCategory")+"";

                if ("99".equals(feeId))
                {
                    feeId = "100000";
                }
                if ("4".equals(feeMod))
                {
                    feeMod = "2";
                }
                
                feeInfo.put("feeCategory", feeMod);
                if ("100000".equals(feeId))// 如果有预存就加，不是预存的话，就取原来的值
                {
                    feeInfo.put("origFee",Integer.valueOf(feeInfo.get("origFee") + "")/ 10 + oldAddMoney) ;
                    feeInfo.put("reliefFee", Integer.valueOf(feeInfo.get("reliefFee") + "")/ 10 + addMoney) ;
                    feeInfo.put("realFee",Integer.valueOf(feeInfo.get("realFee") + "")/ 10 + addMoney) ;
                }else{
                    feeInfo.put("origFee",Integer.valueOf(feeInfo.get("origFee") + "")/ 10) ;
                    feeInfo.put("reliefFee", Integer.valueOf(feeInfo.get("reliefFee") + "")/ 10) ;
                    feeInfo.put("realFee",Integer.valueOf(feeInfo.get("realFee") + "")/ 10) ;
                }
                
                feeInfo.put("isPay", "1");
                feeInfo.put("payTag", "1");
                feeInfo.put("calculateId", GetSeqUtil.getSeqFromCb(exchange, "seq_trade_id"));
                feeInfo.put("calculateTag", "N");
//                if (!"0".equals(feeInfo.get("origFee") + "")) // 去掉0费用情况
//                    feeInfolist.put("feeInfo", feeInfolist);

                isOlnyOneFee = false;
            }

        }
        
        Object payType=new ChangeCodeUtils().changePayType(((Map) msg.get("payInfo")).get("payType"));
        ((Map)msg.get("payInfo")).put("payType", payType);
        ((Map)msg.get("payInfo")).put("subProvinceOrderId", msg.get("essSubscribeId"));
        
        msg.remove("feeInfo");
       
        subOrderSubReq.put("subProvinceOrderId", msg.get("ordersId"));
        subOrderSubReq.put("subProvinceOrderId", msg.get("essSubscribeId"));
        subOrderSubReq.put("feeInfo", feeInfolist);
        subOrderSubReqlist.add(subOrderSubReq);
        msg.put("provOrderId", msg.get("essSubscribeId"));
        msg.put("orderNo", msg.get("essSubscribeId"));
        msg.put("operationType", "01");
        msg.put("sendTypeCode", "0");
        msg.put("noteNo", null!=msg.get("invoiceNo")?msg.get("invoiceNo"):"11111111111111");
        msg.put("noteType", "1");
        msg.put("noteFlag", "1");
        msg.put("subOrderSubReq", subOrderSubReqlist);
        msg.put("origTotalFee", "0");
        msg.put("cancleTotalFee", "0");
        return msg;

    }
}
