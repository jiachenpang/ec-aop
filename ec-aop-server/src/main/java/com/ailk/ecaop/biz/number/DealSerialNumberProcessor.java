package com.ailk.ecaop.biz.number;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("dealSerialNumber")
public class DealSerialNumberProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map head = (Map) exchange.getIn().getHeaders().get("strParams");
        Map msg = JSON.parseObject(head.get("msg").toString());
        if (!"1".equals(msg.get("is3G"))) {
            return;
        }
        if (!"1".equals(msg.get("qryCbss")) || !"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        Object outRet = exchange.getOut().getBody();
        Map out = new HashMap();
        if (outRet instanceof String) {
            out = JSON.parseObject(outRet.toString());
        }
        else {
            out = (Map) outRet;
        }
        List<Map> orgList = (List<Map>) out.get("numInfo");
        if (IsEmptyUtils.isEmpty(orgList)) {
            return;
        }
        String method = exchange.getMethodCode();
        Map reqMap = MapUtils.asMap("resourcesInfo", readySerialNumber(orgList, method));
        msg.putAll(reqMap);
        exchange.getIn().setBody(MapUtils.asMap("msg", msg));
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.cbss.checkSerialNumber.param", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.QueryIdleNumberSer");
        lan.xml2Json("ecaop.trade.cbss.checkSerialNumber.template", exchange);
        Map cbRet = exchange.getOut().getBody(Map.class);
        List<Map> cbssNumber = (ArrayList<Map>) cbRet.get("resourcesRsp");
        exchange.getOut().setBody(MapUtils.asMap("numInfo", mergeSerialNumberrList(orgList, cbssNumber, method)));
    }

    private List<Map> readySerialNumber(List<Map> orgList, String method) {
        List<Map> retList = new ArrayList<Map>();
        if (null == orgList || 0 == orgList.size()) {
            return retList;
        }
        for (Map org : orgList) {
            Map temp = new HashMap();
            temp.put("number", org.get(getNumKey(method)));
            retList.add(temp);
        }
        return retList;
    }

    /**
     * CBSS号码和接口查询号码取公共部分，此处未采用双层for循环处理，为提高效率
     * 
     * @param orgList
     * @param cbssNumber
     * @return
     */
    private List<Map> mergeSerialNumberrList(List<Map> orgList, List<Map> cbssNumber, String method) {
        List<Map> retList = new ArrayList<Map>();
        if (null == cbssNumber || 0 == cbssNumber.size()) {
            return retList;
        }
        StringBuffer serialNumber = new StringBuffer();
        for (Map number : cbssNumber) {
            serialNumber.append(number.get("number") + "|");
        }
        String numberString = serialNumber.toString();
        for (Map org : orgList) {
            if (numberString.contains(org.get(getNumKey(method)).toString())) {
                retList.add(org);
            }
        }
        return retList;
    }

    private String getNumKey(String method) {

        // 标准版和简单版的号码节点名称不一样，在此特殊处理
        return "qcsq".equals(method) ? "numID" : "numId";
    }
}
