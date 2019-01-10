package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.processor.AopHandler;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.FlowProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("checkAndSentMISPOSInfoToCBTest")
public class CheckAndSentMISPOSInfoToCBTestProcessor extends BaseAopProcessor implements ParamsAppliable {

    /**
     *卖场通过该支付流水与支付平台进行交互，对订单进行支付，支付成功后发起订单正式提交，AOP通过支付信息同步接口（接口2）将MISPOS支付信息同步给cBSS后，再调用正式提交接口。
     *2017-3-27新逻辑修改：a).裸机销售时，tradeId从CB取;b).封装18个接口报错，返回特定code;c).校验多个paymentInfoSyncReq里单次消费金额tradeAmt与payFee总金额关系
     */
    private static final String[] PARAM_ARRAY = { "ecaop.param.paid.paymentInfoSync.paramtersmapping",
            "ecaop.masb.chph.gifa.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    private final String ERROECODE = "0008";

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void process(Exchange exchange) throws Exception {
        Map inBody = exchange.getIn().getBody(Map.class);
        Map msg = inBody.get("msg") instanceof String ? JSON.parseObject((String) inBody.get("msg")) : (Map) inBody
                .get("msg");
        List<Map> payInfoList = null;
        List<Map> paymentInfoSyncReq = null;

        if (msg.get("payInfo") instanceof Map)
        {
            payInfoList = new ArrayList();
            payInfoList.add((Map) msg.get("payInfo"));
        }
        else
        {
            payInfoList = new ArrayList((List) msg.get("payInfo"));
        }
        System.out.println("CheckAndSentMISPOSInfoToCBTestProcessor_payInfoList:" + payInfoList);
        boolean isMISPOS = false;
        if (null != payInfoList && payInfoList.size() > 0)
        {
            for (Map payInfo : payInfoList)
            {
                //25    MISPOS
                if (!"25".equals(payInfo.get("payType") + ""))
                    continue;
                paymentInfoSyncReq = (List<Map>) payInfo.get("paymentInfoSyncReq");
                if (null == paymentInfoSyncReq || paymentInfoSyncReq.size() < 1)
                    continue;
                isMISPOS = true;
                System.out.println("CheckAndSentMISPOSInfoToCBTestProcessor_paymentInfoSyncReq:" + paymentInfoSyncReq);
                checkOriginalBusiAmt(paymentInfoSyncReq, (String) payInfo.get("payFee"));
                callPaymentInfoSync(exchange, msg, paymentInfoSyncReq);
            }
        }
        try
        {
            AopHandler handler = new AopHandler();
            handler.applyParams(new String[] { "ecaop.trades.mispos." + exchange.getMethodCode() + ".processors" });
            handler.process(exchange);
            System.out.println("CheckAndSentMISPOSInfoToCBTestProcessor_handler:"
                    + exchange.getOut().getBody().toString());
        }
        catch (EcAopServerSysException e)
        {
            throw new EcAopServerSysException(isMISPOS ? ERROECODE : null == e.getCode() ? "9999" : e.getCode(),
                    isMISPOS ? "MISPOS支付同步后正式提交报错：" + e.getMessage() : e.getMessage());
        }
        catch (EcAopServerBizException e)
        {
            throw new EcAopServerBizException(isMISPOS ? ERROECODE : null == e.getCode() ? "9999" : e.getCode(),
                    isMISPOS ? "MISPOS支付同步后正式提交报错：" + e.getMessage() : e.getMessage());
        }
        catch (Exception e)
        {
            throw new EcAopServerSysException(isMISPOS ? ERROECODE : "9999", isMISPOS ? "MISPOS支付同步后正式提交报错："
                    + e.getMessage() : e.getMessage());
        }
        Map out = exchange.getOut().getBody(Map.class);
        if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE"))))
        {
            throw new EcAopServerBizException(isMISPOS ? ERROECODE : null == out.get("code") ? "9999" : out.get("code")
                    + "", isMISPOS ? "MISPOS支付同步后正式提交报错：" + out.get("detail") : out.get("detail") + "");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void callPaymentInfoSync(Exchange exchange, Map msg, List<Map> paymentInfoSyncReq) {
        for (Map paymentInfoSync : paymentInfoSyncReq)
        {
            //裸机销售时，tradeId从CB取
            if ("sjxs,mytest".contains(exchange.getMethodCode()))
            {
                String tradeId = GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 1)
                        .get(0) + "";
                paymentInfoSync.put("tradeId", tradeId);
            }
            Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
            Map tempInBody = tempExchange.getIn().getBody(Map.class);
            Map tempMsg = new HashMap(msg);
            tempMsg.putAll(paymentInfoSync);
            tempInBody.put("msg", tempMsg);
            System.out.println("tempMsg:" + tempMsg);
            tempExchange.getIn().setBody(tempInBody);
            LanUtils lan = new LanUtils();
            lan.preData(pmp[0], tempExchange);
            try
            {
                CallEngine.wsCall(tempExchange, "ecaop.comm.conf.url.cbss.services.other.TerminalSaleAopSer");// URL未知
                lan.xml2Json("ecaop.param.paid.paymentInfoSync.template", tempExchange);
            }
            catch (EcAopServerSysException e)
            {
                throw new EcAopServerSysException(null == e.getCode() ? "9999" : e.getCode(), "支付信息同步接口报错,交易tradeId为："
                        + paymentInfoSync.get("tradeId") + ";" + e.getMessage());
            }
            catch (EcAopServerBizException e)
            {
                throw new EcAopServerBizException(null == e.getCode() ? "9999" : e.getCode(), "支付信息同步接口报错,交易tradeId为："
                        + paymentInfoSync.get("tradeId") + ";" + e.getMessage());
            }
            catch (Exception e)
            {
                throw new EcAopServerSysException("9999", "支付信息同步接口报错,交易tradeId为：" + paymentInfoSync.get("tradeId") + ";"
                        + e.getMessage());
            }
            Map out = tempExchange.getOut().getBody(Map.class);
            if (!"200".equals(String.valueOf(tempExchange.getProperty("HTTP_STATUSCODE"))))
            {
                throw new EcAopServerBizException(null == out.get("code") ? "9999" : out.get("code") + "",
                        "支付信息同步接口报错,交易tradeId为：" + paymentInfoSync.get("tradeId") + ";" + out.get("detail"));
            }
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++)
        {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    @SuppressWarnings("rawtypes")
    private void checkOriginalBusiAmt(List<Map> paymentInfoSyncReq, String payFee) {
        if (null == payFee || "".equals(payFee) || "null".equals(payFee))
            return;
        int total = 0;
        int amt = 0;
        for (Map paymentInfo : paymentInfoSyncReq)
        {
            String tradeAmt = (String) paymentInfo.get("tradeAmt");//单位：分
            try
            {
                total = total + Integer.parseInt(tradeAmt) * 10;
            }
            catch (Exception e)
            {
                throw new EcAopServerBizException("9999", "ERR_MISPOS-01,tradeAmt转换报错：" + e.getMessage());
            }
        }
        try
        {
            amt = Integer.parseInt(payFee);//单位是厘
        }
        catch (Exception e)
        {
            throw new EcAopServerBizException("9999", "ERR_MISPOS-02,payFee转换报错：" + e.getMessage());
        }
        if (amt != total)
        {
            throw new EcAopServerBizException("9999", "ERR_MISPOS-03,mispos支付金额payFee不是各分项消费金额tradeAmt的总合，请核实。");
        }
    }
}
