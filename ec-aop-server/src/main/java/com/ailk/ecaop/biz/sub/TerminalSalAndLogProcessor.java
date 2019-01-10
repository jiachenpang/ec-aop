package com.ailk.ecaop.biz.sub;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.ResourceStateChangeUtils;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TransFeeUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;

/**
 * @author cuij
 * @version 创建时间：2016-11-9 上午9:26:15
 *          裸机的销售与返销，销售类型：销售4008，退机4009，返销4059，换机4058
 *          业务流程1.销售数据落地到CB 2.调用T4000002/T4000005进行终端的销售和返销
 *          卖场销售的裸机可以在cBSS开取发票，输出参数中TAX_INVOICENO描述修改为销售流水号，
 *          可以通过该字段到cBSS开发票。
 */
@EcRocTag("terminalSalAndLogProcessor")
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TerminalSalAndLogProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.param.sctr.ParametersMapping", "ecaop.gdjk.checkres.ParametersMapping",
            "ecaop.masb.chph.sale.ParametersMapping", "ecaop.trades.sccc.cancelTml.paramtersmapping",
            "ecaop.param.mapping.sjxs" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[6];

    ResourceStateChangeUtils rsc = new ResourceStateChangeUtils();

    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 根据methodCode判断是销售还是返销
        String methodCode = exchange.getMethodCode();
        msg.put("methodCode", methodCode);
        if (!EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province").contains(msg.get("province") + "")) {
            if ("sjxs".equals(methodCode)) {// 裸机销售
                terminalSale(msg, exchange);
            }
            if ("sctr".equals(methodCode)) {// 裸机返销
                terminalResale(msg, exchange);
            }
        } else {// 新流程
            if ("sjxs".equals(methodCode)) {// 裸机销售
                if (!"2".equals(msg.get("opeSysType"))) {// 调3GE的时候自己生成一个时间戳作为订单号
                    SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmssSSS");
                    String date = sdf.format(new Date());
                    msg.put("ordersId", date);
                }
                newTerminalSale(msg, exchange);
                if (!"2".equals(msg.get("opeSysType"))) {// 因为之前在配置文件中通过retailFlag字段判断是否是割接省份，所以无需再判断了
                    msg.put("isNewResCenter", "Y");// 直接放是割接省份标识
                    Exchange terminalExchangeEss = ExchangeUtils.ofCopy(exchange, msg);
                    lan.preData(pmp[5], terminalExchangeEss);
                    CallEngine.aopCall(terminalExchangeEss, "ecaop.comm.conf.url.esshttp.newsub");
                    lan.xml2Json4ONS("ecaop.template.3g.terminalCheck", terminalExchangeEss);
                    Map outMap = terminalExchangeEss.getOut().getBody(Map.class);
                    exchange.getOut().setBody(outMap);
                } else {// 裸机销售直接返回订单号
                    Map rspMap = new HashMap();
                    rspMap.put("bssOrderId", msg.get("ordersId"));
                    rspMap.put("essOrderId", msg.get("SUBSCRIBE_ID"));
                    exchange.getOut().setBody(rspMap);
                }

            }
            if ("sctr".equals(methodCode)) {// 裸机返销

                msg.put("isResale", "1");

                callTermSaleNew(msg, exchange);

                Map rspMap = new HashMap();
                rspMap.put("essOrderId", msg.get("SUBSCRIBE_ID"));
                exchange.getOut().setBody(rspMap);

                // newTerminalResale(msg, exchange);//换流程不要了
            }
        }
    }

    /**
     * 处理返回
     */
    public void dealReturn(Exchange exchange, Map msg) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        ArrayList<Map> paraList = (ArrayList<Map>) bodyMap.get("PARA");
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map paraMap : paraList) {
                paraMap.put("paraId", paraMap.get("PARA_ID"));
                paraMap.put("paraValue", paraMap.get("PARA_VALUE"));
                paraMap.remove("PARA_ID");
                paraMap.remove("PARA_VALUE");
            }
        }
        Map rspMap = new HashMap();
        rspMap.put("bssOrderId", msg.get("ordersId"));
        rspMap.put("essOrderId", msg.get("taxInvoiceno"));
        rspMap.put("para", paraList);
        exchange.getOut().setBody(rspMap);
    }

    /**
     * 
     * 裸机返销新流程---新零售
     */
    private void newTerminalResale(Map msg, Exchange exchange) throws Exception {

        // 查询终端信息
        List<Map> resourceInfo = new ArrayList<Map>();
        Map resourceInfoMap = new HashMap();
        resourceInfoMap.put("resourcesCode", msg.get("resourcesCode"));
        resourceInfo.add(resourceInfoMap);
        msg.put("resourcesInfo", resourceInfo);
        resourceInfoMap.put("occupiedFlag", "0");
        rsc.getResourceInfo(exchange);// 资源验证、查询
        // 校验信息
        Map respMap = exchange.getOut().getBody(Map.class);
        ArrayList<Map> res = (ArrayList<Map>) respMap.get("resourcesRsp");
        msg.putAll(res.get(0));
        msg.put("terminalId", msg.get("resourcesCode"));
        msg.put("resourceSrcCode", "1");
        Map retMap = new HashMap();
        callCbssOrder(exchange, msg);// CBSS订单记录接口
        retMap.put("essOrderId", msg.get("taxInvoiceno"));

        // 返销
        Map terminalMap = new HashMap();
        MapUtils.arrayPut(terminalMap, msg, MagicNumber.COPYARRAY);
        terminalMap.put("imei", msg.get("resourcesCode"));
        terminalMap.put("ordersId", msg.get("ordersId"));// 外围系统订单ID
        terminalMap.put("essOrigOrderId", msg.get("oldOrdersId"));// 原订单
        Exchange terminalExchange = ExchangeUtils.ofCopy(exchange, terminalMap);
        rsc.resCancelSalePre(terminalExchange);
        Exchange terminalExchange2 = ExchangeUtils.ofCopy(exchange, terminalMap);
        rsc.resCancelSale(terminalExchange2);
        Map outMap = (Map) JSON.parse(terminalExchange2.getOut().getBody().toString());
        Map rspMap = (Map) outMap.get("UNI_BSS_BODY");
        List<Map> paraList = (List<Map>) rspMap.get("PARA");
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map paraMap : paraList) {
                paraMap.put("paraId", paraMap.get("PARA_ID"));
                paraMap.put("paraValue", paraMap.get("PARA_VALUE"));
                paraMap.remove("PARA_ID");
                paraMap.remove("PARA_VALUE");
            }
        }
        retMap.put("para", paraList);
        exchange.getOut().setBody(retMap);
    }

    /**
     * 
     * 裸机销售新流程---新零售
     */
    private void newTerminalSale(Map msg, Exchange exchange) throws Exception {
        // 已割接省份裸机销售CB流程只调用能力平台接口termSaleNew --by yangzg 20181027
        if ("2".equals(msg.get("opeSysType"))) {
            callTermSaleNew(msg, exchange);
        } else {
            List<Map> resourceInfo = new ArrayList<Map>();
            Map resourceInfoMap = new HashMap();
            resourceInfoMap.put("resourcesCode", msg.get("resourcesCode"));
            resourceInfo.add(resourceInfoMap);
            msg.put("resourcesInfo", resourceInfo);
            resourceInfoMap.put("occupiedFlag", "1");
            // 1、 查询并处理终端信息
            Exchange infoexchange = ExchangeUtils.ofCopy(exchange, msg);
            rsc.getResourceInfo(infoexchange);// 资源验证、查询
            // 2、 校验信息
            Map retMap = infoexchange.getOut().getBody(Map.class);
            ArrayList<Map> res = (ArrayList<Map>) retMap.get("resourcesRsp");
            dealResReturn(res, msg);
            // if ("2".equals(msg.get("opeSysType"))) { // CBSS订单记录接口
            // callCbssOrder(exchange, msg);
            // }
            // 终端信息实占
            rsc.realOccupiedResourceInfo(exchange);
        }
    }


    /**
     * 裸机销售/返销-调微服务
     * 
     * @param msg
     * @param exchange
     */
    private void callTermSaleNew(Map msg, Exchange exchange) {
        Map result = new HashMap();
        String code = "";
        String detail = "";
        String netType = (String) msg.get("netType");
        String subsysCode = (String) transNetType(netType);

        String orderId = (String) GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_trade_id", 1).get(0);// 订单号和线上订单号
        try {
            List<Map> feeInfo = (List<Map>) msg.get("feeInfo");
            String isResale = (String) msg.get("isResale");
            String operType = "1".equals(isResale) ? "02" : "00";
            Map fee = new HashMap();
            if (!IsEmptyUtils.isEmpty(feeInfo)) {
                fee = feeInfo.get(0);
            }
            Map preDataMap = NumFaceHeadHelper.creatHead();
            Map terminalSalReq = new HashMap();
            terminalSalReq.put("OPERATOR_ID", msg.get("operatorId"));
            terminalSalReq.put("PROVINCE", msg.get("province"));
            terminalSalReq.put("CITY", msg.get("city"));
            terminalSalReq.put("CHANNEL_ID", msg.get("channelId"));
            terminalSalReq.put("CHANNEL_TYPE", msg.get("channelType"));
            terminalSalReq.put("ACCESS_TYPE", "01");
            terminalSalReq.put("SUBSCRIBE_ID", orderId);// 总部订单号（和资源验证/查询接口保持一致）
            terminalSalReq.put("OLD_SUBSCRIBE_ID", msg.get("oldOrdersId"));// 原有总部订单号（业务类型03：换机时用）
            terminalSalReq.put("SERIAL_NUMBER", msg.get("serialNumber"));// 手机号码,如果是合约计划销售中，必须要填写
            // terminalSalReq.put("TRADE_TYPE", "");//退换机业务类型
            // terminalSalReq.put("AGENT_CHANNEL_ID", msg.get("agentChannelId"));// 代理商渠道编码
            // terminalSalReq.put("RESOURCES_TYPE", msg.get("resourcesType"));//资源类型
            // terminalSalReq.put("PACKAGE_TYPE", msg.get("packageType"));//请求标准包类型
            terminalSalReq.put("OPER_TYPE", operType);// 业务类型 00: 顺价销售 01：合约销售 02：退机 03：换机 04：无线上网卡社会渠道批销 05：4G终端顺价销售
            // terminalSalReq.put("SALE_CHANNEL_ID", msg.get("saleChannelId"));//社会渠道编码，业务类型为04时必传
            // terminalSalReq.put("RESOURCES_NUMBER", msg.get("resourcesNumber"));//无线上网卡号码
            terminalSalReq.put("RESOURCES_CODE", msg.get("resourcesCode"));// 资源串号（终端IMEI或ICCID）
            // terminalSalReq.put("OLD_RESOURCES_CODE", msg.get("oldResourcesCode"));//原有资源串号（终端IMEI或ICCID）业务类型03：换机时用
            // terminalSalReq.put("DEPOSIT_SALE_FEE", msg.get("depositSaleFee"));//预存话费实收金额(单位：厘)
            terminalSalReq.put("TERMINAL_SALE_FEE", fee.get("realFee"));// 终端实收金额(单位：厘)
            // terminalSalReq.put("USIM_SALE_FEE", fee.get("realFee"));//USIM卡费实收金额(单位：厘)
            terminalSalReq.put("REDUCE_REASON", fee.get("reliefResult"));// 减免原因
            // terminalSalReq.put("INVOICE_NO", msg.get("invoiceNo"));//发票号码
            // terminalSalReq.put("ACTIVE_TYPE", msg.get("activeType"));//01预存话费送手机 02 购手机送话费 04 订业务送手机
            terminalSalReq.put("DRECOMMEND_ID", msg.get("recomPersonId"));// 发展人编码
            terminalSalReq.put("RECOMMEND_NAME", msg.get("recomPersonName"));
            terminalSalReq.put("RECOM_DEPART_ID", msg.get("recomDepartId"));
            // terminalSalReq.put("RECOMMEND_NUMBER", msg.get("recommendNumber"));//发展人电话
            terminalSalReq.put("SALE_PRICES", msg.get("salePrices"));// 销售价格（单位：厘）
            terminalSalReq.put("COST", msg.get("cost"));// 成本价格（单位：厘）
            terminalSalReq.put("MACHINE_TYPE_CODE", msg.get("machineTypeCode"));// 终端机型编码
            terminalSalReq.put("SUBSYS_CODE", StringUtils.isEmpty((String) msg.get("subsysCode")) ? subsysCode : msg.get("subsysCode"));// 系统标识
            terminalSalReq.put("TERMINAL_SUB_TYPE", msg.get("terminalSubType"));// 资源验证时返回
            terminalSalReq.put("TERMINAL_TYPE", msg.get("terminalType"));
            terminalSalReq.put("DISCOUNT_PRICES", msg.get("discountPrices"));// 折扣价格（单位：厘）
            terminalSalReq.put("DB_FLAG", msg.get("dbFlag"));// CB落数据传1,不落数据传0
            terminalSalReq.put("ACTIVITY_ID", msg.get("actPlandId"));// 活动编码
            terminalSalReq.put("PRODUCT_ID", msg.get("productId"));// 产品编码

            Map bodyMap = new HashMap();
            bodyMap.put("NEW_TERM_SALE_REQ", terminalSalReq);
            preDataMap.put("UNI_BSS_BODY", bodyMap);

            Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
            CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.resources.newtermsale");
            result = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
            Map UNI_BSS_HEAD = (Map) result.get("UNI_BSS_HEAD");
            Map UNI_BSS_BODY = (Map) result.get("UNI_BSS_BODY");
            Map rsp = (Map) UNI_BSS_BODY.get("NEW_TERM_SALE_RSP");
            if (IsEmptyUtils.isEmpty(rsp)) {
                String RESP_CODE = (String) UNI_BSS_HEAD.get("RESP_CODE");
                String RESP_DESC = (String) UNI_BSS_HEAD.get("RESP_DESC");
                throw new EcAopServerBizException(RESP_CODE, "调能力平台接口报错：" + RESP_DESC);
            }

            code = (String) rsp.get("RSP_CODE");
            detail = (String) rsp.get("RSP_DESC");
            if (!"0000".equals(code)) {
                throw new EcAopServerBizException(code, detail);
            }
        } catch (EcAopServerSysException e) {
            throw new EcAopServerSysException(e.getCode(), e.getMessage());
        } catch (EcAopServerBizException e) {
            throw new EcAopServerBizException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            throw new EcAopServerSysException("9999", "调能力平台接口报错：" + e.getMessage());
        }
        msg.put("SUBSCRIBE_ID", orderId);
        msg.put("code", code);
        msg.put("detail", detail);
    }



    private void terminalSale(Map msg, Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        // 1.查询并处理终端信息
        msg = dealTmlInfo(msg, exchange);
        // 2.准备基本参数
        String subLogId = (String) msg.get("ordersId");// 原订单号,退机、返销、换机必填。
        String orderId = (String) msg.get("tradeId");//(String) GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_trade_id", 1).get(0);// 订单号和线上订单号
        exchange.getIn().setBody(body);
        Object eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String provinceCode = (String) msg.get("province");
        String netType = (String) msg.get("netType");
        msg.put("subsysCode", transNetType(netType));
        msg.put("resTradeId", "4008");
        msg.put("subLogId", subLogId);
        msg.put("otoOrderId", orderId);
        msg.put("tradeId", orderId);
        msg.put("eparchyCode", eparchyCode);
        msg.put("provinceCode", provinceCode);
        msg.put("cityCode", msg.get("district"));
        msg.put("orderSource", "OTO" + provinceCode);

        // 3.准备销售费用信息及支付信息
        msg = dealSaleInfo(msg);

        // 4.调用cbss接口记录订单。
        Exchange ToCBexchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[1], ToCBexchange);
        CallEngine.wsCall(ToCBexchange, "ecaop.comm.conf.url.cbss.services.TerminalSaleAopSer");
        lan.xml2Json("ecaop.param.sctr.template", ToCBexchange);
        // 5.将cb结果放入exchange等待终端销售成功后处理
        Map rsp4CB = (Map) ToCBexchange.getOut().getBody();
        exchange.getOut().setBody(rsp4CB);
        // 6.对终端进行销售/返销
        dealTerminal(exchange, msg);
        // 7.销售成功入库
        EssBusinessDao essDao = new EssBusinessDao();
        essDao.insertTerSaleRecord(msg);
        // 8.处理cbss返回
        dealReturnParams(exchange, subLogId);

    }

    private void terminalResale(Map msg, Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        EssBusinessDao essDao = new EssBusinessDao();
        // 1.查库取出销售数据
        List<Map> saleRecords = new ArrayList<Map>();
        Map inMap = new HashMap();
        inMap.put("tradeId", msg.get("oldOrdersId"));
        saleRecords = essDao.qryTerSaleRecord(inMap);
        if (null != saleRecords && saleRecords.size() > 0) {
            for (Map saleRecord : saleRecords) {
                String terminalType = (String) saleRecord.get("TERMINAL_TYPE");
                // 当终端类别为智能终端时，该字段必填，终端子类别编码：入门级：04普及型：03中高端：02明星：01
                if ("PP".equals(terminalType)) {
                    msg.put("terminalSubType", saleRecord.get("TERMINAL_SUB_TYPE"));
                }
                msg.put("machineTypeCode", saleRecord.get("MACHINE_TYPE_CODE"));
                msg.put("terminalId", saleRecord.get("TERMINAL_ID"));
                msg.put("resourceSrcCode", "1");
                msg.put("terminalType", terminalType);
                msg.put("serialNumber", saleRecord.get("SERIAL_NUMBER"));
            }
        }
        else {
            throw new EcAopServerBizException("获取销售数据失败！");
        }
        // 2.准备基本参数
        String subLogId = (String) msg.get("ordersId");// 原订单号,退机、返销、换机必填。
        String orderId = (String) GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_trade_id", 1).get(0);// 订单号和线上订单号
        exchange.getIn().setBody(body);
        Object eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String provinceCode = (String) msg.get("province");
        String netType = (String) msg.get("netType");
        msg.put("subsysCode", transNetType(netType));
        msg.put("resTradeId", "4059");
        msg.put("subLogId", msg.get("oldOrdersId"));
        msg.put("otoOrderId", subLogId);
        msg.put("tradeId", orderId);
        msg.put("eparchyCode", eparchyCode);
        msg.put("provinceCode", provinceCode);
        msg.put("cityCode", msg.get("district"));
        msg.put("orderSource", "OTO" + provinceCode);

        // 3.处理销售信息
        msg = dealSaleInfo(msg);
        // 4.调用cbss接口记录订单。
        Exchange ToCBexchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[1], ToCBexchange);
        CallEngine.wsCall(ToCBexchange, "ecaop.comm.conf.url.cbss.services.TerminalSaleAopSer");
        lan.xml2Json("ecaop.param.sctr.template", ToCBexchange);
        // 5.将cb结果放入exchange等待终端销售成功后处理
        Map rsp4CB = (Map) ToCBexchange.getOut().getBody();
        exchange.getOut().setBody(rsp4CB);
        // 6.对终端进行销售/返销
        dealTerminal(exchange, msg);
        // 7.处理cbss返回
        dealResaleReturnParams(exchange, ToCBexchange, subLogId);

    }

    /*
     * 订单来源转换
     */
    private Object transNetType(String netType) {
        String subsysCode = "";
        if (null == netType || "".equals(netType)) {
            subsysCode = "JTOTO";
        }
        else {
            Map netTypes = MapUtils.asMap("00", "JTOTO", "01", "HTOTO", "02", "BDOTO", "03", "LSOTO", "04", "HWOTO",
                    "05", "XMOTO", "06", "BJMC", "07", "MARKINGAPP");//RLN2017062200058裸机销售订单来源新增07类型
            subsysCode = (String) netTypes.get(netType);
        }
        return subsysCode;
    }

    private void dealTerminal(Exchange exchange, Map msg) throws Exception {
        // 判断销售/返销
        if ("4008".equals(msg.get("resTradeId"))) {// 销售
            msg.put("operType", "05");// 4G顺价销售
            Exchange saleTmlexchange = ExchangeUtils.ofCopy(exchange, msg);
            // 对终端进行实占
            lan.preData(pmp[3], saleTmlexchange);
            CallEngine.aopCall(saleTmlexchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.chph.sale.template", saleTmlexchange);
        }
        if ("4059".equals(msg.get("resTradeId"))) {// 返销
            // 准备返销需要的参数
            String orderId = (String) GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_order_id", 1).get(0);
            String essOrigOrderId = (String) msg.get("ordersId");
            msg.put("ordersId", orderId);// 随机生成
            msg.put("essOrigOrderId", essOrigOrderId);// 传原订单号
            msg.put("imei", msg.get("resourcesCode"));
            Exchange saleTmlexchange = ExchangeUtils.ofCopy(exchange, msg);
            // 对终端进行返销
            lan.preData(pmp[4], saleTmlexchange);
            CallEngine.aopCall(saleTmlexchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.sccc.cancelTml.template", saleTmlexchange);
        }
    }

    /**
     * 裸机返销专用
     * 
     * @param exchange
     * @param ToCBexchange
     * @param ordersId
     */
    private void dealResaleReturnParams(Exchange exchange, Exchange ToCBexchange, String ordersId) {
        Map rspMap = (Map) ToCBexchange.getOut().getBody();
        Map retMap = new HashMap();
        String taxInvoiceno = "";
        List<Map> respInfo = (List<Map>) rspMap.get("respInfo");
        if (null != respInfo) {
            for (Map respInfos : respInfo) {
                taxInvoiceno = (String) respInfos.get("taxInvoiceno");
            }
        }
        retMap.put("bssOrderId", ordersId);
        retMap.put("essOrderId", taxInvoiceno);
        exchange.getOut().setBody(retMap);
    }

    private void dealReturnParams(Exchange exchange, String ordersId) {
        Map rspMap = (Map) exchange.getOut().getBody();
        Map retMap = new HashMap();
        String taxInvoiceno = "";
        List<Map> respInfo = (List<Map>) rspMap.get("respInfo");
        if (null != respInfo) {
            for (Map respInfos : respInfo) {
                taxInvoiceno = (String) respInfos.get("taxInvoiceno");
            }
        }
        retMap.put("bssOrderId", ordersId);
        retMap.put("essOrderId", taxInvoiceno);
        exchange.getOut().setBody(retMap);
    }

    private Map dealTmlInfo(Map msg, Exchange exchange) throws Exception {
        String resourcesCode = (String) msg.get("resourcesCode");
        // 准备查询参数
        ArrayList<Map> preresourcesInfo = new ArrayList<Map>();
        Map qryTml = new HashMap();
        qryTml.put("resourcesType", "01");
        qryTml.put("resourcesCode", resourcesCode);
        qryTml.put("occupiedFlag", "0");
        preresourcesInfo.add(qryTml);
        msg.put("resourcesInfo", preresourcesInfo);
        Exchange Tmlexchange = ExchangeUtils.ofCopy(exchange, msg);
        // 查询终端信息
        lan.preData(pmp[2], Tmlexchange);
        CallEngine.aopCall(Tmlexchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4Res("ecaop.gdjk.checkres.template", Tmlexchange);
        // 处理返回并准备裸机销售
        Map queryMap = (Map) Tmlexchange.getOut().getBody();
        List<Map> resourcesInfo = (List<Map>) queryMap.get("resourcesRsp");
        if (null != resourcesInfo) {
            for (Map resourcesInfos : resourcesInfo) {
                String terminalType = (String) resourcesInfos.get("terminalType");
                // 当终端类别为智能终端时，该字段必填，终端子类别编码：入门级：04普及型：03中高端：02明星：01
                if ("PP".equals(terminalType)) {
                    msg.put("terminalSubType", resourcesInfos.get("termialTSubType"));
                }
                msg.put("machineTypeCode", resourcesInfos.get("machineTypeCode"));
                msg.put("terminalId", resourcesInfos.get("resourcesCode"));
                msg.put("resourceSrcCode", "1");
                msg.put("terminalType", terminalType);
                msg.put("serialNumber", resourcesInfos.get("serviceNumber"));

            }
        }
        return msg;
    }

    private Map dealSaleInfo(Map msg) {
        List<Map> feeInfo = (List<Map>) msg.get("feeInfo");
        List<Map> payInfo = (List<Map>) msg.get("payInfo");
        List<Map> payInfoList = new ArrayList<Map>();
        String origFee = "";
        String realFee = "";
        String reliefFee = "";
        String payFee = "";
        String payType = "";
        if (null != feeInfo) {
            for (Map feeInfos : feeInfo) {
                // 终端销售指导价格（单位分）已由厘转分
                if ("0".equals(feeInfos.get("origFee"))) {
                    origFee = (String) feeInfos.get("origFee");
                }
                else {
                    origFee = TransFeeUtils.transFee(feeInfos.get("origFee"), -1).toString();
                }
                // 终端线上销售实际价格（单位分）
                if ("0".equals(feeInfos.get("realFee"))) {
                    realFee = (String) feeInfos.get("realFee");
                }
                else {
                    realFee = TransFeeUtils.transFee(feeInfos.get("realFee"), -1).toString();
                }
                // 折扣（单位分）
                if ("0".equals(feeInfos.get("reliefFee"))) {
                    reliefFee = (String) feeInfos.get("reliefFee");
                }
                else {
                    reliefFee = TransFeeUtils.transFee(feeInfos.get("reliefFee"), -1).toString();
                }

            }
        }
        if (null != payInfo) {
            for (Map payInfos : payInfo) {
                Map payInfoMap = new HashMap();
                if ("0".equals(payInfos.get("payFee"))) {
                    payFee = (String) payInfos.get("payFee");
                }
                else {
                    payFee = TransFeeUtils.transFee(payInfos.get("payFee"), -1).toString();
                }
                payType = (String) payInfos.get("payType");
                if ("02".equals(payType)) {
                    payInfoMap.put("payType", "XSZF");
                }
                else if ("10".equals(payType) || "14".equals(payType)) {
                    payInfoMap.put("payType", "CASH");
                }
                else if ("11".equals(payType) || "12".equals(payType)) {
                    payInfoMap.put("payType", "CHCK");
                }
                else if ("13".equals(payType) || "15".equals(payType)) {
                    payInfoMap.put("payType", "NPOS");
                }
                else {
                    payInfoMap.put("payType", payType);
                }
                payInfoMap.put("lineSale", payFee);
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

    /**
     * 调用CBSS订单记录接口
     */
    private void callCbssOrder(Exchange exchange, Map msg) throws Exception {
        String subLogId = (String) msg.get("ordersId");// 原订单号,退机、返销、换机必填。
        Exchange tradeExchange = ExchangeUtils.ofCopy(exchange, msg);
        String orderId = (String) GetSeqUtil.getSeqFromCb(pmp[0], tradeExchange, "seq_trade_id", 1).get(0);// 订单号和线上订单号
        Object eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String provinceCode = (String) msg.get("province");
        String netType = (String) msg.get("netType");
        msg.put("subsysCode", transNetType(netType));
        msg.put("resTradeId", "4008");
        msg.put("subLogId", subLogId);
        msg.put("otoOrderId", orderId);
        msg.put("tradeId", orderId);
        msg.put("eparchyCode", eparchyCode);
        msg.put("provinceCode", provinceCode);
        msg.put("cityCode", msg.get("district"));
        msg.put("orderSource", "OTO" + provinceCode);
        if ("sctr".equals(msg.get("methodCode"))) {
            msg.put("resTradeId", "4059");
            msg.put("subLogId", msg.get("oldOrdersId"));
            msg.put("otoOrderId", subLogId);
        }
        msg = dealSaleInfo(msg);
        Exchange ToCBexchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[1], ToCBexchange);
        CallEngine.wsCall(ToCBexchange, "ecaop.comm.conf.url.cbss.services.TerminalSaleAopSer");
        lan.xml2Json("ecaop.param.sctr.template", ToCBexchange);
        msg.put("taxInvoiceno", orderId);
    }


    private void dealResReturn(ArrayList<Map> res, Map msg) {
        if (!IsEmptyUtils.isEmpty(res)) {
            for (Map resMap : res) {
                String rscStateCode = resMap.get("rscStateCode").toString();
                if (!"0000".equals(rscStateCode)) {
                    throw new EcAopServerBizException(rscStateCode, resMap.get("rscStateDesc").toString());
                }
                String terminalType = (String) resMap.get("terminalType");
                // 当终端类别为智能终端时，该字段必填，终端子类别编码：入门级：04普及型：03中高端：02明星：01
                if ("PP".equals(terminalType)) {
                    msg.put("terminalSubType", resMap.get("termialTSubType"));
                }
                msg.put("machineTypeCode", resMap.get("machineTypeCode"));
                msg.put("terminalId", resMap.get("resourcesCode"));
                msg.put("resourceSrcCode", "1");
                msg.put("terminalType", terminalType);
                msg.put("serialNumber", resMap.get("serviceNumber"));
                msg.put("resourcesInfo", res);
            }
        }

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
