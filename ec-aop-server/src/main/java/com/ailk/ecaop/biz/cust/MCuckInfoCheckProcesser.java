package com.ailk.ecaop.biz.cust;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("mCuckInfoCheck")
public class MCuckInfoCheckProcesser extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        List<Map> custInfo = new ArrayList<Map>();
        Map outMap = new HashMap();
        Map<String, Object> out = exchange.getOut().getBody(Map.class);
        Object code = out.get("code");
        if (null != code && "0001".equals(code)) {
            throw new EcAopServerBizException("0001", out.get("detail").toString());
        }
        List<Map> custInfoList = (List<Map>) out.get("existedCustomer");
        if (custInfoList == null || custInfoList.isEmpty()) {
            return;
        }
        for (Map temapMap : custInfoList) {
            if ("1".equals(temapMap.get("blackListFlag"))) {
                throw new EcAopServerBizException("0203", "黑名单");
            }
            if ("1".equals(temapMap.get("maxUserFlag"))) {
                throw new EcAopServerBizException("0204", "最大用户数");
            }
            // 客户Id超过16位，剔除
            Object custId = temapMap.get("custId");
            if (null != custId && custId.toString().length() > 16) {
                temapMap.remove("custId");
            }
            List<Map> arrearageMessList = (List) temapMap.get("arrearageMess");
            Object arrearageFlag = temapMap.get("arrearageFlag");
            if (null == arrearageFlag || "".equals(arrearageFlag)) {
                arrearageFlag = "0";
            }
            if ("1".equals(arrearageFlag)) {
                Map temp = new HashMap();
                if (null == arrearageMessList || 0 == arrearageMessList.size()) {
                    throw new EcAopServerBizException("9999", "Sorry,arrearageMess is null");
                }
                for (int i = 0; i < arrearageMessList.size(); i++) {
                    temp = arrearageMessList.get(i);
                    String feeString = (String) temp.get("arrearageFee");
                    feeString = String.valueOf(Integer.parseInt(feeString) / 10);
                    temp.put("arrearageFee", feeString);
                }
            }
            temapMap.put("arrearageFlag", arrearageFlag);
            Map custMap = new HashMap();
            MapUtils.arrayPut(custMap, temapMap, new String[] { "certType", "certNum", "certAdress", "customerType",
                    "customerLevel", "customerLoc", "customerName", "custId", "certExpireDate", "contactPerson",
                    "contactPhone", "arrearageFlag", "arrearageMess", "groupId", "para" });
            custInfo.add(custMap);
        }
        outMap.put("custInfo", custInfo);
        exchange.getOut().setBody(outMap);
    }
}
