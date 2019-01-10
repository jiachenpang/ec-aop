package com.ailk.ecaop.biz.sub;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TransFeeUtils;

/**
 * @author cuij
 * @version 创建时间：2017-4-21 下午3:35:44
 *          无串码销售与返销，销售类型：销售4008，退机4009，返销4059，换机4058
 */
@EcRocTag("sinTermSaleAndResale")
public class sinTermSaleAndResaleProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.masb.sint.sale.ParametersMapping", "ecaop.param.scss.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        LanUtils lan = new LanUtils();
        // 查询终端信息
        msg = qrysinTermInfo(exchange, msg);
        // 根据methodCode判断是销售还是返销
        String methodCode = exchange.getMethodCode();
        if ("scss".equals(methodCode)) {// 无串码销售T2000022
            msg.put("resTradeId", "4008");
            sinTerminalSale(msg, exchange);
        }
        if ("scsr".equals(methodCode)) {// 无串码返销T2000022
            msg.put("resTradeId", "4059");
            sinTerminalResale(msg, exchange);
        }
        // 处理费用信息
        msg = dealSaleInfo(msg);
        // 拼装msg节点
        msg.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        msg.put("provinceCode", msg.get("province"));
        msg.put("cityCode", msg.get("district"));
        msg.put("resTradeId", "4008");
        msg.put("subsysCode", transNetType(String.valueOf(msg.get("subsysCode"))));
        msg.put("otoOrderId", String.valueOf(msg.get("otoOrderId")));

        // 4.调用cbss接口记录订单。
        System.out.println("cb落库前body数据" + body);
        Exchange ToCBexchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[2], ToCBexchange);
        CallEngine.wsCall(ToCBexchange, "ecaop.comm.conf.url.cbss.services.TerminalSaleAopSer");
        lan.xml2Json("ecaop.param.scss.template", ToCBexchange);
        List<Map> resMap = (List) ToCBexchange.getOut().getBody(Map.class).get("respInfo");
        Map rspInfo = resMap.get(0);
        rspInfo.put("bssOrderId", msg.get("tradeId"));
        rspInfo.put("essOrderId", rspInfo.get("taxInvoiceno"));
        exchange.getOut().setBody(rspInfo);

    }

    private void sinTerminalSale(Map msg, Exchange exchange) throws Exception {
        System.out.println("****无串码销售开始****");
        Map body = exchange.getIn().getBody(Map.class);
        LanUtils lan = new LanUtils();
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_trade_id", 1).get(0);// 订单号
        exchange.getIn().setBody(body);
        msg.put("tradeId", tradeId);
        msg.put("occupiedFlag", "2");// 查询销售
        Exchange saleexchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[1], saleexchange);
        CallEngine.aopCall(saleexchange, "ecaop.comm.conf.url.esshttp.cbss");
        lan.xml2Json4ONS("ecaop.trades.sint.sale.template", saleexchange);
    }

    private void sinTerminalResale(Map msg, Exchange exchange) throws Exception {
        System.out.println("****裸机返销开始****");
        Map body = (Map) exchange.getIn().getBody();
        LanUtils lan = new LanUtils();
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_trade_id", 1).get(0);// 订单号
        exchange.getIn().setBody(body);
        msg.put("tradeId", tradeId);
        msg.put("subLogId", msg.get("subLogId"));
        msg.put("occupiedFlag", "3");// 返销
        Exchange resaleexchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[1], resaleexchange);
        CallEngine.aopCall(resaleexchange, "ecaop.comm.conf.url.esshttp.cbss");
        lan.xml2Json4ONS("ecaop.trades.sint.sale.template", resaleexchange);
    }

    /**
     * 查询终端信息
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map qrysinTermInfo(Exchange exchange, Map msg) throws Exception {
        LanUtils lan = new LanUtils();
        List<Map> paraList = new ArrayList<Map>();
        msg.put("occupiedFlag", "0");// 查询
        Exchange qryTermexchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[1], qryTermexchange);
        CallEngine.aopCall(qryTermexchange, "ecaop.comm.conf.url.esshttp.cbss");
        lan.xml2Json4ONS("ecaop.trades.sint.sale.template", qryTermexchange);
        Map resRsps = (Map) qryTermexchange.getOut().getBody();
        Map resRsp = (Map) resRsps.get("resourcesInfo");
        String[] paraIds = { "RESOURCES_BRAND_CODE", "RESOURCES_BRAND_NAME", "RESOURCES_MODEL_CODE",
                "RESOURCES_MODEL_NAME", "ITEM_BAR_CODE" };
        String[] paraValues = { String.valueOf(resRsp.get("resourcesBrandCode")),
                String.valueOf(resRsp.get("resourcesBrandName")),
                String.valueOf(resRsp.get("resourcesModelCode")), String.valueOf(resRsp.get("resourcesModelName")), "" };
        for (int i = 0; i < paraIds.length; i++) {
            Map para = new HashMap();
            para.put("paraId", paraIds[i]);
            para.put("paraValue", paraValues[i]);
            paraList.add(para);
        }
        msg.put("para", paraList);
        return msg;
    }

    /*
     * 订单来源转换
     */
    private Object transNetType(String sysCode) {
        String subsysCode = "";
        if (null == sysCode || "".equals(sysCode)) {
            subsysCode = "JTOTO";
        }
        else {
            Map sysCodes = MapUtils.asMap("00", "JTOTO", "01", "HTOTO", "02", "BDOTO", "03", "LSOTO", "04", "HWOTO",
                    "05", "XMOTO", "06", "WOSYS");
            subsysCode = (String) sysCodes.get(sysCode);
        }
        return subsysCode;
    }

    /**
     * 处理销售信息
     * 
     * @param msg
     * @return
     */
    private Map dealSaleInfo(Map msg) {
        List<Map> payInfo = (List<Map>) msg.get("payInfo");
        List<Map> payInfoList = new ArrayList<Map>();
        String origFee = (String) msg.get("origFee");
        String realFee = (String) msg.get("realFee");
        String reliefFee = (String) msg.get("reliefFee");
        origFee = "0".equals(origFee) ? origFee : TransFeeUtils.transFee(origFee, -1).toString();
        realFee = "0".equals(realFee) ? realFee : TransFeeUtils.transFee(realFee, -1).toString();
        reliefFee = "0".equals(reliefFee) ? reliefFee : TransFeeUtils.transFee(reliefFee, -1).toString();

        if (null != payInfo) {
            for (Map payInfos : payInfo) {
                Map payInfoMap = new HashMap();
                String lineSale = (String) payInfos.get("payFee");
                lineSale = "0".equals(lineSale) ? lineSale : TransFeeUtils.transFee(lineSale, -1).toString();
                payInfoMap.put("payType", payInfos.get("payType"));
                payInfoMap.put("lineSale", lineSale);
                payInfoMap.put("barCode", payInfos.get("barCode"));
                payInfoMap.put("mblNo", payInfos.get("mblNo"));
                payInfoMap.put("bonPwd", payInfos.get("bonPwd"));
                payInfoList.add(payInfoMap);
            }
        }
        // 处理主节点
        msg.put("oldMoney", origFee);
        msg.put("saleMoney", realFee);
        msg.put("saleNum", "1");
        msg.put("sumMoney", realFee);// 合计价格为支付金额
        msg.put("discountFee", reliefFee);
        msg.put("cost", realFee);
        msg.put("payInfo", payInfoList);
        msg.put("saleTime", getSaleTime());
        msg.put("saleStaffId", msg.get("operatorId"));
        msg.put("saleDepartId", msg.get("channelId"));
        return msg;

    }

    private Object getSaleTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        return format.format(new Date());
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
