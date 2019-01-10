package com.ailk.ecaop.biz.bank;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.CallProcessorUtils;

@EcRocTag("bankOrderCommitProcesser")
public class BankOrderCommitProcesser extends BaseAopProcessor implements ParamsAppliable {

    private String provOrderId;
    private ExchangeCache cache;

    @Override
    public void process(Exchange exchange) throws Exception {
        cache = new BankOrderCommitProcesser.ExchangeCache();
        cache.body = (Map) exchange.getProperty("bankAppInParam");
        cache.msg = (Map) cache.body.get("msg");

        // 准备预提交接口请求参数
        preBankDeductApp(exchange);

        // 调用预提交接口
        preBankDeductAppCommit(exchange);

        // 对返回进行处理
        provOrderId = dealReturn(exchange);

        // 准备提交参数
        preBankCommit(exchange);
        try {
            // 调用提交接口
            preBankDeductCommit(exchange);

            // 准备成功通知接口参数
            preBankNotify(true, exchange);

        }
        catch (EcAopServerBizException e) {

            // 准备失败通知接口参数
            preBankNotify(false, exchange);
        }
        finally {

            // 调用通知接口
            bankNotify(exchange);

            // 处理通知接口返回
            dealNotify(exchange);
        }
    }

    private void dealNotify(Exchange exchange) {
        Map msg = cache.msg;
        if (!"01".equals(msg.get("dealResult"))) {
            throw new EcAopServerBizException("9999", "银行卡业务提交失败");
        }

        Map outMap = new HashMap();
        outMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(outMap);
    }

    private void bankNotify(Exchange exchange) throws Exception {
        CallProcessorUtils.newCallIFaceUtils(exchange)
                .preData("ecaop.ecsb.mcba.notifyParametersMapping")
                .wsCall("ecaop.comm.conf.url.osn.services.autoRemoveTreatyNotifySer")
                .xml2Json("ecaop.ecsb.mcba.notifytemplate");
    }

    private void preBankDeductCommit(Exchange exchange) throws Exception {
        CallProcessorUtils.newCallIFaceUtils(exchange)
                .preData("ecaop.masb.odsb.ActivityAryParametersMapping")
                .wsCall("ecaop.comm.conf.url.osn.services.ordser")
                .xml2Json("ecaop.masb.odsb.template");
    }

    private void preBankDeductAppCommit(Exchange exchange) throws Exception {
        CallProcessorUtils.newCallIFaceUtils(exchange)
                .preData("ecaop.ecsb.mcba.ParametersMapping")
                .wsCall("ecaop.comm.conf.url.osn.services.bankPaymentSer")
                .xml2Json("ecaop.ecsb.mcba.template");
        cache.out = exchange.getOut().getBody(Map.class);
    }

    private void preBankCommit(Exchange exchange) {
        Map msg = cache.msg;
        msg.put("provOrderId", provOrderId);
        msg.put("noteType", 1);
        msg.put("orderNo", msg.get("orderId"));
        msg.put("origTotalFee", 0);
        msg.put("cancleTotalFee", 0);
        exchange.getIn().setBody(copyBody(cache.body));
    }

    private String dealReturn(Exchange exchange) {
        Map respInfo = (Map) cache.out.get("respInfo");

        if (respInfo == null || respInfo.get("provOrderId") == null) {
            throw new EcAopServerBizException("9999", "预提交返回信息为空");
        }
        return (String) respInfo.get("provOrderId");
    }

    private void preBankDeductApp(Exchange exchange) {
        Map msg = cache.msg;
        String method = exchange.getMethodCode();
        if ("mcba".equals(method)) {
            msg.put("tradeTypeCode", "0127");

        }
        if ("mcbd".equals(method)) {
            msg.put("tradeTypeCode", "0128");

        }
        Map accountInfo = new HashMap();
        accountInfo.put("accountId", msg.get("accountId"));
        accountInfo.put("bankName", msg.get("bankName"));
        accountInfo.put("bankCardType", msg.get("bankCardType"));
        accountInfo.put("accountLastFour", msg.get("accountLastFour"));
        accountInfo.put("contractNumber", msg.get("contractNumber"));
        accountInfo.put("levelValue", msg.get("levelValue"));
        accountInfo.put("everyValue", msg.get("everyValue"));
        accountInfo.put("appDate", msg.get("appDate"));
        accountInfo.put("actorName", msg.get("actorName"));
        accountInfo.put("actorCerttypeId", msg.get("actorCerttypeId"));
        accountInfo.put("actorCertnum", msg.get("actorCertnum"));
        msg.put("accountInfo", accountInfo);
        exchange.getIn().setBody(copyBody(cache.body));
    }

    private void preBankNotify(boolean success, Exchange exchange) {
        Map msg = cache.msg;
        msg.put("eparchyCode", msg.get("city"));
        msg.put("dealDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        if (success) {
            msg.put("dealResult", "01");
        }
        else {
            msg.put("dealResult", "02");
        }
        exchange.getIn().setBody(copyBody(cache.body));
    }

    private Map copyBody(Map map) {
        return new HashMap(map);
    }

    @Override
    public void applyParams(String[] params) {

    }

    static class ExchangeCache {

        private Map body;
        private Map msg;
        private Map out;

    }

}
