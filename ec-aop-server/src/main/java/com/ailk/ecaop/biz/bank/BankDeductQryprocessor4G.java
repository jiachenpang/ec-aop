package com.ailk.ecaop.biz.bank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("bankDeductQryprocessor4G")
public class BankDeductQryprocessor4G extends BaseAopProcessor implements ParamsAppliable {

    private Map<String, Object> body;

    // 银行卡代扣状态查询返回处理
    @Override
    public void process(Exchange exchange) throws Exception {

        body = exchange.getOut().getBody(Map.class);

        if (body.get("signList") == null) {
            throw new EcAopServerBizException("9999", "签约列表返回为空");
        }
        List<Map> signList = (List<Map>) body.get("signList");
        if (signList.size() == 0) {
            throw new EcAopServerBizException("9999", "签约列表返回为空");
        }
        for (Map map : signList) {
            // 获取账户信息
            Map accountInfo = (Map) map.get("accountInfo");
            String signState = (String) accountInfo.get("signState");
            if (null == accountInfo || signState == null) {
                throw new EcAopServerBizException("9999", "ECS无签约记录");
            }

            // 判断签约状态 00--已签约 返回组装账户信息 01--未签约 ||02--其他 账户信息为空
            if (signState.equals("01") || signState.equals("02")) {
                body.put("signState", signState);
                body.put("startTime", null == accountInfo.get("startTime") ? "" : accountInfo.get("startTime"));
                body.remove("signList");
                exchange.getOut().setBody(body);
            }
            if (signState.equals("00")) {
                Map account = new HashMap();
                account.put("bankName", accountInfo.get("bankName"));
                account.put("bankCardType", accountInfo.get("bankCardType"));
                account.put("accountLastFour", accountInfo.get("accountLastFour"));
                account.put("contractNumber", accountInfo.get("contractNumber"));
                account.put("levelValue", accountInfo.get("levelValue"));
                account.put("everyValue", accountInfo.get("everyValue"));
                body.put("accountInfo", account);
                body.put("signState", signState);
                body.put("startTime", null == accountInfo.get("startTime") ? "" : accountInfo.get("startTime"));
                body.remove("signList");
                exchange.getOut().setBody(body);
                break;
            }
        }
    }

    @Override
    public void applyParams(String[] params) {

    }

}
