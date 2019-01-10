package com.ailk.ecaop.biz.sub.same;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.ActivityInfoProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.MixOpenUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("CBbrdAddOpenPreCommit")
public class CBBrdAddOpenPreCommitProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.baoa.sUniTradeParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);

        // 对参数进行校验和处理
        checkParams(body);
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        LanUtils lan = new LanUtils();

        // 调用三户信息
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "101001101010001001", "serialNumber",
                msg.get("shareSerialNumber"), "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);// 报下游返回格式有误--已改
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
        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.baoa.sUniTrade.template", exchange);
        Map message = exchange.getOut().getBody(Map.class);
        // 处理返回参数
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

        // 区别新老账户
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String createOrExtendsAcct = (String) newUserInfo.get("createOrExtendsAcct");
        Map acctInfo = new HashMap();
        if ("0".equals(createOrExtendsAcct)) {
            // 此时是新账户状态
            createAcct(acctInfo, newUserInfo);
        }
        else {
            // 老账户直接继承
            List<Map> acctInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("acctInfo");
            acctInfo = acctInfoList.get(0);
        }
        // 获取三户返回的结果
        List<Map> userInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("userInfo");
        List<Map> custInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("custInfo");
        Map userInfo = userInfoList.get(0);
        Map custInfo = custInfoList.get(0);

        String xDatatype = "NULL";
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String province = (String) msg.get("province");
        // 根据产品和资费获取产品信息的时候会用到
        msg.put("PROVINCE_CODE", "00" + province);
        msg.put("EPARCHY_CODE", eparchyCode);
        msg.put("eparchyCode", eparchyCode);// 处理产品的公共方法使用的是小写的 by wangmc 20171023

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
            msg.put("PRODUCT_ID", productId);
        }
        productInfos.add(productInfo);
        String provinceCode = "00" + (String) msg.get("province");
        // 准备用户标示调用产品处理方法
        String id = (String) userInfo.get("userId");
        msg.put("userId", id);
        Map EXT = new HashMap();
        EXT = TradeManagerUtils.newPreProductInfo(productInfos, provinceCode, msg);
        // 处理开户选择的资费信息
        String delayType = "";
        String delayDiscntId = "";
        List discntList = (List) msg.get("discntList");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos) {
            for (Map map : boradDiscntInfos) {
                msg.put("ELEMENT_ID", map.get("boradDiscntId"));
                discntList.add(TradeManagerUtils.preProductInfoByElementIdProductId4CB(msg));
                Map boradDiscntAttr = (Map) map.get("boradDiscntAttr");
                if (boradDiscntAttr != null) {
                    delayType = (String) boradDiscntAttr.get("delayType");
                    delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
                }
            }
        }
        // 处理IPTV信息和互联网信息
        List<Map> iptvInfos = (List<Map>) newUserInfo.get("iptvInfo");
        String iptvProductId = (String) newUserInfo.get("iptvProductId");
        List<Map> interTvInfos = (List<Map>) newUserInfo.get("interTvInfo");
        String interTvProductId = (String) newUserInfo.get("interTvProductId");
        if (iptvInfos != null && StringUtils.isNotEmpty(iptvProductId)) {
            for (Map map : iptvInfos) {
                List<Map> iptvDiscntInfos = (List) map.get("IptvServiceAttr");
                for (Map iptvDiscntInfo : iptvDiscntInfos) {
                    msg.put("ELEMENT_ID", iptvDiscntInfo.get("IptvDiscntId"));
                    msg.put("PRODUCT_ID", iptvProductId);
                    discntList.add(TradeManagerUtils.preProductInfoByElementIdProductId4CB(msg));
                }
            }
        }
        if (interTvInfos != null && StringUtils.isNotEmpty(interTvProductId)) {
            for (Map map : interTvInfos) {
                List<Map> interTvDiscntInfos = (List) map.get("interTvDiscntInfo");
                for (Map interTvDiscntInfo : interTvDiscntInfos) {
                    msg.put("ELEMENT_ID", interTvDiscntInfo.get("interTvDiscntId"));
                    msg.put("PRODUCT_ID", interTvProductId);
                    discntList.add(TradeManagerUtils.preProductInfoByElementIdProductId4CB(msg));
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
        EXT = activity.process(exchange);
        exchange.getIn().setBody(body);
        // 取出itemId
        Map tradeDiscnt = (Map) EXT.get("tradeDiscnt");
        List<Map> discnt = (List<Map>) tradeDiscnt.get("item");
        String itemId = null;
        for (Map m : discnt) {
            if (m.get("itemId") != null) {
                itemId = (String) m.get("itemId");
            }
        }

        // 转换接入方式
        Map inMap = new HashMap();
        inMap.put("ACCESS_TYPE", newUserInfo.get("accessMode"));
        inMap.put("CB_ACCESS_TYPE", newUserInfo.get("cbssAccessMode"));
        inMap.put("PROVINCE_CODE", msg.get("province"));
        inMap.put("NET_TYPE_CODE", "40");
        MixOpenUtils mixOpenUtil = new MixOpenUtils();
        Map accesMap = mixOpenUtil.checkAccessCode(inMap);
        String accessMode = (String) accesMap.get("accessMode");
        String cbAccessType = (String) accesMap.get("cbssAccessMode");

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
        Object machineProvide = null;
        String machineBrandCode = null;
        String machineModelCode = null;
        String machineTypeCode = null;
        String machineType = null;
        String machineMac = null;
        // 处理终端信息
        List machineInfos = (List) msg.get("machineInfo");
        if (null != machineInfos) {
            Map machineInfo = (Map) machineInfos.get(0);
            machineBrandCode = (String) machineInfo.get("machineBrandCode");
            machineModelCode = (String) machineInfo.get("machineModelCode");
            machineTypeCode = (String) machineInfo.get("machineTypeCode");
            machineType = (String) machineInfo.get("machineType");
            machineMac = (String) machineInfo.get("machineMac");
            machineProvide = ChangeCodeUtils.changeMachineProvideToCB(machineInfo.get("machineProvide"));// zzc 20170309
            if (machineType == null) {
                machineType = "1";
            }
        }

        // base
        // String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
        Object tradeId = GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0);
        // String userId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_user_id");
        Object userId = GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_user_id", 2).get(0);
        String usecustId = (String) custInfo.get("custId");
        String custId = (String) custInfo.get("custId");
        String acctId = (String) acctInfo.get("acctId");
        String startDate = GetDateUtils.getDate();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        String acceptDate = GetDateUtils.getDate();
        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String termIp = "132.35.87.198";// 受理终端IP？(缺少字段)
        String execTime = GetDateUtils.getDate();

        Map base = new HashMap();
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", usecustId);
        base.put("acctId", acctId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        base.put("tradeTypeCode", "0010");
        base.put("productId", productId);
        base.put("brandCode", brandCode);
        base.put("userDiffCode", "00");// 用户类型用户类型00:普通用户01:亲友电话虚用户02:对讲机虚用户03:两地同虚用户04:两地通虚用户05:组合产品用户
        base.put("netTypeCode", "0040");
        base.put("serinalNamber", serialNumber);
        base.put("custName", custName);
        base.put("termIp", termIp);
        base.put("eparchyCode", eparchyCode);
        base.put("cityCode", userInfo.get("cityCode"));
        base.put("execTime", execTime);
        base.put("operFee", "0");// 营业费用
        base.put("foregift", "0");// 押金
        base.put("advancePay", "0");// 预付话费
        base.put("feeState", "");// 收费标志
        base.put("feeTime", "");// 收费时间
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

        // tradeUser
        String userPasswd = (String) msg.get("userPasswd");
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
        String developCityCode = (String) msg.get("recomPersonCityCode");

        Map TRADE_USER = new HashMap();
        Map item1 = new HashMap();
        item1.put("xDataType", xDatatype);
        item1.put("usecustId", usecustId);
        item1.put("userPasswd", userPasswd);
        item1.put("userTypeCode", "0");
        item1.put("scoreValue", "0");// 重复数据
        item1.put("creditClass", "0");//
        item1.put("basicCreditValue", "0");//
        item1.put("creditValue", "0");//
        item1.put("acctTag", "0");
        item1.put("prepayTag", "0");
        item1.put("inDate", GetDateUtils.getDate());
        item1.put("openDate", GetDateUtils.getDate());
        item1.put("openMode", "0");
        item1.put("openDepartId", "18a1236");
        item1.put("openStaffId", "baxk1");
        item1.put("inDepartId", "18a1236");
        item1.put("inStaffId", "baxk1");
        item1.put("removeTag", "0");
        item1.put("userStateCodeset", "0");
        item1.put("mputeMonthFee", "0");
        item1.put("developStaffId", developStaffId);
        item1.put("developDate", developDate);
        item1.put("developEparchyCode", "0311");
        item1.put("developCityCode", developCityCode);
        item1.put("developDepartId", developDepartId);
        item1.put("inNetMode", "0");
        item1.put("productTypeCode", productTypeCode);
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
        item3.put("payrelationId", GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_payrela_id", 3).get(0));
        item3.put("actTag", "1");
        TRADE_PAYRELATION.put("item", item3);
        EXT.put("tradePayrelation", TRADE_PAYRELATION);

        // tradeItem
        Map TRADE_ITEM = new HashMap();
        List<Map> items = new ArrayList<Map>();
        if (new ChangeCodeUtils().isWOPre(exchange.getAppCode())) {
            items.add(lan.createAttrInfoNoTime("WORK_TRADE_ID", msg.get("orderNo")));
        }
        // 将每一组参数放入
        items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", developStaffId));
        items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", developDepartId));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType")));
        items.add(LanUtils.createTradeItem("EXTRA_INFO", ""));
        items.add(LanUtils.createTradeItem("PH_NUM", ""));
        items.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
        items.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
        items.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
        items.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
        items.add(LanUtils.createTradeItem("USER_PASSWD", userPasswd));
        items.add(LanUtils.createTradeItem("EXISTS_ACCT", "1"));
        items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        items.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
        items.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        items.add(LanUtils.createTradeItem("NEW_PASSWD", userPasswd));
        items.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        items.add(LanUtils.createTradeItem("PRE_START_TIME", "0"));
        items.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZDH:0:30"));
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
        subitems.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("ADDRESS_ID", "337145300", itemId));// ?
        subitems.add(lan.createTradeSubItem("SHARE_NBR", msg.get("shareSerialNumber"), itemId));
        subitems.add(lan.createTradeSubItem("USETYPE", "1", itemId));
        subitems.add(lan.createTradeSubItem("MODULE_EXCH_ID", newUserInfo.get("moduleExchId"), itemId));
        subitems.add(lan.createTradeSubItem("WOPAY_MONEY", "", itemId));
        subitems.add(lan.createTradeSubItem("AREA_CODE", msg.get("areaCode"), itemId));
        subitems.add(lan.createTradeSubItem("ACCESS_TYPE", accessMode, itemId));
        subitems.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", itemId));
        subitems.add(lan.createTradeSubItem("POINT_EXCH_ID", newUserInfo.get("pointExchId"), itemId));
        subitems.add(lan.createTradeSubItem("USER_PASSWD", userPasswd, itemId));
        subitems.add(lan.createTradeSubItem("CONNECTNETMODE", "1", itemId));
        subitems.add(lan.createTradeSubItem("ISWAIL", "0", itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_SN", machineTypeCode, itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_MAC", machineMac, itemId));
        subitems.add(lan.createTradeSubItem("SWITCH_EXCH_ID", "", itemId));
        subitems.add(lan.createTradeSubItem("ISWOPAY", "0", itemId));
        subitems.add(lan.createTradeSubItem("KDLX_2061", "N", itemId));
        subitems.add(lan.createTradeSubItem("TERMINALSRC_MODE", "A001", itemId));// ?
        subitems.add(lan.createTradeSubItem("AREA_EXCH_ID", newUserInfo.get("areaExchId"), itemId));
        subitems.add(lan.createTradeSubItem("MOFFICE_ID", newUserInfo.get("exchCode"), itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("USER_CALLING_AREA", msg.get("areaCode"), itemId));
        subitems.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", itemId));
        subitems.add(lan.createTradeSubItem("CB_ACCESS_TYPE", cbAccessType, itemId));
        subitems.add(lan.createTradeSubItem("ACCT_NBR", serialNumber, itemId));
        subitems.add(lan.createTradeSubItem("COMMPANY_NBR", "", itemId));
        subitems.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        subitems.add(lan.createTradeSubItem("TOWN_FLAG", "C", itemId));
        subitems.add(lan.createTradeSubItem("POSITION_XY", "", itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_MODEL", machineModelCode, itemId));
        subitems.add(lan.createTradeSubItem("SPEED", newUserInfo.get("speedLevel"), itemId));
        subitems.add(lan.createTradeSubItem("HZGS_0000", "", itemId));
        subitems.add(lan.createTradeSubItem("LOCAL_NET_CODE", "0311", itemId));
        subitems.add(lan.createTradeSubItem("EXPECT_RATE", "", itemId));
        subitems.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_TYPE", machineProvide, itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_BRAND", machineBrandCode, itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_NAME", "", itemId));
        subitems.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        subitems.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", itemId));
        subitems.add(lan.createTradeSubItem("ACCT_PASSWD", "123456", itemId));
        subitems.add(lan.createTradeSubItemE("LINK_NAME", "", itemId));
        subitems.add(lan.createTradeSubItemE("OTHERCONTACT", "", itemId));
        subitems.add(lan.createTradeSubItemE("LINK_PHONE", "", itemId));
        TRADE_SUB_ITEM.put("item", subitems);
        EXT.put("tradeSubItem", TRADE_SUB_ITEM);

        Map TRADE = new HashMap();
        TRADE.put("base", base);
        TRADE.put("ext", EXT);

        return TRADE;

    }

    private void createAcct(Map acctInfo, Map newUserInfo) {
        acctInfo.put("acctId", GetSeqUtil.getSeqFromCb());
        acctInfo.put("payName", newUserInfo.get("certName"));
        acctInfo.put("payModeCode", "0");
        acctInfo.put("prepayTag", "0");
        // newAcctInfo.put("payPasswd", "000000");
        // newAcctInfo.put("payAddress", newUserInfo.get("certAddress"));
        // newAcctInfo.put("payPostCode", "000000");
        // newAcctInfo.put("payContact", checkUserInfo.get("contactPerson"));
        // newAcctInfo.put("payContactPhone", checkUserInfo.get("contactPhone"));

    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
