package com.ailk.ecaop.biz.brd;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.sub.PreDataProcessor;
import com.ailk.ecaop.biz.sub.lan.YearPayEcs4CBSSSub;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;

@EcRocTag("yearPaySubCB")
public class YearPaySubCBProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.smcom.preSub.ParametersMapping",
            "ecaop.trades.sccc.cancel.paramtersmapping" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    private final String ERROR_CODE = "8888";
    private final LanUtils lan = new LanUtils();

    public void process(Exchange exchange) throws Exception {
        boolean flag = true;
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map preSubmitReturnMap = new HashMap();
        Map threePartInfo = callThreePart(exchange, msg);
        //通过relationTypeCode判断是不是融合号码（88或者89开头的）
        if (!IsEmptyUtils.isEmpty(msg.get("relationTypeCode"))) {
            //按规范不传或者是1为续费流程
            if ("1".equals(msg.get("changeTag")) || IsEmptyUtils.isEmpty(msg.get("changeTag"))) {
                flag = false;
                // 虚拟用户预提交
                Map mixMap = preMixNumberInfo(exchange, msg, flag);
                // 成员用户预提交
                Map phoneMap = prePhoneNumberInfo(exchange, msg, threePartInfo, flag);
                //正式提交
                orderSub(mixMap, phoneMap, exchange, msg);
            }
            //2为变更流程
            else if ("2".equals(msg.get("changeTag"))) {
                // 虚拟用户预提交
                Map mixMap = preMixNumberInfo(exchange, msg, flag);
                // 成员用户预提交
                Map phoneMap = prePhoneNumberInfo(exchange, msg, threePartInfo, flag);
                //正式提交
                orderChgSub(mixMap, phoneMap, exchange, msg);
            }
            else if ("3".equals(msg.get("changeTag"))) {
                throw new EcAopServerBizException(ERROR_CODE, "不支持非主产品变更,请核实");
            }
            else {
                throw new EcAopServerBizException(ERROR_CODE, "产品变更方式[" + msg.get("changeTag")
                        + "]不在[1.趸交;2.变更产品;3.变更非主产品");
            }
        }
        else {
            YearPayEcs4CBSSSub sub = new YearPayEcs4CBSSSub();
            preSubmitReturnMap = sub.yearPayEcsCheck(exchange, msg);
            Message out = new DefaultMessage();
            Map outMap = new HashMap();
            // 宽带趸交流程，沿用现有流程
            if ("0".equals(preSubmitReturnMap.get("isNoChangeProduct"))) {
                preSubmitReturnMap.remove("isNoChangeProduct");
                outMap = preSubmitReturnMap;
            }
            else {
                outMap = sub.yearPayEcsSub(exchange, preSubmitReturnMap, msg);
            }
            out.setBody(outMap);
            exchange.setOut(out);
        }
    }

    /**
     * 调用三户接口,获取预提交的必要信息
     *
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map callThreePart(Exchange exchange, Map msg) throws Exception {
        Map threePartMap = MapUtils.asMap("getMode", "111001101010001101", "serialNumber", msg.get("serialNumber"),
                "tradeTypeCode", "9999", "serviceClassCode", "0040");
        Map para = MapUtils.asMap("paraId", "OPERATE_FLAG", "paraValue", "AOP_KD_DISCNT");
        List<Map> paraList = new ArrayList<Map>();
        paraList.add(para);
        threePartMap.putAll(MapUtils.asMap("para", paraList));
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map retMap = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfoList = (List<Map>) retMap.get("custInfo");
        List<Map> userInfoList = (List<Map>) retMap.get("userInfo");
        List<Map> acctInfoList = (List<Map>) retMap.get("acctInfo");
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        Object bDiscntCode = "";// 资费编码
        List<Map> uuInfo = (List<Map>) userInfo.get("uuInfo");
        List<Map> uattrInfo = (List<Map>) userInfo.get("attrInfo");
        if (!IsEmptyUtils.isEmpty(uattrInfo) && "1".equals(msg.get("changeTag"))) {
            for (Map attr : uattrInfo) {
                if ("SPEED".equals(attr.get("attrCode"))) {
                    msg.put("speedLevel", attr.get("attrValue"));
                }
            }
        }
        String relationTypeCode = "";
        if (!IsEmptyUtils.isEmpty(uuInfo)) {
            for (Map temMap : uuInfo) {
                String endDate = (String) temMap.get("endDate");
                relationTypeCode = (String) temMap.get("relationTypeCode");
                if (relationTypeCode.startsWith("89") || relationTypeCode.startsWith("88")
                        && 0 < endDate.compareTo(GetDateUtils.getNextMonthFirstDayFormat())) {
                    msg.put("mixUserId", temMap.get("userIdA"));
                    msg.put("mixNumber", temMap.get("serialNumberA"));
                    msg.put("relationTypeCode", relationTypeCode);
                    msg.put("mixProduct", temMap.get("productIdA"));
                }
            }
        }
        Map arrearageFeeInfo = (Map) userInfo.get("arrearageFeeInfo");
        List<Map> arrearageMess = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(arrearageFeeInfo)) {
            if (Integer.valueOf(arrearageFeeInfo.get("depositMoney").toString()) < 0) {
                Map arrearageInfo = new HashMap();
                arrearageInfo.put("arrearageNumber", msg.get("serialNumber"));
                arrearageInfo.put("areaCode", msg.get("areaCode"));
                arrearageInfo.put("arrearageType", "0");
                arrearageMess.add(arrearageInfo);
            }
        }
        if (arrearageMess.size() > 0) {
            msg.put("arrearageMess", arrearageMess);
        }
        String cycle = "";
        String itemId = "";
        String itemId1 = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "seq_item_id", 1).get(0);
        Object prodCycle = "";// 当前主产品周期
        Object prodTariffCode = "";// 当前主产品资费编码
        Object prodTariffDescribe = "";// 当前主产品资费描述
        List<Map> paramList = (List<Map>) retMap.get("para");
        if (IsEmptyUtils.isEmpty(paramList)) {
            throw new EcAopServerBizException("9999", "CBSS未返回PARA信息");
        }
        for (Map param : paramList) {
            if ("PROD_CYCLE".equals(param.get("paraId"))) {
                prodCycle = param.get("paraValue");
            }
            else if ("PROD_TARIFF_CODE".equals(param.get("paraId"))) {
                prodTariffCode = param.get("paraValue");
            }
            else if ("PROD_TARIFF_DESCRIBE".equals(param.get("paraId"))) {
                prodTariffDescribe = param.get("paraValue");
            }
        }
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        for (Map discnt : discntInfo) {
            if (!prodTariffCode.equals(discnt.get("discntCode"))) {
                continue;
            }
            itemId = (String) discnt.get("itemId");
            bDiscntCode = discnt.get("discntCode");
            List<Map> attrInfo = (ArrayList<Map>) discnt.get("attrInfo");
            if (!IsEmptyUtils.isEmpty(attrInfo)) {
                for (Map attr : attrInfo) {
                    if ("cycle".equals(attr.get("attrCode"))) {
                        cycle = (String) attr.get("attrValue");
                    }
                }
            }
            prodCycle = cycle;
            String startDate = discnt.get("startDate").toString();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String yyEndDate = sdf.format(RDate.addMonths(sdf.parse(startDate), Integer.valueOf(prodCycle.toString())));
            String yyStartDate = yyEndDate.substring(0, 8) + "000000";
            yyEndDate = GetDateUtils.getNextDiscntStartDate(yyStartDate, Integer.valueOf(prodCycle.toString()));
            yyEndDate = DateUtils.addSeconds(yyEndDate, -1);
            msg.put("itemId", itemId);
            msg.put("bDiscntCode", bDiscntCode);
            msg.put("oldStartData", startDate);
            msg.put("packageId", discnt.get("packageId"));
            msg.put("itemId1", itemId1);
            //msg.put("discntName", prodTariffCode);
            msg.put("yyStartDate", yyEndDate);
            msg.put("yyEndDate", yyEndDate);
        }
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        String orderId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        //取出发展人信息
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("recomInfo"))) {
            List<Map> recomInfo = (List<Map>) msg.get("recomInfo");
            for (Map comInfo : recomInfo) {
                if (StringUtils.isNotEmpty((String) comInfo.get("recomPersonId"))) {
                    msg.put("recomPersonId", comInfo.get("recomPersonId"));// 发展人
                }
                if (StringUtils.isNotEmpty((String) comInfo.get("recomDepartId"))) {
                    msg.put("recomDepartId", comInfo.get("recomDepartId"));// 发展人渠道
                }
            }
        }
        msg.putAll(MapUtils.asMap("tradeId", tradeId, "orderId", orderId, "userId", userInfo.get("userId"), "custId",
                custInfo.get("custId"), "acctId", acctInfo.get("acctId"), "productName", userInfo.get("productName"),
                "itemId1", itemId1, "custName", custInfo.get("custName"), "contactPerson",
                custInfo.get("contactPerson"), "contactPhone", custInfo.get("contactPhone"), "brandCode",
                userInfo.get("brandCode"), "productTypeCode", userInfo.get("productTypeCode"),
                "productId", userInfo.get("productId"), "eparchyCode", eparchyCode, "method",
                exchange.getMethodCode(), "methodCode", exchange.getMethodCode(),
                "customerName", custInfo.get("customerName"), "startDate", GetDateUtils.getNextMonthFirstDayFormat()));
        return retMap;
    }

    private Map preMixNumberInfo(Exchange exchange, Map msg, Boolean flag) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map ext = new HashMap();
        ext.put("tradeUser", preDataForTradeUser(msg));
        ext.put("tradeItem", preDataForTradeItem(msg, false, flag));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg, false, flag));
        msg.put("ext", ext);
        Map base = preBaseData(msg, exchange.getAppCode(), true, flag);
        msg.put("base", base);
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("orderId"));
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.smcom.preSub.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    private Map prePhoneNumberInfo(Exchange exchange, Map msg, Map threePartInfo, Boolean flag) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map ext = new HashMap();
        if (flag) {
            // 宽带所需的资费和生效方式
            List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
            String broadDiscntId = (String) broadDiscntInfo.get(0).get("broadDiscntId");
            Map broadDiscntAttr = (Map) broadDiscntInfo.get(0).get("broadDiscntAttr");
            if (null != broadDiscntAttr && !broadDiscntAttr.isEmpty()) {
                msg.put("delayType", broadDiscntAttr.get("delayType"));
                msg.put("delayDiscntId", broadDiscntAttr.get("delayDiscntId"));
                msg.put("delayDiscntType", broadDiscntAttr.get("delayDiscntType"));
                msg.put("broadDiscntId", broadDiscntId);
            }
            List<Map> productInfoList = (List<Map>) msg.get("productInfo");
            List<Map> oldProductList = new ArrayList<Map>();
            // 获取订购的主产品和退订的主产品
            String newProductId = "";
            String oldProductId = "";
            String orderEndDate = "";
            for (Map productInfoMap : productInfoList) {
                if ("0".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                    newProductId = (String) productInfoMap.get("oldProductId");
                    orderEndDate = (String) productInfoMap.get("brandNumber");
                }
                if ("1".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                    oldProductId = (String) productInfoMap.get("oldProductId");
                }
            }
            for (int i = 0; i < productInfoList.size(); i++) {
                if ("0".equals(productInfoList.get(i).get("optType"))) {
                    productInfoList.get(i).put("firstMonBillMode", "02");// 给外围主产品默认02
                    // 订购的新产品
                    productInfoList.get(i).put("productId", productInfoList.get(i).get("oldProductId"));
                }
                else {
                    oldProductList.add(productInfoList.get(i));
                }
            }
            if (null != oldProductList || !oldProductList.isEmpty()) {
                productInfoList.removeAll(oldProductList);
            }
            Object oldProductStartDate = GetDateUtils.getDate();
            Object orserProductStartDate = GetDateUtils.getDate();
            List<Map> userInfo = (List<Map>) threePartInfo.get("userInfo");
            for (Map user : userInfo) {
                oldProductStartDate = user.get("openDate");
                orserProductStartDate = user.get("openDate");
                List<Map> productInfo = (List<Map>) user.get("productInfo");
                if (IsEmptyUtils.isEmpty(productInfo)) {
                    continue;
                }
                for (Map product : productInfo) {
                    if (oldProductId.equals(product.get("productId"))) {
                        oldProductStartDate = product.get("productActiveTime");
                        break;
                    }
                    if (newProductId.equals(product.get("productId"))) {
                        orserProductStartDate = product.get("productActiveTime");
                        break;
                    }
                }
            }
            String startDate4Order = DateUtils.getDate();
            startDate4Order = "0".equals(msg.get("delayDiscntId")) ? DateUtils.getDate() : DateUtils
                    .getNextMonthFirstDay();
            Map parm = MapUtils.asMap("oldProductStartDate", oldProductStartDate, "orserProductStartDate",
                    orserProductStartDate, "newProductId", newProductId, "oldProductId", oldProductId, "orderEndDate",
                    orderEndDate, "startDate4Order", startDate4Order);
            msg.putAll(parm);
            ext.putAll(TradeManagerUtils.newPreProductInfo(productInfoList, "00" + msg.get("province"), msg));
            // 宽带需要传入资费节点,重新塞入discnt节点,作为新增资费下发
            List<Map> discntList = new ArrayList<Map>();
            for (int j = 0; j < broadDiscntInfo.size(); j++) {
                Map disMap = new HashMap();
                msg.put("PROVINCE_CODE", "00" + msg.get("province"));
                msg.put("ELEMENT_ID", broadDiscntId);
                msg.put("PRODUCT_ID", msg.get("newProductId"));
                disMap = TradeManagerUtils.preProductInfoByBroadDiscntId4CB(msg);
                discntList.add(disMap);
            }

            if (null != discntList && !discntList.isEmpty()) {
                msg.put("discntList", discntList);
            }
            ext.put("tradeOther", preTradeOther(msg));
            ext.put("tradeDiscnt", preDiscntData(msg));
            ext.put("tradeItem", preDataForTradeItem(msg, true, flag));
            ext.put("tradeSubItem", preDataForTradeSubItem(msg, true, flag));
            //变更资费时只下发订购退订的资费节点
            if (newProductId.equals(oldProductId)) {
                ext.remove("tradeSvc");
                ext.remove("tradeOther");
                ext.remove("tradeProduct");
                ext.remove("tradeProductType");
                ext.remove("tradeSp");
                ext.put("tradeDiscnt", preForTradeDiscnt(msg, ext));
            }
        }
        else {
            ext.put("tradeItem", preDataForTradeItem(msg, true, flag));
            ext.put("tradeSubItem", preDataForTradeSubItem(msg, true, flag));
        }
        msg.put("ext", ext);
        Map base = preBaseData(msg, exchange.getAppCode(), false, flag);
        msg.put("base", base);
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("orderId"));
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.smcom.preSub.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    private Map preForTradeDiscnt(Map msg, Map ext) {
        List<Map> disItem = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(ext.get("tradeDiscnt"))) {
            disItem = (List<Map>) ((Map) ext.get("tradeDiscnt")).get("item");
        }
        Map item = new HashMap();
        Map tradeDiscnt = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("id", msg.get("userId"));
        item.put("idType", "1");
        item.put("userIdA", "-1");
        item.put("productId", msg.get("oldProductId"));
        item.put("packageId", msg.get("packageId"));
        item.put("discntCode", msg.get("bDiscntCode"));
        item.put("specTag", "0");
        item.put("relationTypeCode", "");
        item.put("startDate", msg.get("oldStartData"));
        item.put("itemId", msg.get("itemId"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        item.put("modifyTag", "1");
        disItem.add(item);
        tradeDiscnt.put("item", disItem);
        return tradeDiscnt;
    }

    private Map preBaseData(Map msg, String appCode, Boolean isMixNum, Boolean flag) {
        Map base = new HashMap();
        base.put("startDate", GetDateUtils.getDate());
        base.put("olcomTag", "0");
        base.put("areaCode", msg.get("eparchyCode"));
        base.put("foregift", "0");
        base.put("execTime", GetDateUtils.getDate());
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("endAcycId", "203701");
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        base.put("acceptMonth", RDate.currentTimeStr("MM"));
        base.put("advancePay", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("checktypeCode", "0");
        base.put("termIp", "10.124.0.48");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("cityCode", msg.get("district"));
        base.put("remark", new PreDataProcessor().getRemark(msg));
        base.put("subscribeId", msg.get("orderId"));
        base.put("tradeDepartId", msg.get("departId"));
        base.put("custId", msg.get("custId"));
        base.put("custName", msg.get("custName"));
        base.put("acctId", msg.get("acctId"));
        base.put("usecustId", msg.get("custId"));
        base.put("feeState", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("nextDealTag", "Z");
        if (isMixNum) {
            base.put("tradeId", msg.get("orderId"));
            base.put("userDiffCode", "8900");
            base.put("brandCode", "COMP");
            base.put("tradeTypeCode", "0110");
            base.put("netTypeCode", "00CP");
            base.put("userId", msg.get("mixUserId"));
            base.put("productId", msg.get("mixProduct"));
            base.put("serinalNamber", msg.get("mixNumber"));
        }
        else {
            base.put("tradeId", msg.get("tradeId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", msg.get("brandCode"));
            base.put("tradeTypeCode", "0340");
            base.put("netTypeCode", "0040");
            base.put("userId", msg.get("userId"));
            base.put("productId", msg.get("productId"));
            base.put("serinalNamber", msg.get("serialNumber"));
            if (flag) {
                base.put("productId", msg.get("newProductId"));
            }
        }
        return base;
    }

    // 拼装TRADE_USER
    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("productTypeCode", "COMP");
            item.put("userPasswd", "123456");
            item.put("userTypeCode", "0");
            item.put("scoreValue", "0");
            item.put("creditClass", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("acctTag", "0");
            item.put("prepayTag", "0");
            item.put("inDate", GetDateUtils.getDate());
            item.put("openDate", GetDateUtils.getDate());
            item.put("openMode", "0");
            item.put("openDepartId", msg.get("channelId"));
            item.put("openStaffId", msg.get("operatorId"));
            item.put("inDepartId", msg.get("channelId"));
            item.put("inStaffId", msg.get("operatorId"));
            item.put("removeTag", "0");
            item.put("userStateCodeset", "0");
            item.put("mputeMonthFee", "0");
            item.put("developDate", GetDateUtils.getDate());
            item.put("developStaffId", msg.get("recomPersonId"));
            item.put("developDepartId", msg.get("recomDepartId"));
            item.put("inNetMode", "0");
            itemList.add(item);
            tradeUser.put("item", itemList);
            return tradeUser;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_USER节点报错" + e.getMessage());
        }
    }

    private Map preDataForTradeItem(Map msg, Boolean isCY, Boolean flag) throws Exception {
        List<Map> Item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        if (flag) {
            if (isCY) {
                Item.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "4"));
                Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
                Item.add(LanUtils.createTradeItem("OPER_CODE", "3"));
                Item.add(LanUtils.createTradeItem("NEW_PRODUCT_ID", msg.get("newProducrId")));
                Item.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
                Item.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
                Item.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
                Item.add(LanUtils.createTradeItem("WORK_TRADE_ID_SWITCH", ""));
                Item.add(LanUtils.createTradeItem("BOOK_FLAG", ""));
                Item.add(LanUtils.createTradeItem("NET_TYPE_CODE", "40"));
                Item.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
                Item.add(LanUtils.createTradeItem("IS_SAME_CUST", msg.get("custId")));
                Item.add(LanUtils.createTradeItem("MAIN_USER_TAG", ""));
                Item.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
                Item.add(LanUtils.createTradeItem("PRE_TIME_SPAN", ""));
                Item.add(LanUtils.createTradeItem("PRE_START_HRS", ""));
                Item.add(LanUtils.createTradeItem("PRODUCT_TYPE_CODE", msg.get("productTypeCode")));
                Item.add(LanUtils.createTradeItem("NEW_BRAND_CODE", msg.get("brandCode")));
                Item.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
                Item.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", msg.get("mixProduct")));
                Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "1"));
            }
            else {
                Item.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", msg.get("channelId")));
                Item.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", msg.get("operatorId")));
                Item.add(LanUtils.createTradeItem("ACTOR_ADDRESS", ""));
                Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
                Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "2"));
            }
        }
        else {
            if (isCY) {
                Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "1"));
                Item.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", msg.get("mixProduct")));
                Item.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
                Item.add(LanUtils.createTradeItem("PRE_START_HRS", ""));
                Item.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
                Item.add(LanUtils.createTradeItem("MAIN_USER_TAG", ""));
                Item.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
                Item.add(LanUtils.createTradeItem("IS_SAME_CUST", msg.get("custId")));
                Item.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
                Item.add(LanUtils.createTradeItem("WORK_TRADE_ID_SWITCH", ""));
                Item.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
                Item.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
                Item.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
                Item.add(LanUtils.createTradeItem("OPER_CODE", "3"));
                Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
                Item.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "4"));
            }
            else {
                Item.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", msg.get("channelId")));
                Item.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", msg.get("operatorId")));
                Item.add(LanUtils.createTradeItem("ACTOR_ADDRESS", ""));
                Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
                Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "2"));
            }
        }
        tradeItem.put("item", Item);
        return tradeItem;
    }

    private Map preDataForTradeSubItem(Map msg, Boolean isCY, Boolean flag) {
        List<Map> Item = new ArrayList<Map>();
        Map tradeSubItem = new HashMap();
        String startDate = GetDateUtils.getNextMonthFirstDayFormat();
        String endDate = "20501231235959";
        String discntitemId = (String) msg.get("itemId1");
        if (flag) {
            if (isCY) {
                Map cycleItem = TradeManagerUtils.selBroadByBroadDiscntId4CB(msg);
                String expireDealMode = "1".equals(msg.get("delayType")) ? "a"
                        : "3".equals(msg.get("delayType")) ? "t" : "b";
                Item.add(lan.createTradeSubItemC(discntitemId, "adEnd", "", startDate, endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "expireDealMode", expireDealMode, startDate, endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "aDiscntCode", msg.get("delayDiscntId"), startDate,
                        endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "", startDate, endDate));// ECS宽带包年逻辑修改为从三户获取bDiscntCode的value值下发给CB(start)
                Item.add(lan.createTradeSubItemC(discntitemId, "effectMode", "1", startDate, endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "adStart", GetDateUtils.getNextMonthFirstDayFormat(),
                        startDate, endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "cycleNum", cycleItem.get("cycleNum"), startDate,
                        endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "cycle", cycleItem.get("cycle"), startDate, endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "fixedHire", "12", startDate, endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "cycleFee", cycleItem.get("cycleFee"), startDate,
                        endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "recharge", "", startDate, endDate));
                Item.add(lan.createTradeSubItemC(discntitemId, "callBack", "0", startDate, endDate));
                Item.add(lan.createTradeSubItemE("SPEED",
                        IsEmptyUtils.isEmpty(msg.get("speedLevel")) ? "10" : msg.get("speedLevel"),
                                (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("contactPerson"), (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("contactPhone"), (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("PHONE_VERIFIED", "0", (String) msg.get("userId")));

            }
            else {
                Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("contactPerson"), (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("contactPhone"), (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("PHONE_VERIFIED", "0", (String) msg.get("userId")));
            }
        }
        else {
            if (isCY) {
                Item.add(lan.createTradeSubItemE("SPEED", msg.get("speedLevel"), (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("contactPerson"), (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("contactPhone"), (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemE("PHONE_VERIFIED", "0", (String) msg.get("userId")));
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "expireDealMode", "a",
                        GetDateUtils.getDate(),
                        "20501231235959"));
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "bDiscntCode", msg.get("bDiscntCode"),
                        GetDateUtils.getDate(),
                        "20501231235959"));
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "callBack", "0", GetDateUtils.getDate(),
                        "20501231235959"));
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "recharge", "", GetDateUtils.getDate(),
                        "20501231235959"));
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "cycleNum", "1", GetDateUtils.getDate(),
                        "20501231235959"));
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "adEnd", "", GetDateUtils.getDate(),
                        "20501231235959"));
            }
            else {
                Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("contactPerson"), (String) msg.get("mixUserId")));
                Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("contactPhone"), (String) msg.get("mixUserId")));
                Item.add(lan.createTradeSubItemE("PHONE_VERIFIED", "0", (String) msg.get("mixUserId")));
            }
        }
        tradeSubItem.put("item", Item);
        return tradeSubItem;
    }

    private void orderSub(Map mixMap, Map phoneMap, Exchange exchange, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        dealSubmit(submitMap, mixMap, phoneMap, msg);
        List<Map> payList = new ArrayList<Map>();
        Map pay = new HashMap();
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("payInfo"))) {
            List<Map> payInfoList = (List<Map>) msg.get("payInfo");
            for (Map payInfo : payInfoList) {
                pay.put("payType", ChangeCodeUtils.changePayType4N6odsb(payInfo.get("payType")));
                pay.put("payMoney", submitMap.get("origTotalFee"));
                pay.put("subProvinceOrderId", msg.get("provOrderId"));
            }
            payList.add(pay);
            submitMap.put("payInfo", payList);
        }
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[3], submitExchange);
        try {
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Map outMap = new HashMap();
        outMap.put("code", "0000");
        outMap.put("detail", "OK");
        outMap.put("orderNo", orderNo);
        outMap.put("provOrderId", msg.get("provOrderId"));
        outMap.put("provOrderId2", msg.get("provOrderId2"));
        Message out = new DefaultMessage();
        out.setBody(outMap);
        exchange.setOut(out);
    }

    /**
     * 拼装正式提交参数
     */
    /**
     *
     * 整理正式提交的參數
     */
    private Map dealSubmit(Map submitMap, Map mixMap, Map phoneMap, Map msg) {
        List<Map> rspInfo = (ArrayList<Map>) mixMap.get("rspInfo");
        submitMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        List<Map> phoneRspInfo = (ArrayList<Map>) phoneMap.get("rspInfo");
        Object provOrderId2 = "";
        Object provOrderId = rspInfo.get(0).get("bssOrderId");
        submitMap.put("provOrderId", provOrderId);
        submitMap.put("orderNo", msg.get("orderNo"));
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Integer totalFee = 0;
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if (!IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            totalFee = Integer.valueOf(provinceOrderInfo.get(0).get("totalFee").toString());
        }
        subOrderSubReq.add(dealFeelInfo(rspInfo));
        for (Map rspMap : phoneRspInfo) {
            List<Map> provinceOrder = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrder && provinceOrder.size() > 0) {
                for (Map orderInfo : provinceOrder) {
                    if (!"0".equals(orderInfo.get("totalFee"))) {
                        totalFee = totalFee + Integer.valueOf(orderInfo.get("totalFee").toString());
                        provOrderId2 = orderInfo.get("subOrderId");
                    }
                }
            }
        }
        msg.put("provOrderId", provOrderId2);
        msg.put("provOrderId2", provOrderId);
        subOrderSubReq.add(dealFeelInfo(phoneRspInfo));
        submitMap.put("subOrderSubReq", subOrderSubReq);
        submitMap.put("origTotalFee", totalFee);
        submitMap.put("cancleTotalFee", "0");
        return submitMap;
    }

    private Map dealFeelInfo(List<Map> rspInfo) {
        Map subOrderSubMap = new HashMap();
        String subProvinceOrderId = (String) rspInfo.get(0).get("bssOrderId");
        String subOrderId = (String) rspInfo.get(0).get("provOrderId");
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                for (Map provinceOrder : provinceOrderInfo) {
                    List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (!IsEmptyUtils.isEmpty(feeInfo)) {
                        for (Map fee : feeInfo) {
                            fee.put("feeCategory", fee.get("feeMode"));
                            fee.put("feeId", fee.get("feeTypeCode"));
                            fee.put("feeDes", fee.get("feeTypeName"));
                            fee.put("origFee", fee.get("oldFee"));
                            fee.put("realFee", fee.get("oldFee"));
                            fee.put("isPay", "0");
                            fee.put("calculateTag", "N");
                            fee.put("payTag", "1");
                            fee.put("calculateId", GetSeqUtil.getSeqFromCb());
                            fee.put("calculateDate", DateUtils.getDate());
                        }
                        subProvinceOrderId = (String) provinceOrder.get("subProvinceOrderId");
                        subOrderId = (String) provinceOrder.get("subOrderId");
                        subOrderSubMap.put("feeInfo", feeInfo);
                    }
                }
            }
        }
        subOrderSubMap.put("subProvinceOrderId", subProvinceOrderId);
        subOrderSubMap.put("subOrderId", subOrderId);
        return subOrderSubMap;
    }

    public Map preDiscntData(Map msg) {
        Map tradeDis = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> discnt = (List<Map>) msg.get("discntList");
        for (int i = 0; i < discnt.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("id", msg.get("userId"));
            item.put("idType", "1");
            item.put("userIdA", "-1");
            item.put("productId", discnt.get(i).get("productId"));
            item.put("packageId", discnt.get(i).get("packageId"));
            item.put("discntCode", discnt.get(i).get("discntCode"));
            item.put("specTag", "0");
            item.put("relationTypeCode", "");
            item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            item.put("itemId", msg.get("itemId1"));
            if (null != discnt.get(i).get("activityTime")) {
                item.put("endDate", discnt.get(i).get("activityTime"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            if ("50".equals(discnt.get(i).get("productMode"))) {
                item.put("itemId", msg.get("itemId1"));
            }
            item.put("modifyTag", "0");
            itemList.add(item);

        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    private Map preTradeOther(Map inputMap) {
        Map TradeOther = new HashMap();
        List<Map> item = new ArrayList<Map>();
        String monLastTime = GetDateUtils.getMonthLastDayFormat();
        List<Map> oldProductList = queryProductInfoWithLimit("00" + inputMap.get("province"),
                (String) inputMap.get("oldProductId"), (String) inputMap.get("eparchyCode"), "02", "00");
        if (null == oldProductList || oldProductList.isEmpty()) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + inputMap.get("oldProductId")
                    + "】的产品信息");
        }
        String subId = String.valueOf(oldProductList.get(0).get("PRODUCT_ID"));
        List<Map> subOldProductItem = queryProductItemInfoNoPtype(subId);
        if (null == subOldProductItem || subOldProductItem.isEmpty()) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品属性表【" + inputMap.get("oldProductId") + "】的产品信息");
        }
        String subProductTypeCode = TradeManagerUtils.getValueFromItem("PRODUCT_TYPE_CODE", subOldProductItem);
        String subBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", subOldProductItem);

        Map itemMap = MapUtils.asMap("xDatatype", null, "rsrvValueCode", "NEXP", "rsrvValue",
                inputMap.get("userId"), "rsrvStr1", inputMap.get("oldProductId"), "rsrvStr2", "00", "rsrvStr3",
                "-9", "rsrvStr4", subProductTypeCode, "rsrvStr5", subProductTypeCode, "rsrvStr6", "-1", "rsrvStr7",
                "0",
                "rsrvStr8", "", "rsrvStr9", subBrandCode, "rsrvStr10", inputMap.get("serialNumber"), "modifyTag", "1",
                "startDate", inputMap.get("oldProductStartDate"), "endDate", monLastTime);
        item.add(itemMap);

        TradeOther.put("item", item);
        return TradeOther;
    }

    /**
     * @param provinceCode;省份编码
     * @param commodityId;产品ID
     * @param eparchyCode;地市编码
     * @param firstMonBillMode;首月付费模式
     * @param productMode;产品类型00主产品；01附加产品；04活动
     */
    private List<Map> queryProductInfoWithLimit(String provinceCode, String commodityId, String eparchyCode,
            String firstMonBillMode, String productMode) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        // 需要新增Limit表，新写SQL 暂用qryDefaultPackageElement
        Map param = new HashMap();
        param.put("PROVINCE_CODE", provinceCode);
        param.put("PRODUCT_ID", commodityId);
        param.put("EPARCHY_CODE", eparchyCode);
        param.put("FIRST_MON_BILL_MODE", firstMonBillMode);
        param.put("PRODUCT_MODE", productMode);
        System.out.println("zsqtest7" + param);
        return n25Dao.queryProductInfoWithLimit(param);
    }

    /**
     * @param provinceCode;省份编码++++
     * @param commodityId;产品ID
     * @param eparchyCode;地市编码
     * @param firstMonBillMode;首月付费模式
     * @param productMode;产品类型00主产品；01附加产品；04活动
     */
    private List<Map> queryProductItemInfoNoPtype(String subId) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map param = new HashMap();
        param.put("PRODUCT_ID", subId);
        return n25Dao.queryProductAndPtypeProduct(param);
    }

    private void orderChgSub(Map mixMap, Map phoneMap, Exchange exchange, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        dealSubmit(submitMap, mixMap, phoneMap, msg);
        List<Map> payList = new ArrayList<Map>();
        Map pay = new HashMap();
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("payInfo"))) {
            List<Map> payInfoList = (List<Map>) msg.get("payInfo");
            for (Map payInfo : payInfoList) {
                pay.put("payType", ChangeCodeUtils.changePayType4N6odsb(payInfo.get("payType")));
                pay.put("payMoney", submitMap.get("origTotalFee"));
                pay.put("subProvinceOrderId", msg.get("provOrderId"));
            }
            payList.add(pay);
            submitMap.put("payInfo", payList);
        }
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[3], submitExchange);
        try {
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Map outMap = new HashMap();
        outMap.put("code", "0000");
        outMap.put("detail", "OK");
        outMap.put("orderNo", orderNo);
        outMap.put("provOrderId", msg.get("provOrderId"));
        outMap.put("provOrderId2", msg.get("provOrderId2"));
        Message out = new DefaultMessage();
        out.setBody(outMap);
        exchange.setOut(out);
    }

    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
