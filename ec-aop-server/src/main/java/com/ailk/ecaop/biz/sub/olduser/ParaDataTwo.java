package com.ailk.ecaop.biz.sub.olduser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;

@EcRocTag("ParaDataTwo")
public class ParaDataTwo extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String origTotalFee = (String) msg.get("origTotalFee");
        origTotalFee = origTotalFee;
        msg.put("origTotalFee", origTotalFee);
        msg.put("cancleTotalFee", origTotalFee);
        String provOrderId = (String) msg.get("essSubscribeId");
        msg.put("orderNo", provOrderId);
        msg.put("noteFlag", "1");
        msg.put("noteType", "1");
        // payInfo信息全
        if (msg.get("payInfo") instanceof Map) {
            Map payInfo = (Map) msg.get("payInfo");
            payInfo.put("payType", ChangeCodeUtils.changePayType4CB(payInfo.get("payType")));
            payInfo.put("payOrg", msg.get("payOrg"));
            payInfo.put("payNum", msg.get("payNum"));
            payInfo.put("subProvinceOrderId", provOrderId);
            payInfo.put("payMoney", origTotalFee);
            msg.put("payInfo", payInfo);
        }
        else {
            // 对接北分卖场，payInfo规范改为LIST
            // Map payInfo = (Map) msg.get("payInfo");
            List<Map> payInfos = new ArrayList();
            List<Map> payInfosReq = (List<Map>) msg.get("payInfo");
            for (Map payInfo : payInfosReq) {
                payInfo.put("payType", ChangeCodeUtils.changePayType4CB(payInfo.get("payType")));
                payInfo.put("payOrg", payInfo.get("payOrg"));
                payInfo.put("payNum", payInfo.get("payNum"));
                payInfo.put("payMoney", payInfo.get("payFee"));
                payInfo.put("subProvinceOrderId", provOrderId);
                // payInfo.put("payMoney", origTotalFee);
                payInfos.add(payInfo);
            }
            msg.put("payInfo", payInfos);
        }
        // subOrderSubReq参数
        List subOrderSubReq = new ArrayList();
        Map fee = new HashMap();
        fee.put("subOrderId", provOrderId);
        fee.put("subProvinceOrderId", provOrderId);
        // feeInfo信息
        List<Map> feeInfos = (List<Map>) msg.get("feeInfo");
        List<Map> feeInfoList = new ArrayList();

        if (feeInfos != null) {
            for (Map feeInfo1 : feeInfos) {
                String feeId = (String) feeInfo1.get("feeId");
                String feeDes = (String) feeInfo1.get("feeDes");
                String origFee = (String) feeInfo1.get("origFee");
                String reliefFee = (String) feeInfo1.get("reliefFee");
                String realFee = (String) feeInfo1.get("realFee");
                Map feeInfo = new HashMap();
                feeInfo.put("reliefFee", reliefFee);// 规范有，报文无
                feeInfo.put("operateType", "1");
                // feeInfo.put("payId", "1115122101882368");
                String acceptDate = GetDateUtils.getDate();
                feeInfo.put("calculateDate", GetDateUtils.transDate(acceptDate, 14));
                feeInfo.put("payTag", "1");
                feeInfo.put("calculateStaffId", "");// 报文是空的
                feeInfo.put("reliefResult", feeInfo1.get("reliefResult"));
                feeInfo.put("calculateTag", "N");// 前端没有？
                feeInfo.put("feeDes", feeDes);
                feeInfo.put("feeId", feeId);
                feeInfo.put("feeCategory", feeInfo1.get("feeCategory"));
                feeInfo.put("calculateId", GetSeqUtil.getSeqFromCb());
                feeInfo.put("origFee", origFee);
                feeInfo.put("realFee", realFee);
                feeInfoList.add(feeInfo);
            }
        }
        fee.put("feeInfo", feeInfoList);
        subOrderSubReq.add(fee);
        msg.put("subOrderSubReq", subOrderSubReq);
        msg.put("operationType", "01");
        msg.put("sendTypeCode", "0");
        msg.put("noteNo", msg.get("invoiceNo")); // 发票号码
        msg.put("provOrderId", provOrderId);
    }

    // private String feeExchang(String str) {
    // String value1 = null;
    // try {
    // int v1 = Integer.parseInt(str);
    // if (v1 % 10 != 0) {
    // throw new EcAopRequestParamException("费用从厘转换为分失败");
    // }
    // v1 = v1 / 10;
    // value1 = v1 + "";
    // return value1;
    // }
    // catch (Exception e) {
    // throw new EcAopRequestParamException(e.getMessage());
    // }
    // }

}
