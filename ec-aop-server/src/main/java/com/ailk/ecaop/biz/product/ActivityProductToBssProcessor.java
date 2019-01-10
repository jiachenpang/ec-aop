package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.ailk.ecaop.dao.essbusiness.EssProvinceDao;

/**
 * bss套餐变更及合约调用全业务接口
 * Date 2018-6-1
 * @author zhaok
 *
 */

@EcRocTag("activityProductToBssProcessor")
public class ActivityProductToBssProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = {"ecaop.ecsb.mags.ParametersMapping"};

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        List<Map> userInfos = (List<Map>) msg.get("userInfo");
        Map userInfo = userInfos.get(0);

        List<Map> subproductInfo = new ArrayList<Map>();
        List<Map> productInfoList = (List<Map>) msg.get("productInfo");
        String priproductId = "";
        String tarproductId = "";
        ELKAopLogger.logStr("传入的产品节点：" + productInfoList);
        if (!IsEmptyUtils.isEmpty(productInfoList)) {
            for (Map productInfo : productInfoList) {
                String productMode = (String) productInfo.get("productMode");
                String optType = (String) productInfo.get("optType");
                String productId = (String) productInfo.get("productId");
                if ("1".equals(productMode) && "00".equals(optType)) {
                    tarproductId = productId; // 订购主产品
                    List<Map> packageInfos = (List<Map>) productInfo.get("packageInfo"); // 订购主产品附加信息
                    if (!IsEmptyUtils.isEmpty(packageInfos)) {
                        for (Map packageInfo : packageInfos) {
                            List<Map> elementInfos = (List<Map>) packageInfo.get("elementInfo");
                            Map productitem = new HashMap();
                            List<Map> feeInfo = new ArrayList<Map>();
                            if (!IsEmptyUtils.isEmpty(elementInfos)) {
                                for (Map elementInfo : elementInfos) {
                                    Map item = new HashMap();
                                    item.put("feeCode", elementInfo.get("elementCode"));
                                    item.put("feeName", elementInfo.get("elementName"));
                                    item.put("startDate", elementInfo.get("startDate"));
                                    item.put("endDate", elementInfo.get("endDate"));
                                    feeInfo.add(item);
                                }
                            }
                            productitem.put("feeInfo", feeInfo);
                            productitem.put("packageId", packageInfo.get("packageId"));
                            productitem.put("packageName", packageInfo.get("packageName"));
                            productitem.put("packageType", packageInfo.get("packageType"));
                            productitem.put("startDate", packageInfo.get("startDate"));
                            productitem.put("endDate", packageInfo.get("endDate"));
                            subproductInfo.add(productitem);
                        }
                    }
                    ELKAopLogger.logStr("传入的订购附加信息节点：" + packageInfos);
                }
                else if ("1".equals(productMode) && "01".equals(optType)) {
                    priproductId = productId; // 退订主产品
                }
            }
        }
        if (IsEmptyUtils.isEmpty(tarproductId)) {
            throw new EcAopServerBizException("9999", "退订产品ID不能为空!");
        }
        if (IsEmptyUtils.isEmpty(priproductId)) {
            throw new EcAopServerBizException("9999", "订购产品ID不能为空!");
        }

        msg.put("subproductInfo", subproductInfo);
        msg.put("priproductId", priproductId);
        msg.put("tarproductId", tarproductId);
        msg.put("orderId", msg.get("ordersId"));
        msg.put("endsystemId", "0" + msg.get("opeSysType"));
        msg.put("transresourcesType", userInfo.get("transresourcesType"));

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用全业务接口
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.DepositGiveFeeSer");
        lan.xml2Json("ecaop.trades.mags.template", exchange);
        // 处理返回
        dealReturn(exchange, msg);
    }

    private void dealReturn(Exchange exchange, Map msg) {
        Map out = exchange.getOut().getBody(Map.class);
        ELKAopLogger.logStr("返回的参数：" + out);
        if (IsEmptyUtils.isEmpty(out)) {
            throw new EcAopServerBizException("9999", "预提交返回信息为空");
        }
        List<Map> retFeeList = new ArrayList<Map>();
        Map realOut = new HashMap();
        List<Map> feeInfo = (List<Map>) out.get("feeInfo");
        if (!IsEmptyUtils.isEmpty(feeInfo)) {
            for (Map fee : feeInfo) {
                Map retFee = new HashMap();
                retFee.put("feeId", fee.get("feeId"));
                retFee.put("feeCategory", fee.get("feeType"));
                retFee.put("feeDes", fee.get("feeName"));
                retFee.put("maxRelief", fee.get("maxRelief"));
                retFee.put("origFee", fee.get("oldFee"));
                retFeeList.add(retFee);
            }
            realOut.put("feeInfo", retFeeList);
        }
        realOut.put("startTime", out.get("startTime"));
        realOut.put("endTime", out.get("endTime"));
        realOut.put("totalFee", out.get("totalFee"));
        realOut.put("bssOrderId", out.get("provinceOrderId"));
        realOut.put("provOrderId", msg.get("ordersId"));

        // 订单入库
        try {
            EssProvinceDao dao = new EssProvinceDao();
            Map item = new HashMap();

            item.put("province", msg.get("province"));
            item.put("subscribeId", msg.get("ordersId"));
            item.put("sysCode", "01");
            item.put("serialNumber", msg.get("serialNumber"));
            item.put("terminalId", "");
            item.put("methodCode", exchange.getMethodCode());
            item.put("proKeyMode", "");
            item.put("proKey", "");
            item.put("activityId", "");
            item.put("discountFlag", "");
            /*
            INSERT INTO TL_B_TRADE_RESOURCE
            (PROVINCE_CODE,   SUBSCIBE_ID,   SYSCODE,   SERIAL_NUMBER,   TERMINAL_ID,
            MEEHOD_CODE,   OPER_DATE,   PRO_KEY_MODE,   PRO_KEY   ,ACTIVITY_ID   ,DISCOUNT_FLAG)
            VALUES
            (#province#,   #subscribeId#,   #sysCode#,   #serialNumber#,   #terminalId#,
            #methodCode#,   SYSDATE,   #proKeyMode#,   #proKey#,   #activityId#,   #discountFlag#)
            */
            ELKAopLogger.logStr("入库参数：" + item);
            new EssBusinessDao().insertResourceInfo(item);
            ELKAopLogger.logStr("订单入库成功：" + item.get("subscribeId"));

        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交返回订单入库错误！");
        }
        exchange.getOut().setBody(realOut);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
