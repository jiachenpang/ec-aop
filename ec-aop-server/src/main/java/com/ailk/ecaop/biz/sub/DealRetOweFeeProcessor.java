package com.ailk.ecaop.biz.sub;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;

@EcRocTag("DealRetOweFee")
public class DealRetOweFeeProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Object oweFeeMap = exchange.getOut().getBody();
        Map out = new HashMap();
        if (oweFeeMap instanceof String) {
            out = JSON.parseObject(oweFeeMap.toString());
        }
        else {
            out = (Map) oweFeeMap;
        }
        String code = (String) out.get("code");
        String detail = (String) out.get("detail");
        List<Map> itemInfoList = null;
        List<Map> oweFeeInfoList = (List<Map>) out.get("oweFeeInfo");
        if (null == oweFeeInfoList || oweFeeInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "未返回欠费信息");
        }
        List<Map> cycleOweFeeInfo = (List<Map>) oweFeeInfoList.get(0).get("cycleOweFeeInfo");
        if (null != cycleOweFeeInfo && !cycleOweFeeInfo.isEmpty()) {
            for (Map cycFeeMap : cycleOweFeeInfo) {
                cycFeeMap.put("receiveFee", cycFeeMap.get("receivedFee"));
                cycFeeMap.put("receiveLateFee", cycFeeMap.get("receivedLateFee"));
                cycFeeMap.put("badDebutyTag", cycFeeMap.get("badDebtTag"));
                String[] delString = { "receivedFee", "receivedLateFee", "badDebtTag" };
                deleteMapkey(cycFeeMap, delString);

            }

            itemInfoList = (List<Map>) cycleOweFeeInfo.get(0).get("itemInfo");
            for (Map itemInfo : itemInfoList) {
                itemInfo.put("itemCode", itemInfo.get("integrateItemCode"));
                itemInfo.put("ItemName", itemInfo.get("integrateItem"));
                itemInfo.put("upperItemCode", itemInfo.get("upperAcctitemCode"));
                itemInfo.put("itemCodeType", itemInfo.get("integrateItemCodeType"));
                String[] delItemString = { "integrateItemCode", "integrateItem", "upperAcctitemCode",
                        "integrateItemCodeType" };
                deleteMapkey(itemInfo, delItemString);

            }
        }

        Map newOut = new HashMap();
        newOut.put("code", code);
        newOut.put("detail", detail);
        newOut.put("oweFeeInfo", oweFeeInfoList);
        exchange.getOut().setBody(newOut);

    }

    private void deleteMapkey(Map inMap, String[] delString) {
        for (int i = 0; i < delString.length; i++) {
            Iterator<Entry> it = inMap.entrySet().iterator();
            while (it.hasNext()) {
                if (delString[i].equals(it.next().getKey())) {
                    it.remove();
                }
            }
        }

    }

}
