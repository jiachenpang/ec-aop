package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class YearPayEcs4CBSSSub extends YearPayEcs4CBSSCheck {

    protected String tradeId = "111";
    protected String userId = "111";
    protected String custId = "111";
    protected String acctId = "111";
    protected String itemId = "111";
    // 资费专属itemId
    protected String discntItemId = "111";

    @Override
    public Map yearPayEcsSub(Exchange exchange, Map preSubmitRet, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        preSubmitRet.put("provOrderId", preSubmitRet.get("provOrderId"));
        preSubmitRet.put("orderNo", preSubmitRet.get("provOrderId"));
        preSubmitRet.put("operationType", "01");
        preSubmitRet.put("origTotalFee", "0");
        preSubmitRet.put("cancleTotalFee", preSubmitRet.get("totalFee"));
        preSubmitRet.put("noteType", "1");
        // {totalFee=72000, provOrderId=1716092858663743, bssOrderId=1716092858663743,
        // feeInfo=[{feeId=400000, maxRelief=0, feeCategory=2, origFee=72000, feeDes=[预存]带包一年-包月费)}]}
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", preSubmitRet.get("provOrderId"));
        subOrderSub.put("subOrderId", preSubmitRet.get("provOrderId"));
        List<Map> feeInfos = (List<Map>) preSubmitRet.get("feeInfo");
        List<Map> feeInfoList = new ArrayList<Map>();
        if (feeInfos != null && 0 != feeInfos.size()) {
            for (Map feeInfo : feeInfos) {
                Map retFee = new HashMap();
                retFee.put("feeCategory", feeInfo.get("feeCategory"));
                retFee.put("feeId", feeInfo.get("feeId"));
                retFee.put("feeDes", feeInfo.get("feeDes"));
                retFee.put("operateType", "1");
                retFee.put("origFee", preSubmitRet.get("totalFee"));
                retFee.put("calculateTag", "N");
                retFee.put("calculateId", GetSeqUtil.getSeqFromCb());
                retFee.put("calculateDate", GetDateUtils.getDate());
                feeInfoList.add(retFee);

            }
        }
        subOrderSub.put("feeInfo", feeInfoList);
        subOrderSubReq.add(subOrderSub);
        preSubmitRet.put("subOrderSubReq", subOrderSubReq);
        preSubmitRet.putAll(msg);
        Map body = new HashMap();
        body.put("msg", preSubmitRet);
        exchange.getIn().setBody(body);

        new LanUtils().preData("ecaop.trades.mpsb.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        new LanUtils().xml2Json("ecaop.trades.mpsb.template", exchange);
        Map out = exchange.getOut().getBody(Map.class);

        List feeList = new ArrayList();
        Map tempFeeMap = new HashMap();

        List<Map> provinceOrderInfo = (List<Map>) (preSubmitRet.get("provinceOrderInfo"));
        Map provinceInfo = provinceOrderInfo.get(0);
        List<Map> province = (List<Map>) provinceInfo.get("preFeeInfoRsp");

        Map rspMap = new HashMap();

        rspMap.put("code", "0000");
        rspMap.put("detail", "成功");
        rspMap.put("provOrderId", preSubmitRet.get("provOrderId"));//
        rspMap.put("orderNo", preSubmitRet.get("provOrderId"));

        return rspMap;

    }

    private Map dealSubmit(Map submitMap, Object orderNo, Map rspInfo) {
        Map retMap = new HashMap();
        Object provOrderId = rspInfo.get("bssOrderId");
        submitMap.put("provOrderId", provOrderId);
        submitMap.put("orderNo", rspInfo.get("provOrderId"));
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSub.put("subOrderId", orderNo);
        List<Map> provinceOrderInfo = (List<Map>) rspInfo.get("provinceOrderInfo");
        if (IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            subOrderSub.put("subProvinceOrderId", provOrderId);
            subOrderSubReq.add(subOrderSub);
            submitMap.put("subOrderSubReq", subOrderSubReq);
            submitMap.put("origTotalFee", "0");
        }
        else {
            List<Map> fee = new ArrayList<Map>();
            List<Map> retFee = new ArrayList<Map>();
            int totalFee = 0;
            for (Map provinceOrder : provinceOrderInfo) {
                List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (IsEmptyUtils.isEmpty(feeInfo)) {
                    continue;
                }
                for (Map feeMap : feeInfo) {
                    Map temp = new HashMap();
                    temp.put("feeCategory", feeMap.get("feeMode"));
                    temp.put("feeId", feeMap.get("feeTypeCode"));
                    temp.put("feeDes", feeMap.get("feeTypeName"));
                    temp.put("operateType", "1");
                    temp.put("origFee", feeMap.get("fee"));
                    temp.put("realFee", feeMap.get("fee"));
                    temp.put("calculateTag", "N");
                    temp.put("calculateId", provOrderId);
                    fee.add(temp);
                }
                retFee.addAll(feeInfo);
                totalFee += Integer.valueOf(provinceOrder.get("totalFee").toString());
            }
            subOrderSub.put("feeInfo", fee);
            subOrderSub.put("subProvinceOrderId", provOrderId);
            subOrderSubReq.add(subOrderSub);
            submitMap.put("subOrderSubReq", subOrderSubReq);
            submitMap.put("origTotalFee", totalFee);
        }
        submitMap.put("cancleTotalFee", "0");
        return retMap;
    }
}
