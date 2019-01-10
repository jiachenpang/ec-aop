package com.ailk.ecaop.biz.cust;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("queryCustNum")
public class QueryCustNumProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        String certTypeCode = msg.get("certType").toString();
        String cbssCertTypeCode = CertTypeChangeUtils.certTypeMall2Cbss(certTypeCode);
        boolean isGroupCertType = "07|13|14|15|17|18|21".contains(certTypeCode);
        LanUtils lan = new LanUtils();
        if (isGroupCertType) {
            msg.put("operateType", "13");
        }
        else {
            msg.put("operateType", "1");
        }
        msg.put("certTypeCode", cbssCertTypeCode);
        msg.put("certCode", msg.get("certNum"));
        lan.preData("ecaop.trade.qccq.qryCustInfoParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.CustSer");

        // 此处用NoError,防止CBSS应答码不为0000,但是返回用户号码的情况,如：欠费,黑名单等.
        lan.xml2JsonNoError("ecaop.trade.qccq.qryCustInfoTemplate", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) retMap.get("custInfo");
        if (null == custInfo || 0 == custInfo.size()) {
            throw new EcAopServerBizException(retMap.get("code").toString(), retMap.get("detail").toString());
        }
        List<Map> numberList = new ArrayList<Map>();
        for (Map cust : custInfo) {
            List<Map> userList = (List<Map>) cust.get("userList");
            if (null == userList || 0 == userList.size()) {
                continue;
            }
            for (Map user : userList) {
                numberList.add(MapUtils.asMap("serialNumber", user.get("serialNumber")));
            }
        }
        if (0 == numberList.size()) {
            throw new EcAopServerBizException("8888", "核心系统未返回用户号码信息");
        }
        exchange.getOut().setBody(MapUtils.asMap("numInfo", numberList));
    }
}
