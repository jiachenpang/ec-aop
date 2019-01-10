package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.util.IsEmptyUtils;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.DealCertTypeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.MixOpenUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;

/**
 * 宽带单装预提交
 * boradDiscntAttr开户选择的资费方式，存在时代表是包年
 */
@EcRocTag("CBopenBroadBandPreCommit")
@SuppressWarnings({ "unchecked", "rawtypes", "static-access" })
public class CBOpenBroadBandPreCommitProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.sbac.sglUniTradeParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping", "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.mvoa.queryMainAndVice.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    @Override
    public void process(Exchange exchange) throws Exception {
        // 调用客户信息校验之前，请求参数的保留备份

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        LanUtils lan = new LanUtils();

        // 20180615针对新零售修改inModeCode和E_IN_MODE为N-------------------start-------------------------
        List<Map> paraList = new ArrayList<Map>();
        paraList = (List<Map>) msg.get("para");
        boolean newRetailFlag = false;
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map paraMap : paraList) {
                if ("NEWRETAIL".equals(paraMap.get("paraId")) && "N".equals(paraMap.get("paraValue"))) {
                    newRetailFlag = true;
                }
            }
        }
        msg.put("newRetailFlag", newRetailFlag);
        // 20180615针对新零售修改inModeCode和E_IN_MODE为N--------------------end-------------------------

        // 调用预提交接口
        Object userName = msg.get("userName");
        Object userPasswd = msg.get("userPasswd");
        String operTypeCode = "0";
        msg.putAll(dealInparams(exchange));
        msg.put("operTypeCode", operTypeCode);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", exchange);

        body = (Map) exchange.getOut().getBody();

        // 整理返回报文
        Map returnMap = dealResult(exchange, userName, userPasswd, msg);
        exchange.getOut().setBody(returnMap);

    }

    // 处理返回结果
    private Map dealResult(Exchange exchange, Object userName, Object userPasswd, Map msg) {
        Map body = (Map) exchange.getOut().getBody();
        List rspInfos = (List) body.get("rspInfo");
        Map rspInfo = (Map) rspInfos.get(0);
        Map retMap = new HashMap();

        List<Map> provinceOrderInfos = (List) rspInfo.get("provinceOrderInfo");
        String isLastMember = (String) msg.get("isLastMember");
        // 处理融合多个订单和费用节点返回
        if ("1".equals(isLastMember)) {
            Integer totalFee = 0;
            String bssOrderId = "";
            String provOrderId = "";
            List<Map> subOrderInfo = new ArrayList<Map>();
            ELKAopLogger.logStr(exchange.getProperty(Exchange.APPTX) + "处理预提交返回：" + rspInfos);
            bssOrderId = (String) rspInfo.get("bssOrderId");
            provOrderId = (String) rspInfo.get("provOrderId");

            // 费用计算
            for (Map provinceOrder : provinceOrderInfos) {
                Map subOrderMap = new HashMap();
                Object subOrderId = provinceOrder.get("subProvinceOrderId");
                subOrderInfo.add(MapUtils.asMap("subOrderId", subOrderId));
                totalFee = totalFee + Integer.valueOf((String) provinceOrder.get("totalFee"));
                List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                    continue;
                }

                List<Map> fList = dealFee(preFeeInfoRsp);
                if (!IsEmptyUtils.isEmpty(fList)) {
                    subOrderInfo.add(MapUtils.asMap("feeInfo", fList));
                }
            }
            retMap.put("provOrderId", provOrderId);
            retMap.put("bssOrderId", bssOrderId);
            retMap.put("totalFee", totalFee + "");
            retMap.put("subOrderInfo", subOrderInfo);
        }
        else {

            List<Map> feeInfos = new ArrayList();
            String provOrderId = (String) rspInfo.get("bssOrderId");

            for (Map provinceOrderInfo : provinceOrderInfos) {
                List<Map> preFeeInfoRsp = (List) provinceOrderInfo.get("preFeeInfoRsp");
                for (Map feeInfo : preFeeInfoRsp) {
                    Map temp = new HashMap();
                    temp.put("feeId", feeInfo.get("feeTypeCode"));
                    temp.put("feeCategory", feeInfo.get("feeMode"));
                    temp.put("feeDes", feeInfo.get("feeTypeName"));
                    if (null != feeInfo.get("maxDerateFee") && !"0".equals(feeInfo.get("maxDerateFee"))
                            && !"".equals(feeInfo.get("maxDerateFee"))) {
                        temp.put("maxRelief", feeInfo.get("maxDerateFee") + "0");
                    }
                    else {
                        temp.put("maxRelief", feeInfo.get("maxDerateFee"));
                    }
                    if (null != feeInfo.get("fee") && !"0".equals(feeInfo.get("fee")) && !"".equals(feeInfo.get("fee"))) {
                        temp.put("origFee", feeInfo.get("fee") + "0");
                    }
                    else {
                        temp.put("origFee", feeInfo.get("fee"));
                    }
                    feeInfos.add(temp);
                }
            }
            double totalFee = 0;
            for (Map feeInfo : feeInfos) {
                String origFee = (String) feeInfo.get("origFee");
                double origFee1 = Double.parseDouble(origFee);
                totalFee += origFee1;
            }
            retMap.put("provOrderId", provOrderId);
            retMap.put("userName", userName);
            retMap.put("userPasswd", userPasswd);
            retMap.put("feeInfo", feeInfos);
            retMap.put("totalFee", totalFee);
            return retMap;
        }

        return retMap;
    }

    private List<Map> dealFee(List<Map> feeList) {
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode") + "");
            retFee.put("feeCategory", fee.get("feeMode") + "");
            retFee.put("feeDes", fee.get("feeTypeName") + "");
            if (null != fee.get("maxDerateFee")) {
                retFee.put("maxRelief", fee.get("maxDerateFee"));
            }
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    // 处理参数
    /**
     * @param exchange
     * @return
     * @throws Exception
     */
    private Map dealInparams(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> bindInfo = (List) msg.get("bindInfo");
        String bssOrderId = (String) msg.get("bssOrderId");
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[1], exchange, "seq_trade_id", 1).get(0);
        exchange.getIn().setBody(body);
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[1], exchange, "seq_item_id", 1).get(0);
        exchange.getIn().setBody(body);
        String userId = (String) GetSeqUtil.getSeqFromCb(pmp[1], exchange, "seq_user_id", 1).get(0);
        msg.put("userId", userId);
        exchange.getIn().setBody(body);
        String custType = (String) msg.get("custType");
        String custId = "";
        String boradDiscntId = "";// 开户选择的主资费节点
        String boradDiscntCycle = "";// 包年资费周期
        if ("1".equals(custType)) {
            custId = (String) msg.get("custId");
        }
        else {
            custId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_cust_id", 1)
                    .get(0);
        }
        String acctId = "";
        String bindSrc = "";
        if (StringUtils.isNotEmpty((String) newUserInfo.get("createOrExtendsAcct"))) {
            if ("1".equals(newUserInfo.get("createOrExtendsAcct"))) {
                Map threePartMap = new HashMap();
                MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
                threePartMap.put("tradeTypeCode", "9999");
                System.out.println("ssptest debutySerialNumber" + newUserInfo.get("debutySerialNumber"));
                threePartMap.put("serialNumber", newUserInfo.get("debutySerialNumber"));
                threePartMap.put("getMode", "1111111111100013010000000100001");
                msg.put("provinceCode", msg.get("province"));
                LanUtils lan = new LanUtils();
                if (bindInfo != null && bindInfo.size() > 0) {
                    bindSrc = (String) bindInfo.get(0).get("bindSrc");
                }
                if ("2".equals(bindSrc)) {
                    Map para = MapUtils.asMap("paraId", "noMainCard", "paraValue", "0");
                    List<Map> paraList = new ArrayList<Map>();
                    paraList.add(para);
                    threePartMap.put("para", paraList);
                    threePartMap.put("provinceCode", msg.get("province"));
                    Exchange queryExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
                    lan.preData(pmp[3], queryExchange);
                    CallEngine.wsCall(queryExchange, "ecaop.comm.conf.url.cbss.services.VoyungoForAopSer");
                    lan.xml2Json("ecaop.trades.mvoa.queryMainAndVice.template", queryExchange);
                    Map checkVice = queryExchange.getOut().getBody(Map.class);
                    Map userInfo = (Map) checkVice.get("userInfo");
                    if (IsEmptyUtils.isEmpty(userInfo)) {
                        throw new EcAopServerBizException("9999", "OH压单查询未返回userInfo信息");
                    }
                    acctId = (String) userInfo.get("acctId");
                }
                else {
                    Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
                    lan.preData(pmp[2], threePartExchange);
                    CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
                    lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
                    Map out = threePartExchange.getOut().getBody(Map.class);
                    Map acctInfo = ((List<Map>) out.get("acctInfo")).get(0);
                    acctId = (String) acctInfo.get("acctId");
                }
            }
            else {
                exchange.getIn().setBody(body);
                acctId = (String) GetSeqUtil.getSeqFromCb(pmp[1], exchange, "seq_acct_id", 1).get(0);
            }
        }
        System.out.println("debutySerialNumber.value" + acctId);
        String installAddress = (String) newUserInfo.get("installAddress");
        List<Map> productInfo = (List) newUserInfo.get("productInfo");
        for (Map product : productInfo) {
            if ("1".equals(product.get("productMode"))) {
                product.put("productMode", "0");
            }
            else {
                product.put("productMode", "1");
            }
        }
        List<Map> activityInfos = (List) newUserInfo.get("activityInfo");
        msg.put("activityInfo", activityInfos);
        String provinceCode = "00" + msg.get("province");
        // 调用产品方法
        msg.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        // iptv产品处理
        String iptvProductId = newUserInfo.get("iptvProductId") + "";
        System.out.println("iptvProductIdtest1:" + iptvProductId);
        if (!"null".equals(iptvProductId)) {
            Map addproparam = new HashMap();
            addproparam.put("isIpOrInterTv", "1");
            // addproparam.put("PROVINCE_CODE", provinceCode);
            addproparam.put("productId", iptvProductId);
            addproparam.put("productMode", "1");
            // addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            // addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
            productInfo.add(addproparam);
            System.out.println("iptvProductIdtest2:" + productInfo);
        }
        // iptv产品处理
        // RXZ2017083000002-外围可以通过传入optType字段来取消某个默认资费的下发 by wangmc 20170928
        getRemoveElement(productInfo, msg);
        msg.put("methodCode", exchange.getMethodCode());
        ELKAopLogger.logStr("处理宽带单装主产品的非默认资费开始时间放入的值methodCode:" + exchange.getMethodCode());
        Map ext = TradeManagerUtils.newPreProductInfo(productInfo, provinceCode, msg);
        // RQH2018051600002-宽带增加sp处理需求 by wangmc 20180517
        // ext.remove("tradeSp");
        List discntList = (List) msg.get("discntList");
        List svcList = (List) msg.get("svcList");
        System.out.print("{}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}{}{}{}{}{}{}{28885245455725");
        List<Map> iptvInfos = (List<Map>) newUserInfo.get("iptvInfo");
        List<Map> interTvInfos = (List<Map>) newUserInfo.get("interTvInfo");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos) {
            for (Map map : boradDiscntInfos) {
                boradDiscntCycle = (String) map.get("boradDiscntCycle");
                boradDiscntId = (String) map.get("boradDiscntId");
                if (StringUtils.isEmpty(boradDiscntId)) {
                    throw new EcAopServerBizException("9999", "开户选择的主资费编码boradDiscntId为空，请检查");
                }

                // sql中的参数是ELEMENT_ID 但是Esql框架会对参数处理,若根据sql参数取不到值,则会将参数转为驼峰型,再取值 -wangmc 20171019
                msg.put("elementId", boradDiscntId);
                msg.put("PROVINCE_CODE", provinceCode);
                msg.put("EPARCHY_CODE", ChangeCodeUtils.changeEparchy(msg));
                // 改成调用去重的方法
                discntList.addAll(TradeManagerUtils.preDistProductInfoByElementIdNew(msg, productInfo));
            }
        }
        System.out.println("ssptestdiscnt" + discntList);
        // 获取iptv资费信息
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        List<String> iptvItem = new ArrayList<String>();
        List<String> iptvItemD = new ArrayList<String>();

        if (iptvInfos != null) {
            int iptvSize = iptvInfos.size();
            iptvItem = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_item_id", iptvSize);

            for (int i = 0; i < iptvSize; i++) {
                String elementId;
                String serviceId = (String) iptvInfos.get(i).get("IptvService");
                if (serviceId != null && serviceId.length() > 0) {
                    msg.put("itemId", iptvItem.get(i));
                    msg.put("ELEMENT_ID", serviceId);
                    msg.put("ELEMENT_TYPE_CODE", "S");
                    svcList.add(TradeManagerUtils.preProductInfoByIpsServiceId4CB(msg, iptvProductId));
                }
                // 资费
                List IptvDiscntInfos = (List) iptvInfos.get(i).get("IptvDiscntInfo");
                iptvItemD = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_item_id", iptvSize);
                msg.put("iptvItem", iptvItemD); // 下面tradeSubItem要用
                if (null != IptvDiscntInfos) {
                    for (int j = 0; j < IptvDiscntInfos.size(); j++) {
                        Map IptvDiscntInfo = (Map) IptvDiscntInfos.get(j);
                        String iptvDelayDiscntType = "";
                        Map iptvDiscntAttrMap = (Map) IptvDiscntInfo.get("iptvDiscntAttr");
                        if (!IsEmptyUtils.isEmpty(iptvDiscntAttrMap)) {
                            iptvDelayDiscntType = (String) iptvDiscntAttrMap.get("iptvDelayDiscntType");
                        }
                        elementId = (String) IptvDiscntInfo.get("IptvDiscntId");
                        if (!"0".equals(elementId)) {
                            msg.put("itemId", iptvItemD.get(i));
                            msg.put("ELEMENT_ID", elementId);
                            discntList.add(TradeManagerUtils.preProductInfoByIpTvElementId4CB(msg, iptvProductId,
                                    iptvDelayDiscntType));
                        }
                    }
                }

            }
        }
        List<String> interTvItem = new ArrayList<String>();
        if (interTvInfos != null) {
            String interTvProductId = (String) newUserInfo.get("interTvProductId");
            int interTvSize = interTvInfos.size();
            interTvItem = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_item_id", interTvSize);
            for (int i = 0; i < interTvSize; i++) {
                String serviceId = (String) interTvInfos.get(i).get("interTvInfoService");
                if (null != interTvInfos.get(i).get("interTvDiscntInfo")) {
                    msg.put("itemId", interTvItem.get(i));
                    List interTvDiscntInfos = (List) interTvInfos.get(i).get("interTvDiscntInfo");
                    for (int j = 0; j < interTvDiscntInfos.size(); j++) {
                        Map interTvDiscntInfo = (Map) interTvDiscntInfos.get(0);
                        String elementId = (String) interTvDiscntInfo.get("interTvDiscntId");
                        if (!"0".equals(elementId)) {
                            msg.put("ELEMENT_ID", elementId);
                            discntList.add(TradeManagerUtils.preProductInfoByElementId4CB(msg));
                        }
                        // msg.put("serviceId", serviceId);
                        // svcList.add(TradeManagerUtils.preProductInfoByServiceId(msg));
                        // 单独给浙江加分支处理互联网电视产品
                        if ("zjpre|nxpr|scpr|scasb".contains(exchange.getAppCode())) {
                            Map tempMap = new HashMap();
                            msg.put("serviceId", serviceId);
                            msg.put("itemId", interTvItem.get(i));
                            msg.put("PROVINCE_CODE", provinceCode);
                            msg.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            msg.put("ELEMENT_ID", serviceId);
                            msg.put("ELEMENT_TYPE_CODE", "S");
                            msg.put("PRODUCT_ID", interTvProductId);
                            if (IsEmptyUtils.isEmpty(interTvProductId)) {
                                svcList.add(TradeManagerUtils.preProductInfoByServiceId4CB(msg));
                            }
                            else {
                                svcList.add(TradeManagerUtils.preProductInfoNoDefaultTag4CB(msg));
                            }
                        }
                    }
                }
            }
        }

        Map tradeProduct = (Map) ext.get("tradeProduct");
        List<Map> item = (List) tradeProduct.get("item");
        String productId = null;
        String brandCode = null;
        for (Map m : item) {
            // 获取主产品id放在base节点中 by wangmc 20180211
            if (m.get("productId") != null && "00".equals(m.get("productMode"))) {
                productId = (String) m.get("productId");
            }
            if (m.get("brandCode") != null && "00".equals(m.get("productMode"))) {
                brandCode = (String) m.get("brandCode");
            }
        }
        Map tradeProductType = (Map) ext.get("tradeProductType");
        List<Map> itemT = (List) tradeProductType.get("item");
        String productTypeCode = null;
        for (Map m : itemT) {
            if (m.get("productTypeCode") != null) {
                productTypeCode = (String) m.get("productTypeCode");

            }
        }

        String reTypeCode = "0";//
        String resCode = (String) msg.get("serialNumber");
        String operatorId = (String) msg.get("operatorId");
        String channelId = (String) msg.get("channelId");

        // 如果传入融合虚拟用户订单号，说明是融合新装宽带业务，需要subscribeId需要和融合的订单绑定 by zhaok Date 9/25
        String subscribeId = "";
        boolean isMixFixed = false;
        if (!IsEmptyUtils.isEmpty(bssOrderId)) {
            subscribeId = bssOrderId; // 融合的订单ID
            isMixFixed = true;
        }
        else {
            subscribeId = tradeId;
        }
        String startDate = GetDateUtils.getDate();
        String nextDate = GetDateUtils.getNextMonthFirstDayFormat();
        String endDate = "20501231235959";
        String acceptDate = startDate;
        String nextDealTag = "Z";
        String olcomTag = "0";
        String tradeTypeCode = "0010";
        String usecustId = custId;
        String userDiffCode = "00";
        String netTypeCode = "0040";
        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String termIp = "127.0.0.1";

        String cityCode = (String) msg.get("district");
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String execTime = GetDateUtils.getDate();
        String operFee = "0";
        String foregift = "0";
        String advancePay = "0";
        String cancelTag = "0";
        String chkTag = "0";
        // acctTrade
        String payName = (String) msg.get("userName");
        if (payName == null) {
            payName = custName;
        }
        String xDataType = "NULL"; // 默认值为null
        String payModeCode = "0";
        String scoreValue = "0";
        String creditClassId = "0";
        String basicCreditValue = "0";
        String creditValue = "0";
        String creditControlId = "0";
        String debutyUserId = userId;
        String removeTag = "0";
        String openDate = GetDateUtils.getDate();

        // tradeUser
        String userPassword = (String) msg.get("userPasswd");
        String userTypeCode = "0";
        String creditClass = "0";
        String acctTag = "0";
        String preTag = "0";
        String inDate = GetDateUtils.getDate();
        String openMode = "0";
        String userStateCodeset = "0";
        String mputeMonthFee = "0";
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developEparchyCode = (String) msg.get("recomPersonCityCode");
        String developCityCode = (String) msg.get("recomPersonDistrict");
        String developDepartId = (String) msg.get("recomPersonChannelId");
        String openDepartId = channelId;
        String openStaffId = operatorId;
        String inDepartId = channelId;
        String inStaffId = operatorId;
        String inNetMode = "0";
        // tradeProductType
        String modifyTag = "0";
        // tradePayrelation
        String payitemCode = "-1"; // 必传
        String acctPriority = "0";
        String userPriority = "0";
        String bindType = "1";
        String defaultTag = "1";
        String limitType = "0";
        String limit = "0";
        String complementTag = "0";
        String addupMonths = "0";
        String addupMethod = "0";

        String actTag = "1";
        // tradeOther
        String rsrvValueCode = "ZZFS";
        String rsrvValue = "0";
        String rsrvStr1 = (String) newUserInfo.get("certNum");
        String rsrvStr2 = "10";
        String rsrvStr3 = "-9";
        String rsrvStr6 = "1";
        String rsrvStr7 = "0";

        // 在北六库里查cbaccessType
        // LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map inMap = new HashMap();
        inMap.put("ACCESS_TYPE", newUserInfo.get("accessMode"));
        inMap.put("CB_ACCESS_TYPE", newUserInfo.get("cbssAccessMode"));
        inMap.put("PROVINCE_CODE", msg.get("province"));
        inMap.put("NET_TYPE_CODE", "40");
        MixOpenUtils mixOpenUtil = new MixOpenUtils();
        Map accesMap = mixOpenUtil.checkAccessCode(inMap);
        String accessMode = (String) accesMap.get("accessMode");
        String cbAccessType = (String) accesMap.get("cbssAccessMode");
        String userType = (String) newUserInfo.get("userType");
        // 用户类型转码,aop规范1对应下游0-普通用户 by wangmc 20171120
        if (StringUtils.isEmpty(userType) || ("xzpre|ynpre".equals(exchange.getAppCode()) && "1".equals(userType))) {
            userType = "0";
        }
        String acceptMode = (String) newUserInfo.get("acceptMode");
        if (StringUtils.isEmpty(acceptMode) || "1".equals(acceptMode)) {
            acceptMode = "0";
        }
        else {
            acceptMode = "1";
        }
        String userProperty = (String) newUserInfo.get("userProperty");
        if (StringUtils.isEmpty(userProperty)) {
            userProperty = "1";
        }
        else if ("2".equals(userProperty)) {
            userProperty = "3";
        }
        else if ("3".equals(userProperty)) {
            userProperty = "2";
        }
        else if ("4".equals(userProperty)) {
            userProperty = "5";
        }
        else if ("5".equals(userProperty)) {
            userProperty = "4";
        }
        else if ("6".equals(userProperty)) {
            userProperty = "99";
        }

        String cityMark = (String) newUserInfo.get("cityMark");
        if (StringUtils.isEmpty(cityMark) || "1".equals(cityMark)) {
            cityMark = "C";
        }
        else {
            cityMark = "T";
        }
        String payMode = (String) newUserInfo.get("payMode");
        if (StringUtils.isEmpty(payMode)) {
            payMode = "1";
        }
        String netMode = (String) newUserInfo.get("netMode");
        if (StringUtils.isEmpty(netMode)) {
            netMode = "1";
        }

        List machineInfos = (List) msg.get("machineInfo");
        String machineBrandCode = "";
        String machineModelCode = "";
        String machineTypeCode = "";
        String machineType = "";
        Object machineProvide = "";
        String machineMac = "";
        String terminalLyfs = "";
        if (null != machineInfos) {
            Map machineInfo = (Map) machineInfos.get(0);
            machineBrandCode = (String) machineInfo.get("machineBrandCode");
            machineModelCode = (String) machineInfo.get("machineModelCode");
            machineTypeCode = (String) machineInfo.get("machineTypeCode");
            machineType = (String) machineInfo.get("machineType");
            terminalLyfs = (String) machineInfo.get("terminalLyfs");// 20181024 terminalLyfs下发在tradeSubItem下---by yangzg
            if (machineType == null) {
                machineType = "1";
            }
            machineProvide = ChangeCodeUtils.changeMachineProvideToCB(machineInfo.get("machineProvide"));// zzc 20170309
            machineMac = (String) machineInfo.get("machineMac");
        }
        String delayType = "";
        String delayDiscntId = "";// 到期资费
        String delayYearDiscntId = "";// 续包年资费
        String delayDiscntType = "";
        if (null != boradDiscntInfos && !boradDiscntInfos.isEmpty()) {

            Map boradDiscntInfo = boradDiscntInfos.get(0);
            Map boradDiscntAttr = (Map) boradDiscntInfo.get("boradDiscntAttr");
            if (boradDiscntAttr != null) {
                delayType = (String) boradDiscntAttr.get("delayType");
                if (StringUtils.isEmpty(delayType)) {
                    throw new EcAopServerBizException("9999", "到期资费方式delayType为空，请检查");
                }
                delayYearDiscntId = (String) boradDiscntAttr.get("delayYearDiscntId");
                if (StringUtils.isEmpty(delayYearDiscntId) && "1".equals(delayType)) {
                    throw new EcAopServerBizException("9999", "续约包年时续包年资费必传");
                }
                delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
                delayDiscntType = (String) boradDiscntAttr.get("delayDiscntType");
                if (StringUtils.isEmpty(delayDiscntType)) {
                    throw new EcAopServerBizException("9999", "生效方式delayDiscntType为空，请检查");
                }
            }
        }

        String areaExchId = (String) newUserInfo.get("areaExchId");
        String pointExchId = (String) newUserInfo.get("pointExchId");
        String moduleExchId = (String) newUserInfo.get("moduleExchId");
        if (moduleExchId == null) {
            moduleExchId = "";
        }
        String addressCode = (String) newUserInfo.get("addressCode");
        if (addressCode == null) {
            addressCode = "";
        }
        String exchCode = (String) newUserInfo.get("exchCode");
        if (exchCode == null) {
            exchCode = "";
        }
        // 整理参数
        Map base = new HashMap();
        base.put("subscribeId", subscribeId);
        base.put("tradeId", tradeId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("acctId", acctId);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", nextDealTag);
        base.put("olcomTag", olcomTag);
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        if ((Boolean) msg.get("newRetailFlag")) {// 20180615针对新零售修改inModeCode和E_IN_MODE为N
            base.put("inModeCode", "N");
        }
        base.put("tradeTypeCode", tradeTypeCode);
        base.put("productId", productId);
        base.put("brandCode", brandCode);
        base.put("usecustId", usecustId);
        base.put("userDiffCode", userDiffCode);
        base.put("netTypeCode", netTypeCode);
        base.put("serinalNamber", serialNumber);
        base.put("custName", custName);
        base.put("termIp", termIp);
        base.put("eparchyCode", eparchyCode);
        base.put("cityCode", cityCode);
        base.put("execTime", execTime);
        base.put("operFee", operFee);
        base.put("foregift", foregift);
        base.put("advancePay", advancePay);
        base.put("cancelTag", cancelTag);
        base.put("chkTag", chkTag);
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("checktypeCode", "0");
        /* base.put("actorName", "");外圍不会传代办人信息，所以不需要下发
         * base.put("actorCertTypeId", "1");
         * base.put("actorPhone", "");
         * base.put("actorCertNum", ""); */
        base.put("contact", msg.get("contactPerson"));
        base.put("contactPhone", msg.get("contactPhone"));
        base.put("contactAddress", msg.get("contactAddress"));
        base.put("remark", null != msg.get("orderRemarks") ? msg.get("orderRemarks") : "");
        // tredeAcct

        Map tradeAcct = new HashMap();
        Map item1 = new HashMap();
        item1.put("xDatatype", xDataType);
        item1.put("acctId", acctId);
        item1.put("payName", payName);
        item1.put("payModeCode", payModeCode);
        item1.put("scoreValue", scoreValue);
        item1.put("creditClassId", creditClassId);
        item1.put("basicCreditValue", basicCreditValue);
        item1.put("creditValue", creditValue);
        item1.put("creditControlId", creditControlId);
        item1.put("debutyUserId", debutyUserId);
        item1.put("debutyCode", serialNumber);
        item1.put("removeTag", removeTag);
        item1.put("openDate", openDate);
        item1.put("custId", custId);
        tradeAcct.put("item", item1);

        // tradeUser
        String serviceArea = (String) newUserInfo.get("serviceArea");
        Map tradeUser = new HashMap();
        Map item2 = new HashMap();
        item2.put("xDatatype", xDataType);
        item2.put("usecustId", usecustId);
        item2.put("userPasswd", userPassword);
        item2.put("userTypeCode", userTypeCode);
        item2.put("scoreValue", scoreValue);
        item2.put("creditClass", creditClass);
        item2.put("basicCreditValue", basicCreditValue);
        item2.put("creditValue", creditValue);
        item2.put("acctTag", acctTag);
        item2.put("prepayTag", preTag);
        item2.put("inDate", inDate);
        item2.put("openDate", openDate);
        item2.put("openMode", openMode);
        item2.put("openDepartId", openDepartId);
        item2.put("openStaffId", openStaffId);
        item2.put("inDepartId", inDepartId);
        item2.put("inStaffId", inStaffId);
        item2.put("removeTag", removeTag);
        item2.put("userStateCodeset", userStateCodeset);
        item2.put("mputeMonthFee", mputeMonthFee);
        item2.put("developStaffId", developStaffId);
        item2.put("developDate", developDate);
        item2.put("developEparchyCode", eparchyCode);
        item2.put("developCityCode", msg.get("district"));
        item2.put("developDepartId", developDepartId);
        item2.put("inNetMode", inNetMode);
        item2.put("productTypeCode", productTypeCode);
        if (StringUtils.isNotEmpty(serviceArea)) {
            item2.put("cityCode", serviceArea);
        }
        else {
            item2.put("cityCode", msg.get("district"));
        }
        tradeUser.put("item", item2);
        // tradeRes
        Map tradeRes = new HashMap();

        Map item3 = new HashMap();
        item3.put("xDatatype", xDataType);
        item3.put("reTypeCode", reTypeCode);
        item3.put("resCode", resCode);
        item3.put("modifyTag", modifyTag);
        item3.put("startDate", startDate);
        item3.put("endDate", endDate);
        tradeRes.put("item", item3);

        // tradePayrelation
        Map tradePayrelation = new HashMap();
        Map item4 = new HashMap();
        item4.put("userId", userId);
        item4.put("acctId", acctId);
        item4.put("payitemCode", payitemCode);
        item4.put("acctPriority", acctPriority);
        item4.put("userPriority", userPriority);
        item4.put("bindType", bindType);
        item4.put("defaultTag", defaultTag);
        item4.put("limitType", limitType);
        item4.put("limit", limit);
        item4.put("complementTag", complementTag);
        item4.put("addupMonths", addupMonths);
        item4.put("addupMethod", addupMethod);
        item4.put("payrelationId",
                GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_payrela_id", 1).get(0));
        item4.put("actTag", actTag);
        tradePayrelation.put("item", item4);

        Map tradeCustomer = new HashMap();
        Map itemCust = new HashMap();
        itemCust.put("xDatatype", "NULL");
        itemCust.put("sustId", custId);
        itemCust.put("custName", newUserInfo.get("certName"));
        if ("07,13,14,15,17,18,21".contains((String) newUserInfo.get("certType"))) {
            itemCust.put("custType", "1");
        }
        else {
            itemCust.put("custType", "0");
        }
        itemCust.put("custState", "0");
        itemCust.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) newUserInfo.get("certType")));
        itemCust.put("psptId", newUserInfo.get("certNum"));
        itemCust.put("openLimit", "0");
        itemCust.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        itemCust.put("cityCode", msg.get("district"));
        itemCust.put("developDepartId", msg.get("recomPersonChannelId"));
        itemCust.put("inDate", startDate);
        itemCust.put("removeTag", "0");
        itemCust.put("rsrvTag1", "2");
        tradeCustomer.put("item", itemCust);
        // tradeOther
        Map tradeOther = new HashMap();
        Map item9 = new HashMap();
        item9.put("xDatatype", xDataType);
        item9.put("rsrvValueCode", rsrvValueCode);
        item9.put("rsrvValue", rsrvValue);
        item9.put("rsrvStr1", rsrvStr1);
        item9.put("rsrvStr2", rsrvStr2);
        item9.put("rsrvStr3", rsrvStr3);
        item9.put("rsrvStr6", rsrvStr6);
        item9.put("rsrvStr7", rsrvStr7);
        item9.put("modifyTag", modifyTag);
        item9.put("startDate", startDate);
        item9.put("endDate", endDate);
        tradeOther.put("item", item9);

        // tradeItem
        List<Map> tradeItem = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        if (new ChangeCodeUtils().isWOPre(exchange.getAppCode())) {
            tradeItem.add(lan.createAttrInfoNoTime("WORK_TRADE_ID", msg.get("orderNo")));
        }
        tradeItem.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", developStaffId));
        tradeItem.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", developDepartId));
        tradeItem.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", ChangeCodeUtils.changeEparchy(msg)));
        tradeItem.add(LanUtils.createTradeItem("EXTRA_INFO", ""));
        tradeItem.add(LanUtils.createTradeItem("PH_NUM", ""));
        tradeItem.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
        tradeItem.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
        if (bindInfo != null && bindInfo.size() > 0) {
            tradeItem.add(LanUtils.createTradeItem("BINDING_SRC", bindInfo.get(0).get("bindSrc")));
        }

        if (null != newUserInfo.get("hopeDate")) {
            tradeItem.add(LanUtils.createTradeItem("BOOK_FLAG", "1"));// 1预约 0非预约
            tradeItem.add(LanUtils.createTradeItem("PRE_START_TIME",
                    GetDateUtils.transDate((String) newUserInfo.get("hopeDate"), 19)));
        }
        else {
            tradeItem.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));// 1预约 0非预约
            tradeItem.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
        }

        tradeItem.add(LanUtils.createTradeItem("USER_PASSWD", userPassword));
        tradeItem.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
        tradeItem.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        tradeItem.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
        tradeItem.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        tradeItem.add(LanUtils.createTradeItem("NEW_PASSWD", userPassword));
        tradeItem.add(LanUtils.createTradeItem("SFGX_2060", "N"));
        tradeItem.add(LanUtils.createTradeItem("GXLX_TANGSZ", ""));

        // 新增行销订单标识以及销售模式类型
        // markingTag:是否行销装备
        if (StringUtils.isNotEmpty((String) msg.get("markingTag"))) {
            tradeItem.add(LanUtils.createTradeItem("MARKING_APP", msg.get("markingTag")));
        }
        // saleModType:销售模式类型
        if (StringUtils.isNotEmpty((String) msg.get("saleModType"))) {
            tradeItem.add(LanUtils.createTradeItem("MarketingChannelType", msg.get("saleModType")));
        }
        if ("0".equals(msg.get("deductionTag"))) {
            tradeItem.add(LanUtils.createTradeItem("FEE_TYPE", "1"));// RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求
        }
        // 河南天宫版行销线上宽带需要进行加实名制限制
        // RHA2018060600033-AOP宽带下单中订单属性需求
        List<Map> woExchInfoList = (List<Map>) newUserInfo.get("woExchInfo");
        if ("cpsb".equals(exchange.getAppCode())) {
            if (woExchInfoList != null && woExchInfoList.size() > 0) {
                for (Map woExchInfo : woExchInfoList) {
                    if ("FAST_SALES".equals(woExchInfo.get("key"))) {
                        tradeItem.add(LanUtils.createTradeItem("FAST_SALES", woExchInfo.get("value")));
                    }
                    if ("REAL_PHOTO_USER_NAME1".equals(woExchInfo.get("key"))) {
                        tradeItem.add(LanUtils.createTradeItem("REAL_PHOTO_USER_NAME1", woExchInfo.get("value")));
                    }
                    if ("photoTagForBroad".equals(woExchInfo.get("key"))) {
                        tradeItem.add(LanUtils.createTradeItem("photoTagForBroad", woExchInfo.get("value")));
                    }
                    if ("MARKING_FXD".equals(woExchInfo.get("key"))) {
                        tradeItem.add(LanUtils.createTradeItem("MARKING_FXD", woExchInfo.get("value")));
                    }
                }
            }
        }
        if (!IsEmptyUtils.isEmpty(woExchInfoList)) {
            for (Map woExchInfo : woExchInfoList) {
                if ("SCHOOL_ADDR_TYPE".equals(woExchInfo.get("key"))) {
                    tradeItem.add(LanUtils.createTradeItem("SCHOOL_ADDR_TYPE", woExchInfo.get("value")));
                }
                if ("STUDENT_ID".equals(woExchInfo.get("key"))) {
                    tradeItem.add(LanUtils.createTradeItem("STUDENT_ID", woExchInfo.get("value")));
                }
            }
        }
        // tradeSubItem
        List<Map> tradeSubItem = new ArrayList<Map>();
        List<Map> tradeSubKeyList = (List<Map>) newUserInfo.get("tradeSubItem");
        if (!IsEmptyUtils.isEmpty(tradeSubKeyList)) {
            for (Map tradeSubKey : tradeSubKeyList) {
                if ("SCHOOL_ADDR_TYPE".equals(tradeSubKey.get("key"))) {
                    tradeSubItem.add(lan.createTradeSubItem("SCHOOL_ADDR_TYPE", tradeSubKey.get("value"), itemId));
                }
                if ("STUDENT_ID".equals(tradeSubKey.get("key"))) {
                    tradeSubItem.add(lan.createTradeSubItem("STUDENT_ID", tradeSubKey.get("value"), itemId));
                }
            }
        }

        // 添加iptv等相关信息
        if (iptvInfos != null && iptvInfos.size() <= 5 && iptvInfos.size() > 0) {
            for (int i = 0; i < iptvInfos.size(); i++) {
                List<Map> IptvServiceAttrs = (List) iptvInfos.get(i).get("IptvServiceAttr");
                for (Map IptvServiceAttr : IptvServiceAttrs) {
                    tradeSubItem.add(lan.createTradeSubItemD(iptvItem.get(i), (String) IptvServiceAttr.get("code"),
                            IptvServiceAttr.get("value"), startDate, endDate));
                }
            }
        }

        if (interTvInfos != null && interTvInfos.size() <= 5 && interTvInfos.size() > 0) {
            for (int i = 0; i < interTvInfos.size(); i++) {
                List<Map> interTvServiceAttrs = (List) interTvInfos.get(i).get("interTvServiceAttr");
                for (Map interTvServiceAttr : interTvServiceAttrs) {
                    tradeSubItem.add(lan.createTradeSubItemD(interTvItem.get(i),
                            (String) interTvServiceAttr.get("code"), interTvServiceAttr.get("value"), startDate,
                            endDate));
                }
            }
        }

        if (StringUtils.isNotEmpty(terminalLyfs)) {// 20181024 terminalLyfs下发在tradeSubItem下---by yangzg
            tradeSubItem.add(lan.createTradeSubItem("TERMINAL_LYFS", terminalLyfs, userId));
        }
        tradeSubItem.add(lan.createTradeSubItemC(itemId, "adEnd", "", startDate, endDate));
        // aDiscntCode 到期资费 bDiscntCode续包年资费
        if ("1".equals(delayType)) {
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", delayYearDiscntId, startDate, endDate));
        }
        if ("2".equals(delayType)) {
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "b", startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        if ("3".equals(delayType)) {
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "t", startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        String cycleNum = "";
        int cycleFee = 0;
        String diacntName;
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", boradDiscntId);
        msg.put("openDiscntId", boradDiscntId);
        List<Map> activityList = n25Dao.queryBrdOrLineDiscntInfo(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + boradDiscntId + "】的资费信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 是否是绝对时间
            String disnctFee = String.valueOf(activityListMap.get("DISNCT_SALEFEE"));// 资费销售金额 单位分
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            diacntName = String.valueOf(activityListMap.get("DISCNT_NAME"));// 资费名称
            // 5:自然年';
            if (!"null".equals(endTag) && "1".equals(endTag) && !"null".equals(resActivityper)
                    && !"null".equals(endUnit) && "3".equals(endUnit)) {
                cycleNum = resActivityper;
            }
            else {
                cycleNum = "12";
            }
            if ("null".equals(disnctFee)) {
                throw new EcAopServerBizException("9999", "在资费信息表中未查询到销售费用DISNCT_SALEFEE信息");
            }
            else {
                cycleFee = Integer.parseInt(disnctFee) / 100;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage() + "获取资费信息失败，资费是：" + boradDiscntId);
        }
        // 这些属性只有包年时候有
        if (!"null".equals(diacntName) && diacntName.contains("年")) {
            if (StringUtils.isNotEmpty(boradDiscntCycle)) {
                cycleNum = boradDiscntCycle;
            }
            if ("0".equals(delayDiscntType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "adStart", startDate, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "fixedHire", cycleNum, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, startDate, endDate));
            }
            else {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "adStart", nextDate, nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "fixedHire", cycleNum, nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, nextDate, endDate));
            }
        }

        // 增加处理IPTV的生效方式节点和到期资费方式的报文 create by zhaok Date 18/1/26
        preIptvDateForTradeSubItem(msg, itemId, iptvInfos, startDate, endDate, nextDate, tradeSubItem, lan);

        tradeSubItem.add(lan.createTradeSubItemC(itemId, "recharge", "", startDate, endDate));
        tradeSubItem.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
        tradeSubItem.add(lan.createTradeSubItem("PREPAY_TAG", "0", userId));
        tradeSubItem.add(lan.createTradeSubItem("ADDRESS_ID", addressCode, userId));// 标准地址编码
        tradeSubItem.add(lan.createTradeSubItem("SHARE_NBR", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("USETYPE", userProperty, userId));
        tradeSubItem.add(lan.createTradeSubItem("MODULE_EXCH_ID", moduleExchId, userId));
        tradeSubItem.add(lan.createTradeSubItem("WOPAY_MONEY", "", userId));
        if (StringUtils.isNotEmpty(serviceArea)) {
            tradeSubItem.add(lan.createTradeSubItem("AREA_CODE", serviceArea, userId));
        }
        else {
            tradeSubItem.add(lan.createTradeSubItem("AREA_CODE", cityCode, userId));
        }
        tradeSubItem.add(lan.createTradeSubItem("ACCESS_TYPE", accessMode, userId));//

        tradeSubItem.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", userId));

        tradeSubItem.add(lan.createTradeSubItem("POINT_EXCH_ID", pointExchId, userId));
        tradeSubItem.add(lan.createTradeSubItem("USER_PASSWD", userPassword, userId));
        tradeSubItem.add(lan.createTradeSubItem("CONNECTNETMODE", netMode, userId));
        tradeSubItem.add(lan.createTradeSubItem("ISWAIL", acceptMode, userId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_SN", machineTypeCode, userId));// 品牌编码
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_MAC", machineMac, userId));
        // RTJ2018060800057-AOP及CBSS支撑天津沃行销宽带新装下发局向字段的需求
        if ("13".equals(msg.get("province"))) {
            tradeSubItem.add(lan.createTradeSubItem("SWITCH_EXCH_ID", exchCode, userId));
        }
        else {
            tradeSubItem.add(lan.createTradeSubItem("SWITCH_EXCH_ID", "", userId));
        }
        tradeSubItem.add(lan.createTradeSubItem("ISWOPAY", "0", userId));
        tradeSubItem.add(lan.createTradeSubItem("KDLX_2061", "N", userId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINALSRC_MODE", TradeManagerUtils.changeTerminalCode(machineType),
                userId));
        tradeSubItem.add(lan.createTradeSubItem("AREA_EXCH_ID", areaExchId, userId));
        tradeSubItem.add(lan.createTradeSubItem("MOFFICE_ID", exchCode, userId));// 局向编码
        tradeSubItem.add(lan.createTradeSubItem("COMMUNIT_ID", "0", userId));
        tradeSubItem.add(lan.createTradeSubItem("USER_CALLING_AREA", cityCode, userId));
        tradeSubItem.add(lan.createTradeSubItem("USER_TYPE_CODE", userType, userId));
        tradeSubItem.add(lan.createTradeSubItem("CB_ACCESS_TYPE", cbAccessType, userId));
        // 　ACCT_NBR此字段由serialNumber改为authAcctId字段下发　create by zhaok Data 2018/4/27
        if ("ussb|sxpr|cqps|cpsb".contains(exchange.getAppCode())) {
            tradeSubItem.add(lan.createTradeSubItem("ACCT_NBR", msg.get("authAcctId"), userId));
        }
        else {
            tradeSubItem.add(lan.createTradeSubItem("ACCT_NBR", msg.get("serialNumber"), userId));
        }
        tradeSubItem.add(lan.createTradeSubItem("COMMPANY_NBR", "", userId));

        tradeSubItem.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", installAddress, userId));
        tradeSubItem.add(lan.createTradeSubItem("TOWN_FLAG", cityMark, userId));
        tradeSubItem.add(lan.createTradeSubItem("POSITION_XY", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_MODEL", machineModelCode, userId));

        tradeSubItem.add(lan.createTradeSubItem("SPEED", newUserInfo.get("speedLevel"), userId));
        tradeSubItem.add(lan.createTradeSubItem("HZGS_0000", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("LOCAL_NET_CODE", eparchyCode, userId));
        tradeSubItem.add(lan.createTradeSubItem("EXPECT_RATE", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("INSTALL_ADDRESS", installAddress, userId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_TYPE", machineProvide, userId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_BRAND", machineBrandCode, userId));

        // 江西需要下发网格四属性chnlCode（服务渠道id）、chnlName（服务渠道名称）、gridCode（网格id）、gridName（网格名称）
        if (StringUtils.isNotEmpty((String) newUserInfo.get("chnlName"))) {
            tradeSubItem.add(lan.createTradeSubItem("CHNL_NAME", newUserInfo.get("chnlName"), userId));
        }
        if (StringUtils.isNotEmpty((String) newUserInfo.get("chnlCode"))) {
            tradeSubItem.add(lan.createTradeSubItem("CHNL_CODE", newUserInfo.get("chnlCode"), userId));
        }
        if (StringUtils.isNotEmpty((String) newUserInfo.get("gridCode"))) {
            tradeSubItem.add(lan.createTradeSubItem("GRID_CODE", newUserInfo.get("gridCode"), userId));
        }
        if (StringUtils.isNotEmpty((String) newUserInfo.get("gridName"))) {
            tradeSubItem.add(lan.createTradeSubItem("GRID_NAME", newUserInfo.get("gridName"), userId));
        }
        // 接口增加需要绑定的手机号码，通过属性，将手机号绑定在冰淇淋套餐的宽带上
        if (bindInfo != null && bindInfo.size() > 0) {
            tradeSubItem.add(lan.createTradeSubItem("BINDING_TYPE", bindInfo.get(0).get("bindType"), userId));
            tradeSubItem.add(lan.createTradeSubItem("BINDING_SERIALNUM", bindInfo.get(0).get("bindSerialNumber"),
                    userId));
            // tradeSubItem.add(lan.createTradeSubItem("BINDING_SRC", bindInfo.get(0).get("bindSrc")));
            tradeSubItem.add(lan.createTradeSubItem("BINDING_USERID", bindInfo.get(0).get("bindUserId"), userId));
        }

        tradeSubItem.add(lan.createTradeSubItem("COMMUNIT_NAME", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("INIT_PASSWD", "0", userId));
        tradeSubItem.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", userId));//
        tradeSubItem.add(lan.createTradeSubItem("ACCT_PASSWD", "123456", userId)); // 密码
        tradeSubItem.add(lan.createTradeSubItemE("LINK_NAME", msg.get("contactPerson"), userId));
        tradeSubItem.add(lan.createTradeSubItemE("OTHERCONTACT", "", userId));
        tradeSubItem.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("contactPhone"), userId));
        if ((Boolean) msg.get("newRetailFlag")) {// 20180615针对新零售修改inModeCode和E_IN_MODE为N
            tradeSubItem.add(lan.createTradeSubItemE("E_IN_MODE", "N", userId));
        }
        if (!IsEmptyUtils.isEmpty(msg.get("gwQuickOpen"))) {
            String endDate1 = GetDateUtils.transDate(
                    GetDateUtils.getSpecifyDateTime(1, 1, GetDateUtils.transDate(GetDateUtils.getDate(), 19), 7), 14);
            tradeSubItem.add(lan.createTradeSubItemAll(0, "GW_QUICK_OPEN", msg.get("gwQuickOpen"), userId,
                    GetDateUtils.getDate(), endDate1));
        }
        List<Map> userExchInfoList = (List<Map>) newUserInfo.get("userExchInfo");
        if (!IsEmptyUtils.isEmpty(userExchInfoList)) {
            for (Map userExchInfo : userExchInfoList) {
                tradeSubItem.add(lan.createTradeSubItem((String) userExchInfo.get("key"), userExchInfo.get("value"),
                        userId));
            }
        }

        // 实名标识问题 by wangmc 20181215
        List<Map> paraList = (List<Map>) msg.get("para");
        String isPrePhotoed = "0";
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map para : paraList) {
                if ("IS_PRE_PHOTOED".equals(para.get("paraId"))) {
                    isPrePhotoed = (String) para.get("paraValue");
                }
            }
        }
        tradeSubItem.add(lan.createTradeSubItemAll("0", "IS_PRE_PHOTOED", isPrePhotoed, userId, startDate, endDate));

        Map trade = new HashMap();
        if ("0".equals(custType)) {
            ext.put("tradeCustomer", tradeCustomer);
        }
        // 收入集团归集处理
        Map tradeRelation = new HashMap();
        if ("1".equals(msg.get("groupFlag"))) {
            Map jtcpMap = new HashMap();
            jtcpMap.put("province", "00" + msg.get("province"));
            jtcpMap.put("groupId", msg.get("groupId"));
            jtcpMap.put("operatorId", msg.get("operatorId"));
            jtcpMap.put("city", ChangeCodeUtils.changeEparchy(msg));
            jtcpMap.put("district", msg.get("district"));
            jtcpMap.put("channelId", msg.get("channelId"));
            jtcpMap.put("channelType", msg.get("channelType"));
            tradeRelation = dealJtcp(exchange, jtcpMap, msg);
            ext.put("tradeRelation", tradeRelation);
        }

        // 拼装融合新装宽带业务
        if (isMixFixed) {
            // 当前成员编号, 下发trade_item时需要数字+1
            String memberNo = (String) msg.get("memberNo");
            if (IsEmptyUtils.isEmpty(memberNo)) {
                throw new EcAopServerBizException("9999", "融合业务新装宽带时需要传入当前成员编号");
            }
            // 目标融合类型
            String mixNetType = (String) msg.get("mixNetType");
            if (IsEmptyUtils.isEmpty(mixNetType)) {
                throw new EcAopServerBizException("9999", "融合业务新装宽带时需要传入目标融合类型");
            }
            // 融合虚拟用户号码
            String mixSerialNumber = (String) msg.get("serialNumberCP");
            if (IsEmptyUtils.isEmpty(mixSerialNumber)) {
                throw new EcAopServerBizException("9999", "融合业务新装宽带时需要传入融合虚拟用户号码");
            }
            // 融合用户ID
            String mixUserId = (String) msg.get("userIdCp");
            if (IsEmptyUtils.isEmpty(mixUserId)) {
                throw new EcAopServerBizException("9999", "融合业务新装宽带时需要传入融合虚拟用户号码");
            }
            // 最后一个成员时ALONE_TCS_COMP_INDEX下发1，CB会区分最后一个下发融合的所有费用和订单，
            // 不是最后一个ALONE_TCS_COMP_INDEX+1下发
            if ("1".equals(msg.get("isLastMember"))) {
                tradeItem.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "1"));
            }
            else {
                tradeItem.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", Integer.valueOf(memberNo) + 1 + ""));
            }

            Map tradeRelationMap = (Map) ext.get("tradeRelation");
            List<Map> relaItemList = new ArrayList<Map>();
            if (!IsEmptyUtils.isEmpty(tradeRelationMap)
                    && !IsEmptyUtils.isEmpty((List<Map>) tradeRelationMap.get("tradeRelation"))) {
                relaItemList = (List<Map>) tradeRelationMap.get("tradeRelation");
            }
            Map relaMap = new HashMap();
            relaMap.put("xDatatype", "NULL");
            relaMap.put("relationTypeCode", mixNetType);
            relaMap.put("idA", mixUserId); // 融合userId
            relaMap.put("idB", userId); // 宽带userId
            relaMap.put("roleCodeA", "0");
            relaMap.put("roleCodeB", "4");
            relaMap.put("startDate", GetDateUtils.getDate());
            relaMap.put("endDate", "20501231235959");
            relaMap.put("modifyTag", "0");
            relaMap.put("serialNumberA", mixSerialNumber); // 融合号码
            relaMap.put("serialNumberB", serialNumber); // 宽带号码
            relaMap.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
            relaItemList.add(relaMap);

            ext.put("tradeRelation", MapUtils.asMap("item", relaItemList));
        }

        ext.put("tradeAcct", tradeAcct);
        ext.put("tradeUser", tradeUser);
        ext.put("tradeRes", tradeRes);
        ext.put("tradePayrelation", tradePayrelation);
        ext.put("tradeOther", tradeOther);
        ext.put("tradeDiscnt", preDiscntData(msg, itemId));
        ext.put("tradeSvc", preTradeSvc(body, exchange, msg, itemId));
        // RQH2018051600002-宽带增加sp处理需求 by wangmc 20180517
        ext.put("tradeSp", preTradeSpData(msg));
        Map tradeItems = new HashMap();
        tradeItems.put("item", tradeItem);
        ext.put("tradeItem", tradeItems);

        // RQH2018051600002-处理ServiceAttr节点传进来的属性 by wangmc 20180530
        perServiceAttr(productInfo, tradeSubItem, ext, startDate, endDate);
        Map tradeSubItems = new HashMap();

        tradeSubItems.put("item", tradeSubItem);

        ext.put("tradeSubItem", tradeSubItems);

        // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
        msg.put("custId", custId);// 新客户时,custId是直接生成的 没有放入msg
        msg.put("apptx", body.get("appxt"));
        DealCertTypeUtils.dealCertType(msg, newUserInfo, ext);

        trade.put("ext", ext);
        trade.put("base", base);
        trade.put("ordersId", tradeId);

        return trade;

    }

    /**
     * 处理集团收入归集
     * @param exchange
     * @param jtcpMap
     * @param msg
     * @return
     */

    private Map dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        LanUtils lan = new LanUtils();
        try {
            lan.preData("ecaop.trades.sell.mob.jtcp.ParametersMapping", jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// 地址
            lan.xml2Json("ecaop.trades.sell.mob.jtcp.template", jtcpExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "集团信息查询失败" + e.getMessage());
        }
        Map retMap = jtcpExchange.getOut().getBody(Map.class);
        List<Map> userList = (List<Map>) retMap.get("useList");
        if (null == userList || userList.isEmpty()) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        String serialNumberA = (String) userList.get(0).get("serialNumber");
        String userIdA = (String) userList.get(0).get("userId");
        if (StringUtils.isEmpty(serialNumberA) || StringUtils.isEmpty(userIdA)) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        List<Map> relaItem = new ArrayList<Map>();
        Map item = new HashMap();
        Map tradeRelation = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("relationAttr", "");
        item.put("relationTypeCode", "2222");
        item.put("idA", userIdA);
        item.put("idB", msg.get("userId"));
        item.put("roleCodeA", "0");
        item.put("roleCodeB", "0");
        item.put("orderno", "");
        item.put("shortCode", "");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "2050-12-31 23:59:59");
        item.put("modifyTag", "0");
        item.put("remark", "");
        item.put("serialNumberA", serialNumberA);
        item.put("serialNumberB", msg.get("serialNumber"));
        item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
        relaItem.add(item);
        tradeRelation.put("item", relaItem);
        return tradeRelation;
    }

    /**
     * 处理ServiceAttr属性信息,拼装在tradeSubItem节点
     * @param productInfo
     * @param tradeSubItem
     * @param ext
     * @param startDate
     * @param endDate
     */
    private void perServiceAttr(List<Map> productInfo, List<Map> tradeSubItem, Map ext, String startDate, String endDate) {
        if (IsEmptyUtils.isEmpty(productInfo)) {
            return;
        }
        if (IsEmptyUtils.isEmpty(tradeSubItem)) {
            tradeSubItem = new ArrayList<Map>();
        }
        // 根据元素类型获取到对应的节点
        Map dataKey = MapUtils.asMap("D", "tradeDiscnt", "S", "tradeSvc", "X", "tradeSp");
        Map codeKey = MapUtils.asMap("D", "discntCode", "S", "serviceId", "X", "spServiceId");
        for (Map product : productInfo) {
            List<Map> packageElement = (List<Map>) product.get("packageElement");
            if (IsEmptyUtils.isEmpty(packageElement)) {
                continue;
            }
            for (Map pacEle : packageElement) {
                // 若该元素存在属性节点,则获取该元素的itemId
                if (!IsEmptyUtils.isEmpty(pacEle.get("serviceAttr"))) {
                    Map tradeKeyData = (Map) ext.get(dataKey.get(pacEle.get("elementType")));
                    if (IsEmptyUtils.isEmpty(tradeKeyData) || IsEmptyUtils.isEmpty(tradeKeyData.get("item"))) {
                        continue;
                    }
                    String getCode = (String) codeKey.get(pacEle.get("elementType"));
                    List<Map> dataItem = (List<Map>) tradeKeyData.get("item");
                    for (Map item : dataItem) {
                        // 如果是该元素,则获取该元素的itemId拼装tradeSubItem节点
                        if (pacEle.get("elementId").equals(String.valueOf(item.get(getCode)))
                                && !IsEmptyUtils.isEmpty(item.get("itemId"))) {
                            Map dataMap = MapUtils.asMap("itemId", item.get("itemId"), "startDate", startDate,
                                    "endDate", endDate);
                            preTradeSubItemByServiceAttr((List<Map>) pacEle.get("serviceAttr"), tradeSubItem, dataMap);
                        }
                    }
                }
            }
        }
    }

    /**
     * 拼装tradeSubItem节点
     * @param serviceAttr
     * @param tradeSubItem
     * @param serviceAttr
     */
    private void preTradeSubItemByServiceAttr(List<Map> serviceAttr, List<Map> tradeSubItem, Map dataMap) {
        LanUtils lan = new LanUtils();
        for (Map attr : serviceAttr) {
            tradeSubItem.add(lan.createTradeSubItemC((String) dataMap.get("itemId"), (String) attr.get("code"),
                    attr.get("value"), (String) dataMap.get("startDate"), (String) dataMap.get("endDate")));
        }
    }

    /**
     * 增加处理IPTV的生效方式节点和到期资费方式的报文
     * @param msg
     * @param itemId
     * @param iptvInfos
     * @param startDate
     * @param endDate
     * @param nextDate
     * @param tradeSubItem
     * @param lan
     */
    private void preIptvDateForTradeSubItem(Map msg, String itemId, List<Map> iptvInfos, String startDate,
            String endDate, String nextDate, List<Map> tradeSubItem, LanUtils lan) {
        if (IsEmptyUtils.isEmpty(iptvInfos)) {
            return;
        }
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        String iptvDelayType = ""; // 到期资费
        String iptvDelayDiscntType = ""; // 生效方式 -0立即生效；-1次月生效
        String iptvDiscntCycle = ""; // // 包年周期
        String iptvDelayYearDiscntId = ""; // 续包年ID
        String iptvDelayDiscntId = ""; // 到期资费ID
        String iptvDiscntId = ""; // iptv资费ID
        // 获取iptv资费节点的item_Id
        List<String> iptvItems = (List<String>) msg.get("iptvItem");
        for (int i = 0; i < iptvInfos.size(); i++) {
            Map iptvInfo = iptvInfos.get(i);
            List<Map> iptvDiscntInfos = (List<Map>) iptvInfo.get("IptvDiscntInfo");

            for (Map iptvDiscntInfo : iptvDiscntInfos) {
                iptvDiscntId = (String) iptvDiscntInfo.get("IptvDiscntId");
                Map iptvDiscntAttr = (Map) iptvDiscntInfo.get("iptvDiscntAttr");
                // 如果有iptv的资费属性，则下发tradeSubItem节点
                if (!IsEmptyUtils.isEmpty(iptvDiscntAttr)) {
                    iptvDelayType = (String) iptvDiscntAttr.get("iptvDelayType");
                    if (IsEmptyUtils.isEmpty(iptvDelayType)) {
                        throw new EcAopServerBizException("9999", "iptv到期资费方式必传");
                    }
                    iptvDelayDiscntType = (String) iptvDiscntAttr.get("iptvDelayDiscntType");
                    if (IsEmptyUtils.isEmpty(iptvDelayDiscntType)) {
                        throw new EcAopServerBizException("9999", "iptv生效方式必传");
                    }
                    iptvDiscntCycle = (String) iptvDiscntAttr.get("iptvDiscntCycle");
                    iptvDelayYearDiscntId = (String) iptvDiscntAttr.get("iptvDelayYearDiscntId");
                    if (IsEmptyUtils.isEmpty(iptvDelayYearDiscntId) && "1".equals(iptvDelayType)) {
                        throw new EcAopServerBizException("9999", "iptv续约包年时续包年资费必传");
                    }
                    iptvDelayDiscntId = (String) iptvDiscntAttr.get("iptvDelayDiscntId");
                }
            }

            // IPTV到期资费方式下发
            itemId = iptvItems.get(i);
            if ("1".equals(iptvDelayType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "iptvExpireDealMode", "a", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", iptvDelayDiscntId, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", iptvDelayYearDiscntId, startDate,
                        endDate));
            }
            else if ("2".equals(iptvDelayType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "iptvExpireDealMode", "b", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", iptvDelayDiscntId, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "b", startDate, endDate));
            }
            else if ("3".equals(iptvDelayType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "iptvExpireDealMode", "t", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", iptvDelayDiscntId, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "b", startDate, endDate));
            }
            // 通过查库获取
            String cycleNum = "";
            int cycleFee = 0;
            String diacntName;
            Map iptvParam = new HashMap();
            iptvParam.put("DISCNT_CODE", iptvDiscntId);
            List<Map> iptvDiscntList = n25Dao.queryBrdOrLineDiscntInfo(iptvParam);
            Map iptvDiscntMap = null;
            if (iptvDiscntList != null && iptvDiscntList.size() > 0) {
                iptvDiscntMap = iptvDiscntList.get(0);
            }
            else {
                throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + iptvDiscntId + "】的资费信息");
            }
            try {
                String resActivityper = String.valueOf(iptvDiscntMap.get("END_OFFSET"));// 失效效偏移时间
                String endTag = String.valueOf(iptvDiscntMap.get("END_ENABLE_TAG"));// 是否是绝对时间
                String disnctFee = String.valueOf(iptvDiscntMap.get("DISNCT_SALEFEE"));// 资费销售金额 单位分
                String endUnit = String.valueOf(iptvDiscntMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
                diacntName = String.valueOf(iptvDiscntMap.get("DISCNT_NAME"));// 资费名称
                // 5:自然年';
                if (!"null".equals(endTag) && "1".equals(endTag) && !"null".equals(resActivityper)
                        && !"null".equals(endUnit) && "3".equals(endUnit)) {
                    cycleNum = resActivityper;
                }
                else {
                    cycleNum = "12";
                }
                if ("null".equals(disnctFee)) {
                    throw new EcAopServerBizException("9999", "在资费信息表中未查询到销售费用DISNCT_SALEFEE信息");
                }
                else {
                    cycleFee = Integer.parseInt(disnctFee) / 100;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException("9999", e.getMessage() + "获取资费信息失败，资费是：" + iptvDiscntId);
            }

            // 这些属性只有包年时候有
            if (!"null".equals(diacntName) && diacntName.contains("年")) {
                if (StringUtils.isNotEmpty(iptvDiscntCycle)) {
                    cycleNum = iptvDiscntCycle;
                }
                if ("0".equals(iptvDelayDiscntType)) {
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "effectMode", iptvDelayDiscntType, startDate,
                            endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "endStart", iptvDelayDiscntType, startDate,
                            endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "adStart", "0", startDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, startDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "iptvFixedHire", cycleNum, startDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "recharge", "", startDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, startDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
                }
                else {
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "effectMode", iptvDelayDiscntType, nextDate,
                            endDate));
                    tradeSubItem.add(lan
                            .createTradeSubItemC(itemId, "endStart", iptvDelayDiscntType, nextDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "adStart", "0", nextDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", nextDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, nextDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "iptvFixedHire", cycleNum, nextDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "recharge", "", nextDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, nextDate, endDate));
                    tradeSubItem.add(lan.createTradeSubItemC(itemId, "callBack", "0", nextDate, endDate));
                }
            }
        }
    }

    /**
     * 拼装tradeSvc节点
     */

    public Map preTradeSvc(Map body, Exchange exchange, Map msg, String svcItemId) {
        try {
            Map svcList = new HashMap();
            List<Map> svc = new ArrayList<Map>();
            svc = (List<Map>) msg.get("svcList");
            List<Map> svList = new ArrayList();
            for (int i = 0; i < svc.size(); i++) {
                Map svcMap = svc.get(i);
                // RXZ2017083000002-外围可以通过传入optType字段来取消某个默认服务的下发 by wangmc 20170928
                String removeKey = "" + svcMap.get("productId") + svcMap.get("packageId") + svcMap.get("serviceId");
                if (!IsEmptyUtils.isEmpty(((Map) msg.get("removeMap")).get(removeKey))) {
                    continue;
                }
                // RXZ2017083000002-end
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("serviceId", svcMap.get("serviceId"));
                item.put("modifyTag", "0");
                item.put("startDate", MapUtils.getDefault(svcMap, "activityStarTime", GetDateUtils.getDate()));
                String endDate = MapUtils.getDefault(svcMap, "activityTime", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (endDate.length() > 14) {
                    item.put("endDate", GetDateUtils.TransDate(endDate, "yyyy-MM-dd HH:mm:ss"));
                }
                else {
                    item.put("endDate", endDate);
                }
                item.put("itemId", MapUtils.getDefault(svcMap, "itemId", svcItemId));
                // 省份下发新的itemId配置开关
                String province = EcAopConfigLoader.getStr("ecaop.global.param.bsop.brd.sinp.svc.province");
                if (province.contains(msg.get("province") + "")) {
                    String id = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
                    item.put("itemId", MapUtils.getDefault(svcMap, "itemId", id));
                }
                else {
                    item.put("itemId", MapUtils.getDefault(svcMap, "itemId", svcItemId));
                }
                item.put("productId", svc.get(i).get("productId"));
                item.put("packageId", svc.get(i).get("packageId"));
                item.put("userIdA", "-1");
                svList.add(item);
            }
            svcList.put("item", svList);
            return svcList;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼接tradeSvc节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradediscnt节点
     */
    public Map preDiscntData(Map msg, String itemId) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> discnt = (List<Map>) msg.get("discntList");
            discnt = NewProductUtils.newDealRepeat(discnt, "discntList");
            System.out.println("=====================disList" + discnt);
            for (int i = 0; i < discnt.size(); i++) {
                // RXZ2017083000002-外围可以通过传入optType字段来取消某个默认资费的下发 by wangmc 20170928
                String removeKey = "" + discnt.get(i).get("productId") + discnt.get(i).get("packageId")
                        + discnt.get(i).get("discntCode");
                if (!IsEmptyUtils.isEmpty(((Map) msg.get("removeMap")).get(removeKey))) {
                    continue;
                }
                // RXZ2017083000002-end
                Map item = new HashMap();
                Map disMap = discnt.get(i);
                item.put("xDatatype", "NULL");
                item.put("id", msg.get("userId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", disMap.get("productId"));
                item.put("packageId", disMap.get("packageId"));
                item.put("discntCode", disMap.get("discntCode"));
                item.put("specTag", "0");
                item.put("relationTypeCode", "");
                // 当enableTag为2时，开始时间下发为此月初
                if (!IsEmptyUtils.isEmpty(disMap.get("startDate"))) {
                    item.put("startDate", disMap.get("startDate"));
                }
                else {
                    item.put("startDate", MapUtils.getDefault(disMap, "activityStarTime", GetDateUtils.getDate()));
                }
                item.put("endDate", MapUtils.getDefault(disMap, "activityTime", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
                // 贵州分支，如果有item_id就取（iptv和interTv的都有item_id），普通资费没有就生成新的。
                if ("85".equals(String.valueOf(msg.get("province")))) {
                    item.put(
                            "itemId",
                            MapUtils.getDefault(disMap, "itemId",
                                    TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID")));
                }
                else {
                    item.put("itemId", MapUtils.getDefault(disMap, "itemId", itemId));
                }
                // 江苏通过配置控制生成itemId
                String province = EcAopConfigLoader.getStr("ecaop.global.param.bsop.brd.sinp.discnt.province");
                if (province.contains(String.valueOf(msg.get("province")))) {
                    String openDiscntId = String.valueOf(msg.get("openDiscntId"));
                    if (openDiscntId.equals(String.valueOf(disMap.get("discntCode")))) {
                        item.put("itemId", MapUtils.getDefault(disMap, "itemId", itemId));
                    }
                    else {
                        item.put(
                                "itemId",
                                MapUtils.getDefault(disMap, "itemId",
                                        TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID")));
                    }
                }
                item.put("modifyTag", "0");
                itemList.add(item);
            }
            tradeDis.put("item", itemList);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    /**
     * RQH2018051600002-宽带增加sp处理需求 by wangmc 20180517
     * @param msg
     * @return
     */
    public Map preTradeSpData(Map msg) {
        Map tardeSp = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> sp = (List<Map>) msg.get("spList");
        for (int i = 0; i < sp.size(); i++) {
            Map item = new HashMap();
            item.put("userId", msg.get("userId"));
            item.put("serialNumber", msg.get("serialNumber"));
            item.put("productId", sp.get(i).get("productId"));
            item.put("packageId", sp.get(i).get("packageId"));
            item.put("partyId", sp.get(i).get("partyId"));
            item.put("spId", sp.get(i).get("spId"));
            item.put("spProductId", sp.get(i).get("spProductId"));
            item.put("firstBuyTime", GetDateUtils.getDate());
            item.put("paySerialNumber", msg.get("serialNumber"));

            item.put("startDate", MapUtils.getDefault(sp.get(i), "activityStarTime", GetDateUtils.getDate()));
            item.put("endDate", MapUtils.getDefault(sp.get(i), "activityTime", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));

            item.put("updateTime", GetDateUtils.getDate());
            item.put("remark", "");
            item.put("modifyTag", "0");
            item.put("payUserId", msg.get("userId"));
            item.put("spServiceId", sp.get(i).get("spServiceId"));
            item.put("userIdA", msg.get("userId"));
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        tardeSp.put("item", itemList);
        return tardeSp;
    }

    /**
     * RXZ2017083000002-外围可以通过传入optType字段来取消某个默认资费的下发 by wangmc 20170928
     * 1.先将带optType字段为1的附加包(packageELement)节点从产品中移除,防止处理产品的公共方法再拼装该节点(虽然会去重)
     */
    private void getRemoveElement(List<Map> productInfo, Map msg) {
        if (!IsEmptyUtils.isEmpty(productInfo)) {
            Map removeMap = new HashMap();
            for (Map product : productInfo) {
                if (!IsEmptyUtils.isEmpty(product.get("packageElement"))) {
                    List<Map> packageELement = new ArrayList<Map>();
                    if (product.get("packageElement") instanceof List) {
                        packageELement = (List<Map>) product.get("packageElement");
                    }
                    else if (product.get("packageElement") instanceof Map) {
                        packageELement.add((Map) product.get("packageElement"));
                    }
                    for (Map pacEle : packageELement) {
                        // 若包元素中有optType,且值为1,则该元素应不下发
                        if (!IsEmptyUtils.isEmpty(pacEle.get("optType"))
                                && "1".equals(pacEle.get("optType").toString())) {
                            removeMap.put(
                                    "" + product.get("productId") + pacEle.get("packageId") + pacEle.get("elementId"),
                                    "remove");
                        }
                    }
                }
            }
            // 将需要取消的资费放入msg中
            msg.put("removeMap", removeMap);
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
