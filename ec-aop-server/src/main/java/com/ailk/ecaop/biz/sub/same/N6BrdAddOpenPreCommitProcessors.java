package com.ailk.ecaop.biz.sub.same;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.ActivityInfoProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("N6brdAddOpenPreCommit")
public class N6BrdAddOpenPreCommitProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("=2016/7/19N6=");
        Map body = exchange.getIn().getBody(Map.class);

        // 对参数进行校验和处理
        checkParams(body);
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        LanUtils lan = new LanUtils();
        if (!"11|17|18|76|91|97".contains(msg.get("province").toString())) {
            throw new EcAopServerBizException("9999", "路由分发失败");
        }
        // 调用三户接口
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "101001101010001001", "serialNumber",
                msg.get("shareSerialNumber"),
                "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData("ecaop.trade.n6.checkUserParametersMapping", threePartExchange);

        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        threePartExchange.getIn().setBody(body);

        // 准备接口所需数据
        Map preParams = dealParam(threePartExchange);
        Map base = (Map) preParams.get("base");
        Map ext = (Map) preParams.get("ext");

        msg.put("ordersId", msg.get("orderNo"));
        msg.put("operTypeCode", "0");
        msg.put("serviceClassCode", newUserInfo.get("serviceClassCode"));
        msg.put("base", base);
        msg.put("ext", ext);

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用预提交接口
        exchange.setMethodCode("baoa");
        lan.preData("ecaop.masb.baoa.N6.sUniTradeParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.baoa.sUniTrade.template", exchange);

        // 处理返回参数
        Map message = exchange.getOut().getBody(Map.class);
        if (null != message.get("rspInfo")) {
            List<Map> rspInfo = (List<Map>) message.get("rspInfo");
            Map result = dealReturn(rspInfo.get(0), msg);
            if (null == result.get("userName")) {
                String userName = (String) ((Map) msg.get("newUserInfo")).get("certName");
                result.put("userName", userName);
            }
            exchange.getOut().setBody(result);
        }
    }

    private Map checkParams(Map body) {
        Map paramsInfo = new HashMap();
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        if (newUserInfo == null) {
            throw new EcAopServerBizException("9999", "客户资料不能为空");
        }
        checkShareInfo(msg);
        msg.putAll(checkAcctInfo(newUserInfo));
        checkTv(newUserInfo);
        return paramsInfo;
    }

    /**
     * 处理iptv与interTV
     * 
     * @param newUserInfo
     */
    private void checkTv(Map newUserInfo) {
        List<Map> iptvInfo = (List<Map>) newUserInfo.get("iptvInfo");
        List<Map> interTvInfo = (List<Map>) newUserInfo.get("interTvInfo");
        if (iptvInfo != null && interTvInfo != null) {
            throw new EcAopServerBizException("9999", "iptv与interTv只能存在其中一个");
        }
    }

    /**
     * 处理合账信息
     * 
     * @param newUserInfo
     * @return
     */
    private Map checkAcctInfo(Map newUserInfo) {
        Map acctMap = new HashMap();
        String debutySerialNumber = (String) newUserInfo.get("debutySerialNumber");
        String createOrExtendsAcct = (String) newUserInfo.get("createOrExtendsAcct");
        if (null == debutySerialNumber && createOrExtendsAcct == "1") {
            throw new EcAopServerBizException("9999", "继承老帐户时,合帐号码必传");
        }

        String serviceClasscode = (String) newUserInfo.get("serviceClassCode");

        if (MagicNumber.LAN_SERVICE_CLASS.equals(serviceClasscode)) {
            if (null == newUserInfo.get("debutyAreaCode")) {
                throw new EcAopServerBizException("9999", "合帐号码区号不正确");
            }
        }
        return acctMap;
    }

    /**
     * 检查共线号码信息
     * 
     * @param msg
     */
    private void checkShareInfo(Map msg) {
        if ("1".equals(msg.get("isSerial"))) {

            Object shareSerialNumber = msg.get("shareSerialNumber");
            if (null == shareSerialNumber) {
                throw new EcAopServerBizException("9999", "共线时,共线固话号码必传");
            }

            Object shareAreaCode = msg.get("shareAreaCode");
            if (null == shareAreaCode) {
                throw new EcAopServerBizException("9999", "共线时,共线号码区号必传");
            }

            // Map userInfo = null == msg.get("userInfo") ? new HashMap() : (Map) msg.get("userInfo");
            // userInfo.put("relyNumber", shareAreaCode.toString() + shareSerialNumber.toString());
            // msg.put("userInfo", userInfo);
        }
    }

    private Map dealReturn(Map rspInfo, Map msg) {
        String serialNumber = (String) msg.get("serialNumber"); // 宽带统一编码
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        Map ret = new HashMap();
        ret.put("provOrderId", isNull(rspInfo.get("bssOrderId").toString()));
        ret.put("userPasswd", userPasswd);
        if (null != rspInfo.get("provinceOrderInfo")) {
            List<Map> provinceOrderInfoList = (List<Map>) rspInfo.get("provinceOrderInfo");
            for (Map provinceOrderInfo : provinceOrderInfoList) {
                ret.put("debutyAreaCode", provinceOrderInfo.get("areaCode"));
                if (null != provinceOrderInfo.get("preFeeInfoRsp")) {
                    List<Map> feeInfo = (List<Map>) provinceOrderInfo.get("preFeeInfoRsp");
                    List<Map> newFeeInfo = new ArrayList<Map>();
                    if (0 != feeInfo.size()) {
                        for (Map fee : feeInfo) {
                            Map newFee = dealFee(fee);
                            newFeeInfo.add(newFee);
                        }
                        ret.put("feeInfo", newFeeInfo);
                    }
                }
                String totalFee = (String) provinceOrderInfo.get("totalFee");
                ret.put("totalFee", totalFee);
            }
        }

        return ret;
    }

    private Object isNull(String string) {
        return null == string ? "" : string;
    }

    private Map dealFee(Map fee) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) fee.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) fee.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) fee.get("feeTypeName")));
        feeInfo.put("maxRelief", isNull((String) fee.get("maxDerateFee")));
        feeInfo.put("origFee", isNull(fee.get("fee").toString()));
        return feeInfo;
    }

    private Map dealParam(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        Boolean isString = body.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        // 获取三户返回的结果
        List<Map> userInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("userInfo");
        List<Map> custInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("custInfo");
        List<Map> acctInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("acctInfo");

        if (null == userInfoList || 0 == userInfoList.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回用户信息");
        }
        if (null == custInfoList || 0 == custInfoList.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回客户信息");
        }
        if (null == acctInfoList || 0 == acctInfoList.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回账户信息");
        }

        Map userInfo = userInfoList.get(0);
        Map custInfo = custInfoList.get(0);
        // 区别新老账户
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String createOrExtendsAcct = (String) newUserInfo.get("createOrExtendsAcct");
        Map acctInfo = new HashMap();
        if ("0".equals(createOrExtendsAcct)) {
            // 此时是新账户状态
            createAcct(exchange, acctInfo, newUserInfo, msg, eparchyCode);
        }
        else {
            // 老账户直接继承
            acctInfo = acctInfoList.get(0);
        }

        String userId = GetSeqUtil.getSeqFromN6ess(exchange, "USER_ID", eparchyCode);
        String custId = (String) custInfo.get("custId");
        String acctId = (String) acctInfo.get("acctId");

        String xDatatype = "NULL";
        String province = (String) msg.get("province");

        // 准备产品信息
        List<Map> productInfos = (List<Map>) newUserInfo.get("productInfo");
        Map productInfo = productInfos.get(0);

        String productId = (String) productInfo.get("productId");
        String productMode = (String) productInfo.get("productMode");
        if ("0".equals(productMode)) {
            productInfo.put("productMode", "1");
        }
        if ("1".equals(productMode)) {
            productInfo.put("productMode", "0");
        }
        productInfos.add(productInfo);
        String provinceCode = "00" + (String) msg.get("province");
        // 准备用户标示调用产品处理方法
        String id = (String) userInfo.get("userId");
        msg.put("userId", id);
        Map EXT = new HashMap();
        EXT = TradeManagerUtils.preProductInfo(productInfos, provinceCode, msg);
        // 处理开户选择的资费信息
        String delayType = "";
        String delayDiscntId = "";
        String delayDiscntType = "";
        List discntList = (List) msg.get("discntList");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos) {
            for (Map map : boradDiscntInfos) {
                msg.put("elementId", map.get("boradDiscntId"));
                discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
                Map boradDiscntAttr = (Map) map.get("boradDiscntAttr");
                if (boradDiscntAttr != null) {
                    delayType = (String) boradDiscntAttr.get("delayType");
                    delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
                    delayDiscntType = (String) boradDiscntAttr.get("delayDiscntType");
                }
            }
        }
        // 处理IPTV信息和互联网信息
        List<Map> iptvInfos = (List<Map>) newUserInfo.get("iptvInfo");
        List<Map> interTvInfos = (List<Map>) newUserInfo.get("interTvInfo");
        if (iptvInfos != null) {
            for (Map map : iptvInfos) {
                List<Map> iptvDiscntInfos = (List) map.get("IptvServiceAttr");
                for (Map iptvDiscntInfo : iptvDiscntInfos) {
                    msg.put("elementId", iptvDiscntInfo.get("IptvDiscntId"));
                    discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
                }
            }
        }
        if (interTvInfos != null) {
            for (Map map : interTvInfos) {
                List<Map> interTvDiscntInfos = (List) map.get("interTvDiscntInfo");
                for (Map interTvDiscntInfo : interTvDiscntInfos) {
                    msg.put("elementId", interTvDiscntInfo.get("interTvDiscntId"));
                    discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
                }
            }
        }
        // 获取产品信息
        Map tradeProduct = (Map) EXT.get("tradeProduct");
        List<Map> item = (List) tradeProduct.get("item");
        String brandCode = null;
        for (Map m : item) {
            if (m.get("productId") != null) {
                productId = (String) m.get("productId");
            }
            if (m.get("brandCode") != null) {
                brandCode = (String) m.get("brandCode");
            }
        }
        Map tradeProductType = (Map) EXT.get("tradeProductType");
        List<Map> itemT = (List) tradeProductType.get("item");
        String productTypeCode = null;
        for (Map m : itemT) {
            if (m.get("productTypeCode") != null) {
                productTypeCode = (String) m.get("productTypeCode");

            }
        }
        // 调用活动处理方法
        ActivityInfoProcessor activity = new ActivityInfoProcessor();
        List<Map> activityInfo = (List<Map>) ((Map) msg.get("newUserInfo")).get("activityInfo");
        if (activityInfo != null && activityInfo.size() != 0) {
            EXT = activity.process(exchange);
        }
        exchange.getIn().setBody(body);
        // 取出itemId
        Map tradeDiscnt = (Map) EXT.get("tradeDiscnt");
        List<Map> discnt = (List<Map>) tradeDiscnt.get("item");
        System.out.println("N6add=discnt=" + discnt);
        String itemId = null;
        for (Map m : discnt) {
            if (m.get("itemId") != null) {
                itemId = (String) m.get("itemId");
            }
        }

        // 转换接入方式
        // Map inMap = new HashMap();
        // inMap.put("ACCESS_TYPE", newUserInfo.get("accessMode"));
        // inMap.put("CB_ACCESS_TYPE", newUserInfo.get("cbssAccessMode"));
        // inMap.put("PROVINCE_CODE", msg.get("province"));
        // inMap.put("NET_TYPE_CODE", "40");
        // MixOpenUtils mixOpenUtil = new MixOpenUtils();
        // Map accesMap = mixOpenUtil.checkAccessCode(inMap);
        // String cbAccessType = (String) accesMap.get("cbssAccessMode");

        // 处理默认信息
        String userType = (String) newUserInfo.get("userType");
        if (userType == null) {
            userType = "1";
        }
        String acceptMode = (String) newUserInfo.get("acceptMode");
        if (acceptMode == null) {
            acceptMode = "1";
        }
        String userProperty = (String) newUserInfo.get("userProperty");
        if (userProperty == null) {
            userProperty = "1";
        }
        String cityMark = (String) newUserInfo.get("cityMark");
        if (cityMark == null) {
            cityMark = "1";
        }
        String payMode = (String) newUserInfo.get("payMode");
        if (payMode == null) {
            payMode = "1";
        }
        String netMode = (String) newUserInfo.get("netMode");
        if (netMode == null) {
            netMode = "1";
        }

        // 处理终端信息
        List machineInfos = (List) msg.get("machineInfo");
        if (machineInfos != null && machineInfos.size() != 0) {
            Map machineInfo = (Map) machineInfos.get(0);
            String machineBrandCode = (String) machineInfo.get("machineBrandCode");
            String machineModelCode = (String) machineInfo.get("machineModelCode");
            String machineTypeCode = (String) machineInfo.get("machineTypeCode");
            String machineType = (String) machineInfo.get("machineType");
            String machineMac = (String) machineInfo.get("machineMac");
            if (machineType == null) {
                machineType = "1";
            }
            String machineProvide = (String) machineInfo.get("machineProvide");
            if (machineProvide == null) {
                machineProvide = "1";
            }
        }

        // 准备基本参数
        String tradeId = GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode);
        exchange.getIn().setBody(body);
        String startDate = GetDateUtils.getDate();
        String endDate = "20501231235959";
        String acceptDate = GetDateUtils.getDate();
        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String termIp = "132.35.87.198";// 受理终端IP？(缺少字段)
        String execTime = GetDateUtils.getDate();
        String productSpec = null;
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        String cityCode = (String) msg.get("city");
        String operatorId = (String) msg.get("operatorId");
        String channelId = (String) msg.get("channelId");

        Map base = new HashMap();
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", custId);
        base.put("acctId", acctId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", "E");
        base.put("tradeTypeCode", "0010");
        base.put("productId", productId);
        base.put("brandCode", brandCode);
        base.put("userDiffCode", "00");// 用户类型用户类型00:普通用户01:亲友电话虚用户02:对讲机虚用户03:两地同虚用户04:两地通虚用户05:组合产品用户
        base.put("netTypeCode", "0040");
        base.put("serinalNamber", serialNumber);
        base.put("custName", custName);
        base.put("termIp", termIp);
        base.put("eparchyCode", eparchyCode);
        // base.put("cityCode", cityCode);// ?客户归属业务区
        base.put("execTime", execTime);
        base.put("operFee", "0");// 营业费用
        base.put("foregift", "0");// 押金
        base.put("advancePay", "0");// 预付话费
        base.put("feeState", "");// 收费标志
        base.put("actorName", "");// 担保人姓名
        base.put("actorCertTypeId", "");// 经办人证件类型
        base.put("actorPhone", "");// 担保人证件号码
        base.put("actorCertNum", "");// 担保人证件号码
        base.put("contact", "");// 联系人
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "0");
        base.put("chkTag", "0");

        // tradeAcct
        Map TRADE_ACCT = new HashMap();
        Map item0 = new HashMap();
        item0.put("xDatatype", xDatatype);
        item0.put("acctId", acctId);
        item0.put("payName", custName);
        item0.put("payModeCode", "0");
        item0.put("scoreValue", "0");
        item0.put("creditClassId", "0");
        item0.put("basicCreditValue", "0");
        item0.put("creditValue", "0");
        item0.put("creditControlId", "0");
        item0.put("debutyUserId", "");
        item0.put("debutyCode", "");
        item0.put("removeTag", "0");
        item0.put("cityCode", cityCode);
        item0.put("openDate", GetDateUtils.getDate());
        TRADE_ACCT.put("item", item0);
        EXT.put("tradeAcct", TRADE_ACCT);

        // tradeUser
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
        String developCityCode = (String) msg.get("recomPersonCityCode");

        Map TRADE_USER = new HashMap();
        Map item1 = new HashMap();
        item1.put("xDatatype", xDatatype);
        item1.put("usecustId", custId);
        item1.put("userPasswd", userPasswd);
        item1.put("userTypeCode", "2");
        item1.put("scoreValue", "0");// 重复数据
        item1.put("creditClass", "0");//
        item1.put("basicCreditValue", "0");//
        item1.put("creditValue", "0");//
        item1.put("acctTag", "0");
        item1.put("prepayTag", "0");
        item1.put("inDate", GetDateUtils.getDate());
        item1.put("openDate", GetDateUtils.getDate());
        item1.put("openMode", "0");
        item1.put("openDepartId", channelId);
        item1.put("openStaffId", operatorId);
        item1.put("inDepartId", channelId);
        item1.put("inStaffId", operatorId);
        item1.put("removeTag", "0");
        item1.put("userStateCodeset", "0");
        item1.put("mputeMonthFee", "0");
        item1.put("developStaffId", developStaffId);
        item1.put("developDate", developDate);
        item1.put("developEparchyCode", eparchyCode);
        item1.put("developCityCode", cityCode);
        item1.put("developDepartId", developDepartId);
        item1.put("inNetMode", "0");
        item1.put("productTypeCode", productTypeCode);
        item1.put("cityCode", cityCode);
        TRADE_USER.put("item", item1);
        EXT.put("tradeUser", TRADE_USER);

        // tradeRes
        Map TRADE_RES = new HashMap();
        String resCode = (String) msg.get("serialNumber");
        Map item2 = new HashMap();
        item2.put("xDataType", xDatatype);
        item2.put("reTypeCode", "0");
        item2.put("resCode", resCode);
        item2.put("modifyTag", "0");
        item2.put("startDate", startDate);
        item2.put("endDate", endDate);
        TRADE_RES.put("item", item2);
        EXT.put("tradeRes", TRADE_RES);

        // tradePayrelation

        Map TRADE_PAYRELATION = new HashMap();

        Map item3 = new HashMap();
        item3.put("userId", userId);
        item3.put("acctId", acctId);
        item3.put("payitemCode", "-1");
        item3.put("acctPriority", "0");
        item3.put("userPriority", "0");
        item3.put("bindType", "1");
        item3.put("defaultTag", "1");
        item3.put("limitType", "0");
        item3.put("limit", "0");
        item3.put("complementTag", "0");
        item3.put("addupMonths", "0");
        item3.put("addupMethod", "0");
        item3.put("payrelationId", GetSeqUtil.getSeqFromN6ess(exchange, "ITEM_ID", eparchyCode));
        item3.put("actTag", "1");
        TRADE_PAYRELATION.put("item", item3);
        EXT.put("tradePayrelation", TRADE_PAYRELATION);

        // tradeItem
        Map TRADE_ITEM = new HashMap();
        List<Map> items = new ArrayList<Map>();
        // 将每一组参数放入
        items.add(lan.createAttrInfoNoTime("MARKETING_MODE", ""));
        items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", developStaffId));
        items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", developDepartId));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", (String) msg.get("channelType")));
        items.add(LanUtils.createTradeItem("PH_NUM", ""));
        items.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        items.add(LanUtils.createTradeItem("USER_ACCPDATE", GetDateUtils.getDate()));
        items.add(LanUtils.createTradeItem("USER_PASSWD", (String) msg.get("userPasswd")));
        items.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        items.add(LanUtils.createTradeItem("NEW_PASSWD", (String) msg.get("userPasswd")));
        items.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
        items.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        items.add(LanUtils.createTradeItem("GXLX_TANGSZ", "PTDH:0:30"));

        TRADE_ITEM.put("item", items);
        EXT.put("tradeItem", TRADE_ITEM);

        // tradeSubItem
        Map TRADE_SUB_ITEM = new HashMap();
        List<Map> subitems = new ArrayList<Map>();
        // 下发IPTV信息
        if (iptvInfos != null && iptvInfos.size() <= 5 && iptvInfos.size() > 0) {
            for (Map iptvInfo : iptvInfos) {
                List<Map> IptvServiceAttrs = (List) iptvInfo.get("IptvServiceAttr");
                for (Map IptvServiceAttr : IptvServiceAttrs) {
                    subitems.add(lan.createTradeSubItemD(itemId, (String) IptvServiceAttr.get("code"),
                            IptvServiceAttr.get("value"), startDate, endDate));
                }
            }
        }
        // 下发互联网电视信息
        if (interTvInfos != null && interTvInfos.size() <= 5 && interTvInfos.size() > 0) {
            for (Map interTvInfo : interTvInfos) {
                List<Map> interTvServiceAttrs = (List<Map>) interTvInfo.get("interTvServiceAttr");
                for (Map interTvServiceAttr : interTvServiceAttrs) {
                    subitems.add(lan.createTradeSubItemD(itemId, (String) interTvServiceAttr.get("code"),
                            interTvServiceAttr.get("value"), startDate, endDate));
                }
            }
        }
        subitems.add(lan.createTradeSubItemC(itemId, "adEnd", "", startDate, endDate));
        if ("1".equals(delayType)) {
            subitems.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "bDiscntCode", delayDiscntId, startDate, endDate));
        }
        if ("2".equals(delayType)) {
            subitems.add(lan.createTradeSubItemC(itemId, "expireDealMode", "b", startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        if ("3".equals(delayType)) {
            subitems.add(lan.createTradeSubItemC(itemId, "expireDealMode", "t", startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }

        String moduleExchId = (String) newUserInfo.get("moduleExchId");
        if (moduleExchId == null) {
            moduleExchId = "";
        }
        String addressCode = (String) newUserInfo.get("addressCode");
        if (addressCode == null) {
            addressCode = "";
        }
        String accessMode = (String) newUserInfo.get("accessMode");
        if (accessMode == null) {
            accessMode = "";
        }
        String exchCode = (String) newUserInfo.get("exchCode");
        if (exchCode == null) {
            exchCode = "";
        }

        subitems.add(lan.createTradeSubItem("iswait", "N", itemId));
        subitems.add(lan.createTradeSubItem("ADDRESS_ID", addressCode, itemId));
        subitems.add(lan.createTradeSubItem("SHARE_NBR", msg.get("shareSerialNumber"), itemId));
        subitems.add(lan.createTradeSubItem("CUST_MANAGER_ID", "", itemId));
        subitems.add(lan.createTradeSubItem("AREA_CODE", cityCode, itemId));
        subitems.add(lan.createTradeSubItem("ENTERNET_MODE", "5", itemId));
        subitems.add(lan.createTradeSubItem("PARENT_MOFFICE", "10882", itemId));
        subitems.add(lan.createTradeSubItem("USER_PASSWD", msg.get("userPasswd"), itemId));
        subitems.add(lan.createTradeSubItem("PROD_PROP", "I9", itemId));
        subitems.add(lan.createTradeSubItem("MOFFICE_ID", exchCode, itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("RES_TYPE", "1", itemId));
        subitems.add(lan.createTradeSubItem("NGN_MARK", "N", itemId));
        subitems.add(lan.createTradeSubItem("USER_CALLING_AREA", cityCode, itemId));
        subitems.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", itemId));
        subitems.add(lan.createTradeSubItem("CUST_MANAGER_NAME", "", itemId));
        subitems.add(lan.createTradeSubItem("SERVICE_CODE", "", itemId));
        subitems.add(lan.createTradeSubItem("SHARE_USER_ID", "", itemId));
        subitems.add(lan.createTradeSubItem("ACCT_NBR", "", itemId));
        subitems.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", "", itemId));
        subitems.add(lan.createTradeSubItem("TOWN_FLAG", "A", itemId));
        subitems.add(lan.createTradeSubItem("RATE", "4096", itemId));
        subitems.add(lan.createTradeSubItem("expect_rate", "", itemId));
        subitems.add(lan.createTradeSubItem("MONTH_RENT", "01", itemId));
        subitems.add(lan.createTradeSubItem("LOCAL_NET_CODE", "0371", itemId));
        subitems.add(lan.createTradeSubItem("LINK_PHONE", "18339934613", itemId));
        subitems.add(lan.createTradeSubItem("CSTOP_FLAG", "01", itemId));
        subitems.add(lan.createTradeSubItem("INSTALL_ADDRESS", "", itemId));
        subitems.add(lan.createTradeSubItem("SERIAL_NUMBER_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_NAME", "", itemId));
        subitems.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        subitems.add(lan.createTradeSubItem("LINK_NAME", "", itemId));
        subitems.add(lan.createTradeSubItem("ACCT_PASSWD", "", itemId));

        subitems.add(lan.createTradeSubItemE("DepartId", "", itemId));
        subitems.add(lan.createTradeSubItemE("MaxNum", "", itemId));
        subitems.add(lan.createTradeSubItemE("StaffId", "243567876543", itemId));
        subitems.add(lan.createTradeSubItemE("BookDateDesc", "", itemId));
        subitems.add(lan.createTradeSubItemE("BookContactPhone", "", itemId));
        subitems.add(lan.createTradeSubItemE("DepartName", "", itemId));
        subitems.add(lan.createTradeSubItemE("NO_BOOK_REASON", "", itemId));
        subitems.add(lan.createTradeSubItemE("BOOK_FLAG", "0", itemId));
        subitems.add(lan.createTradeSubItemE("StaffName", "", itemId));
        subitems.add(lan.createTradeSubItemE("CurrNum", "", itemId));
        subitems.add(lan.createTradeSubItemE("BokInstId", "", itemId));
        subitems.add(lan.createTradeSubItemE("Date", "", itemId));
        subitems.add(lan.createTradeSubItemE("PRE_START_TIME", "2016-05-05 10:26:59", itemId));
        subitems.add(lan.createTradeSubItemE("SCHE_ID", "1234567890", itemId));

        TRADE_SUB_ITEM.put("item", subitems);
        EXT.put("tradeSubItem", TRADE_SUB_ITEM);

        Map TRADE = new HashMap();
        TRADE.put("base", base);
        TRADE.put("ext", EXT);

        return TRADE;

    }

    private void createAcct(Exchange exchange, Map acctInfo, Map newUserInfo, Map msg, String eparchyCode) {
        Map body = exchange.getIn().getBody(Map.class);
        acctInfo.put("acctId", GetSeqUtil.getSeqFromN6ess(exchange, "ACCT_ID", eparchyCode));
        exchange.getIn().setBody(body);
        acctInfo.put("payName", newUserInfo.get("certName"));
        acctInfo.put("payModeCode", "0");
        acctInfo.put("prepayTag", "0");
        // newAcctInfo.put("payPasswd", "000000");
        // newAcctInfo.put("payAddress", newUserInfo.get("certAddress"));
        // newAcctInfo.put("payPostCode", "000000");
        // newAcctInfo.put("payContact", checkUserInfo.get("contactPerson"));
        // newAcctInfo.put("payContactPhone", checkUserInfo.get("contactPhone"));

    }
}
