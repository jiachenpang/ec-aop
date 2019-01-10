package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;

/**
 * 融合关系变更预提交接口
 * @author wangmc
 * @date 2018-09-25
 */
@EcRocTag("mixMemberChgAppprocessor")
public class MixMemberChgAppprocessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    // 三户接口,获取id,预提交
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping", "ecaop.trades.sccc.cancelPre.paramtersmapping" };

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        // 设置默认的开始时间和结束时间
        msg.put("acceptDate", GetDateUtils.getDate());// 业务办理时间
        msg.put("chgStartDate", GetDateUtils.getNextMonthFirstDayFormat());
        msg.put("chgEndDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);

        // 校验请求参数,并返回是否为虚拟用户的标识
        boolean isMixNum = checkInParam(msg);

        // 1.调用三户接口
        Map threePartRet = checkThreePart(exchange, msg, isMixNum);

        // 2.处理产品信息
        Map ext = dealProductInfo(msg, threePartRet);

        // 3.处理其他的ext节点信息
        dealOtherData(msg, threePartRet, ext, isMixNum);
        msg.put("ext", ext);

        // 4.处理base节点
        Object tradeId = preBaseData(exchange, msg, threePartRet, ext, isMixNum);

        // 5.调用预提交接口
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[2], preSubmitExchange);
        try {
            CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用cBSS预提交失败:" + e.getMessage());
        }

        // 6.处理返回
        Map outMap = preSubmitExchange.getOut().getBody(Map.class);
        dealReturn(msg, outMap, exchange, isMixNum, tradeId);
    }

    /**
     * 处理返回
     * @param msg
     * @param outMap
     * @param exchange
     * @param isMixNum
     * @param tradeId
     */
    private void dealReturn(Map msg, Map outMap, Exchange exchange, boolean isMixNum, Object tradeId) {
        Map retMap = new HashMap();
        List<Map> rspInfo = (List<Map>) outMap.get("rspInfo");
        List<Map> feeList = new ArrayList<Map>();

        int totalFee = 0;
        // 费用计算
        for (Map rspMap : rspInfo) {
            // 虚拟成员订单ID
            retMap.put("bssOrderId", rspMap.get("bssOrderId"));
            // 子成员订单ID,先默认为虚拟成员的,对子订单信息判断,若不是虚拟成员预提交,再获取与虚拟成员订单不同的订单号
            retMap.put("provOrderId", rspMap.get("provOrderId"));
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                // 费用计算
                for (Map provinceOrder : provinceOrderInfo) {
                    if (tradeId.equals(provinceOrder.get("subProvinceOrderId"))) {
                        retMap.put("provOrderId", provinceOrder.get("subProvinceOrderId"));
                    }
                    totalFee = totalFee + Integer.valueOf(String.valueOf(provinceOrder.get("totalFee")));
                    List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                        continue;
                    }
                    feeList.addAll(dealFee(preFeeInfoRsp));
                }

            }
        }
        retMap.put("totalFee", totalFee + "");
        retMap.put("number", isMixNum ? msg.get("serialNumberCP") : msg.get("serialNumber"));
        if (!IsEmptyUtils.isEmpty(feeList)) {
            retMap.put("feeInfo", feeList);
        }
        Message out = new DefaultMessage();
        out.setBody(retMap);
        exchange.setOut(out);
    }

    /**
     * 修改费用节点key
     * @param feeList
     * @return
     */
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

    /**
     * 处理base节点
     * @param msg
     * @param threePartRet
     * @param ext
     * @param isMixNum
     */
    private Object preBaseData(Exchange exchange, Map msg, Map threePartRet, Map ext, boolean isMixNum) {
        // 获取三户信息拼base
        Map userInfo = (Map) threePartRet.get("userInfo");
        Map custInfo = (Map) threePartRet.get("custInfo");
        Map acctInfo = (Map) threePartRet.get("acctInfo");

        Map base = MapUtils.asMap("operFee", "0", "foregift", "0", "advancePay", "0", "feeState", "", "feeStaffId", "",
                "cancelTag", "0", "checktypeCode", "", "chkTag", "0", "actorName", "", "actorCertTypeId", "",
                "actorPhone", "", "actorCertNum", "", "contact", "", "contactAddress", "", "contactPhone", "",
                "remark", "");
        Object tradeId = GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id", 1).get(0);
        Object subscribeId = "";
        // 成员预提交的tradeId需要单独生成,虚拟预提交的是同一个
        if (isMixNum) {
            subscribeId = tradeId;
        }
        else {
            subscribeId = msg.get("bssOrderId");
        }
        // 放入开户工号
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("tradeDepartId", msg.get("channelId"));
        base.put("subscribeId", subscribeId);// 虚拟用户预提交订单号
        base.put("tradeId", tradeId);
        base.put("startDate", msg.get("chgStartDate"));
        base.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        base.put("acceptDate", msg.get("acceptDate"));
        base.put("netTypeCode", getNetTypeCode(msg));
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        // 虚拟的下发0110，成员下发0340
        base.put("tradeTypeCode", isMixNum ? "0110" : "0340");
        base.put("productId", msg.get("mainProductId"));
        // 先给虚拟预提交一个写死的brandCode
        base.put("brandCode", isMixNum ? "COMP" : ext.get("brandCode"));
        base.put("userId", userInfo.get("userId"));
        base.put("custId", custInfo.get("custId"));
        base.put("usecustId", custInfo.get("custId"));
        base.put("acctId", acctInfo.get("acctId"));
        base.put("userDiffCode", "00");
        base.put("serinalNamber", isMixNum ? msg.get("serialNumberCP") : msg.get("serialNumber"));
        base.put("custName", custInfo.get("custName"));
        base.put("termIp", "0:0:0:0:0:0:0:1");
        base.put("eparchyCode", userInfo.get("eparchyCode"));
        base.put("cityCode", userInfo.get("cityCode"));
        base.put("execTime", msg.get("acceptDate"));

        msg.put("ordersId", subscribeId);
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");// 订单受理
        msg.put("base", base);
        return tradeId;
    }

    /**
     * 处理base节点的netTypeCode字段
     * @param msg
     * @return 移网-0050;固网-0030;宽带-0040;虚拟:00CP
     */
    private String getNetTypeCode(Map msg) {
        // 1-手机, 2-固话,3-宽带,4-虚拟
        String memberType = String.valueOf(msg.get("memberType"));
        return "1".equals(memberType) ? "0050" : "2".equals(memberType) ? "0030" : "3".equals(memberType) ? "0040"
                : "4".equals(memberType) ? "00CP" : "";
    }

    /**
     * 校验请求参数
     * @param msg
     */
    private boolean checkInParam(Map msg) {
        boolean isMixNum = "4".equals(msg.get("memberType"));

        if (!isMixNum) {
            // 成员用户时,需要传入虚拟成员userId、融合虚拟用户预提交订单、成员号码
            String[] checkParas = new String[] { "bssOrderId", "serialNumber" };
            String[] errorDetails = new String[] { "融合虚拟用户预提交订单", "成员号码" };
            for (int i = 0; i < checkParas.length; i++) {
                if (IsEmptyUtils.isEmpty(msg.get(checkParas[i]))) {
                    throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "成员预提交时:" + errorDetails[i] + "必填");
                }
            }
        }
        // 校验产品节点
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "产品节点必传");
        }
        // 校验是否传入了主产品ID
        for (Map product : productInfo) {
            if ("0".equals(product.get("productMode"))) {
                msg.put("mainProductId", product.get("productId"));
                return isMixNum;
            }
        }
        throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "融合变更主产品必传");
    }

    /**
     * 3.处理其他的ext节点信息
     * @param msg
     * @param threePartRet
     * @param ext
     * @param isMixNum
     */
    private void dealOtherData(Map msg, Map threePartRet, Map ext, boolean isMixNum) {
        if (isMixNum) {
            // 3.1虚拟成员拼装tradeUser节点
            preTradeUser(msg, threePartRet, ext);
        }
        else {
            // 3.2非虚拟成员拼装tradeRelation节点
            preTradeRelation(msg, threePartRet, ext);
        }
        // 3.3处理tradeOther节点
        preTradeOther(msg, threePartRet, ext, isMixNum);
        // 3.4处理tradeItem
        preTradeItem(msg, threePartRet, ext, isMixNum);
        // 3.5处理tradeSubItem
        preTradeSubItem(msg, threePartRet, ext, isMixNum);
    }

    /**
     * 3.5 处理tradeSubItem
     * @param msg
     * @param threePartRet
     * @param ext
     * @param isMixNum
     */
    private void preTradeSubItem(Map msg, Map threePartRet, Map ext, boolean isMixNum) {
        String[] codes = new String[] { "PHONE_VERIFIED", "LINK_NAME", "LINK_PHONE" };
        String[] values = new String[] { "0", (String) msg.get("contactPerson"), (String) msg.get("contactPhone") };
        List<Map> tradeSubItemList = new ArrayList<Map>();
        for (int i = 0; i < codes.length; i++) {
            if (isMixNum) {
                // 融合的不带开始结束时间
                tradeSubItemList.add(lan.createTradeSubItemB2((String) msg.get("userId"), codes[i], values[i]));
            }
            else {
                tradeSubItemList.add(LanUtils.createTradeSubItemAll("0", codes[i], values[i], msg.get("userId"),
                        msg.get("chgStartDate"), msg.get("chgEndDate")));
            }
        }
        // 宽带时,添加速率处理
        if ("3".equals(msg.get("memberType"))) {
            tradeSubItemList.add(LanUtils.createTradeSubItemAll("0", "SPEED", msg.get("speedLevel"), msg.get("userId"),
                    msg.get("chgStartDate"), msg.get("chgEndDate")));
        }
        ext.put("tradeSubItem", MapUtils.asMap("item", tradeSubItemList));
    }

    /**
     * 3.4处理tradeItem
     * @param msg
     * @param threePartRet
     * @param ext
     * @param isMixNum
     */
    private void preTradeItem(Map msg, Map threePartRet, Map ext, boolean isMixNum) {
        List<Map> tradeItemList = new ArrayList<Map>();
        tradeItemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));

        // 是最后一个成员时,index为1,其余为index+1
        String preSubIndex = "1";
        if (!"1".equals(msg.get("isLastMember"))) {
            preSubIndex = String.valueOf(Integer.parseInt(String.valueOf(msg.get("memberNo"))) + 1);
        }
        tradeItemList.add(lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", preSubIndex));
        // 虚拟成员拼装发展人信息
        if (isMixNum) {
            if (!IsEmptyUtils.isEmpty((Map) msg.get("recomInfo"))) {
                Map recomInfo = (Map) msg.get("recomInfo");
                tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", recomInfo.get("recomPersonId")));
                tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", recomInfo.get("recomDepartId")));
            }
        }
        else {
            tradeItemList.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "4"));// 不知道是啥东西,先写死
            tradeItemList.add(LanUtils.createTradeItem("MAIN_USER_TAG", ""));// 也不知道是啥,先写死
            tradeItemList.add(LanUtils.createTradeItem("OPER_CODE", "3"));// 移网固网都是3,不知道啥情况
            tradeItemList.add(LanUtils.createTradeItem("NEW_PRODUCT_ID", msg.get("mainProductId")));// 订购的新主产品
            tradeItemList.add(LanUtils.createTradeItem("PRODUCT_TYPE_CODE", ext.get("productTypeCode")));// 订购的主产品类型编码
            tradeItemList.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));// 好像是看是不是订购,都是,写死0
            tradeItemList.add(LanUtils.createTradeItem("NEW_BRAND_CODE", ext.get("brandCode")));// 订购的主产品的品牌
            tradeItemList.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", ""));// TODO:虚拟产品ID暂时获取不到
            tradeItemList.add(LanUtils.createTradeItem("NET_TYPE_CODE", getNetTypeCode(msg).substring(2)));
        }
        ext.put("tradeItem", MapUtils.asMap("item", tradeItemList));
    }

    /**
     * 3.3处理tradeOther节点
     * @param msg
     * @param threePartRet
     * @param ext
     * @param isMixNum
     */
    private void preTradeOther(Map msg, Map threePartRet, Map ext, boolean isMixNum) {
        List<Map> otherList = new ArrayList<Map>();
        Map userInfo = (Map) threePartRet.get("userInfo");
        Map otherItem = MapUtils.asMap("xDatatype", "NULL", "rsrvValue", msg.get("userId"), "modifyTag", "1");
        otherItem.put("rsrvValueCode", "NEXP");// 预留资料编码先写死
        otherItem.put("rsrvStr1", userInfo.get("productId"));// 退订的主产品
        otherItem.put("rsrvStr2", "00");
        otherItem.put("rsrvStr3", "-9");
        otherItem.put("rsrvStr4", userInfo.get("productTypeCode"));
        otherItem.put("rsrvStr5", userInfo.get("productTypeCode"));
        otherItem.put("rsrvStr6", "-1");
        otherItem.put("rsrvStr7", "0");
        otherItem.put("rsrvStr8", "");
        otherItem.put("rsrvStr9", userInfo.get("brandCode"));// 退订产品品牌
        otherItem.put("rsrvStr10", isMixNum ? msg.get("serialNumberCP") : msg.get("serialNumber"));
        otherItem.put("startDate", msg.get("unBookProductStartDate"));// 取退订的产品节点的开始时间
        otherItem.put("endDate", GetDateUtils.getMonthLastDayFormat());
        otherList.add(otherItem);
        ext.put("tradeOther", MapUtils.asMap("item", otherList));
    }

    /**
     * 3.2非虚拟用户拼装tradeRelation节点
     * @param msg
     * @param threePartRet
     * @param ext
     */
    private void preTradeRelation(Map msg, Map threePartRet, Map ext) {
        List<Map> relationitems = new ArrayList<Map>();
        // 获取角色类型 1-手机;3-固网;4-宽带
        String roleCodeB = getRoleCodeB(msg);
        Map relationInfo = MapUtils.asMap("xDatatype", "NULL", "roleCodeB", roleCodeB, "serialNumberA",
                msg.get("serialNumberCP"), "serialNumberB", msg.get("serialNumber"), "itemId", "-1");
        relationInfo.put("roleCodeA", "0");// 貌似虚拟成员的是0
        relationInfo.put("idB", msg.get("userId"));// 成员用户的userId

        // 3.2.1拼装订购的关系节点
        Map relation = new HashMap(relationInfo);
        relation.put("relationTypeCode", msg.get("mixNetType"));
        relation.put("idA", msg.get("userIdCp"));// 虚拟用户的userId
        relation.put("startDate", msg.get("chgStartDate"));// 次月1日
        relation.put("endDate", msg.get("chgEndDate"));// 2050年
        relation.put("modifyTag", "0");
        relationitems.add(relation);

        // 3.2.2非纳入的成员用户,需要拼装退订的tradeRelation节点
        if ("0".equals(msg.get("installMode"))) {
            relation = new HashMap(relationInfo);
            putRelationInfo(threePartRet, relation, msg);
            relation.put("endDate", GetDateUtils.getMonthLastDayFormat());
            relation.put("modifyTag", "1");
            relationitems.add(relation);
        }
        ext.put("tradeRelation", MapUtils.asMap("item", relationitems));
    }

    /**
     * 从三户获取relationTypeCode和融合虚拟用户ID
     * @param threePartRet
     * @param relation
     * @param serialNumber
     */
    private void putRelationInfo(Map threePartRet, Map relation, Map msg) {
        Map userInfo = (Map) threePartRet.get("userInfo");
        List<Map> uuInfo = (List<Map>) userInfo.get("uuInfo");
        if (!IsEmptyUtils.isEmpty(uuInfo)) {
            for (Map temMap : uuInfo) {
                String endDate = (String) temMap.get("endDate");
                String relationTypeCode = (String) temMap.get("relationTypeCode");
                if (relationTypeCode.startsWith("89") || relationTypeCode.startsWith("88")
                        && 0 < endDate.compareTo(GetDateUtils.getNextMonthFirstDayFormat())) {
                    relation.put("startDate", temMap.get("startDate"));// 融合关系绑定的时间
                    // 放入融合关系的绑定时间
                    // msg.put("oldRelationStartDate", temMap.get("startDate"));
                    relation.put("idA", temMap.get("userIdA"));
                    relation.put("relationTypeCode", relationTypeCode);
                    return;
                }
            }
        }
        throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "号码:" + msg.get("serialNumber")
                + "调用三户接口未返回融合关系信息");
    }

    /**
     * 根据成员类型获取roleCodeB
     * @param msg
     * @return 1-手机;3-固网;4-宽带
     */
    private String getRoleCodeB(Map msg) {
        String memberType = String.valueOf(msg.get("memberType"));
        String roleCodeB = "1".equals(memberType) ? "1" : "2".equals(memberType) ? "3" : "3".equals(memberType) ? "4"
                : null;
        if (null == roleCodeB) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "成员类型值:memberType:" + memberType + ",有误");
        }
        return roleCodeB;
    }

    /**
     * 3.1虚拟成员拼装tradeUser节点
     * @param msg
     * @param threePartRet
     * @param ext
     */
    private void preTradeUser(Map msg, Map threePartRet, Map ext) {
        Map userInfo = (Map) threePartRet.get("userInfo");
        List<Map> userList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        String[] putKeys = new String[] { "userPasswd", "userTypeCode", "prepayTag", "inDate", "openDate" };
        MapUtils.arrayPut(item, userInfo, putKeys);

        // 存在发展人信息则拼装
        if (!IsEmptyUtils.isEmpty((Map) msg.get("recomInfo"))) {
            Map recomInfo = (Map) msg.get("recomInfo");
            item.put("developStaffId", recomInfo.get("recomPersonId"));
            item.put("developDepartId", recomInfo.get("recomDepartId"));
            item.put("developEparchyCode", recomInfo.get("recomCity"));
        }
        // 放一些写死的值
        item.putAll(MapUtils.asMap("userTypeCode", "0", "scoreValue", "0", "creditClass", "0", "basicCreditValue", "0",
                "creditValue", "0", "acctTag", "0", "prepayTag", "0", "openMode", "0", "removeTag", "0",
                "userStateCodeset", "0", "mputeMonthFee", "0", "inNetMode", "0"));
        // 取处理产品时放入的产品类型编码
        item.put("productTypeCode", msg.get("mainProductTypeCode"));
        item.put("openDepartId", msg.get("channelId"));
        item.put("openStaffId", msg.get("operatorId"));
        item.put("inDepartId", msg.get("channelId"));
        // 竣工报错,看下是不是这个地方的问题 FIXME
        item.put("netTypeCode", "00CP");
        userList.add(item);
        ext.put("tradeUser", MapUtils.asMap("item", userList));
    }

    /**
     * 2.处理产品
     * @param msg
     * @param threePartRet
     */
    private Map dealProductInfo(Map msg, Map threePartRet) {
        // 2.1.处理订购的产品节点
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        String provinceCode = "00" + msg.get("province");
        msg.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        Map ext = TradeManagerUtils.newPreProductInfo(productInfo, provinceCode, msg);

        // 移网处理速率
        chooseSpeed(ext, msg);

        // 2.2.处理订购节点的开始时间为次月
        dealProductStartDate(ext, msg);

        // 2.3.处理退订的产品节点
        dealUnBookProduct(msg, threePartRet, ext);
        return ext;
    }

    /**
     * 移网成员处理速率
     * @param ext
     * @param msg
     */
    private void chooseSpeed(Map ext, Map msg) {
        // 非移网成员或者没有下发速率字段,不处理速率
        if (!"1".equals(msg.get("memberType")) || IsEmptyUtils.isEmpty(msg.get("speedLevel"))) {
            return;
        }
        String speed = (String) msg.get("speedLevel");
        // HSPA+(42M上网)-50105，(LTE)100M-50103,(4G+)300M-50107
        String speedId = "42".equals(speed) ? "50105" : "100".equals(speed) ? "50103" : "50107";
        List<Map> svcList = (List<Map>) ((Map) ext.get("tradeSvc")).get("item");
        // 取当前产品的默认速率
        for (int i = 0; i < svcList.size(); i++) {
            String elementId = String.valueOf(svcList.get(i).get("ELEMENT_ID"));
            if ("50103,50105,50107".contains(elementId)) {
                if (elementId.equals(speedId)) {
                    break;
                }
                Map speedParam = new HashMap();
                // productInfoResult.remove(productInfoResult.get(i));
                speedParam.put("PRODUCT_ID", svcList.get(i).get("PRODUCT_ID"));
                speedParam.put("ELEMENT_ID", speedId);// 此处为外围传入的速率
                Map speedMap = new DealNewCbssProduct().qryNewProductSpeed(speedParam);
                // 重新覆盖服务ID和包ID
                svcList.get(i).put("serviceId", speedMap.get("ELEMENT_ID"));
                svcList.get(i).put("packageId", speedMap.get("PACKAGE_ID"));
                break;
            }
        }
    }

    /**
     * 2.3.处理退订的产品节点
     * @param msg
     * @param threePartRet
     * @param ext
     */
    private void dealUnBookProduct(Map msg, Map threePartRet, Map ext) {
        Map userInfo = (Map) threePartRet.get("userInfo");
        // 获取退订的产品ID
        String unBookProductId = String.valueOf(((Map) threePartRet.get("userInfo")).get("productId"));
        // 获取退订的产品开始时间,默认为当前时间
        String unBookStartDate = getUnBookProductStartDate(threePartRet, unBookProductId);
        // 放入退订产品的开始时间
        msg.put("unBookProductStartDate", unBookStartDate);

        // 退订产品节点的公共信息
        Map productInfo = MapUtils.asMap("xDatatype", "NULL", "userId", msg.get("userId"), "productMode", "00",
                "productId", unBookProductId, "modifyTag", "1", "startDate", unBookStartDate, "endDate",
                GetDateUtils.getMonthLastDay());

        // 退订的产品类型节点
        Map productType = new HashMap(productInfo);
        productType.put("productTypeCode", userInfo.get("productTypeCode"));
        List<Map> productTypeList = (List<Map>) ((Map) ext.get("tradeProductType")).get("item");
        productTypeList.add(productType);

        // 退订的产品节点
        Map product = new HashMap(productInfo);
        product.put("brandCode", userInfo.get("brandCode"));
        product.put("userIdA", "-1");
        product.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
        List<Map> productList = (List<Map>) ((Map) ext.get("tradeProduct")).get("item");
        productList.add(product);
    }

    /**
     * 2.2.处理产品节点的开始时间,所有订购的开始时间为次月
     * @param ext
     * @param msg
     */
    private void dealProductStartDate(Map ext, Map msg) {
        String[] dealNodeKeys = new String[] { "tradeProductType", "tradeProduct", "tradeDiscnt", "tradeSvc", "tradeSp" };
        for (String nodeKey : dealNodeKeys) {
            if (IsEmptyUtils.isEmpty(ext.get(nodeKey))
                    || IsEmptyUtils.isEmpty((List<Map>) ((Map) ext.get(nodeKey)).get("item"))) {
                continue;
            }
            List<Map> items = (List<Map>) ((Map) ext.get(nodeKey)).get("item");
            for (Map item : items) {
                item.put("startDate", msg.get("chgStartDate"));
            }
        }

    }

    /**
     * 1.调用三户接口
     * @param exchange
     * @param msg
     * @param isMixNum
     * @return
     */
    private Map checkThreePart(Exchange exchange, Map msg, boolean isMixNum) {
        Map threePartInfoMsg = new HashMap();
        // 获取调用三户的号码
        Object serialNumber = isMixNum ? msg.get("serialNumberCP") : msg.get("serialNumber");
        threePartInfoMsg.put("serialNumber", serialNumber);
        threePartInfoMsg.put("tradeTypeCode", "9999");
        threePartInfoMsg.put("getMode", "1111111111100013010000000100001");
        MapUtils.arrayPut(threePartInfoMsg, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartInfoMsg);

        try {
            lan.preData(pmp[0], threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "号码:" + serialNumber + "调用三户接口异常,"
                    + e.getMessage());
        }

        Map out = threePartExchange.getOut().getBody(Map.class);

        // 校验是否没有返回三户信息
        String[] infoKeys = new String[] { "userInfo", "custInfo", "acctInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        Map threePartRet = new HashMap();
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty((List<Map>) out.get(infoKey))) {
                throw new EcAopServerBizException("9999", "号码:" + serialNumber + "调三户未返回" + errorDetail.get(infoKey));
            }
            threePartRet.put(infoKey, ((List<Map>) out.get(infoKey)).get(0));
        }
        // 校验是否没返回老产品信息
        if (IsEmptyUtils.isEmpty(((Map) threePartRet.get("userInfo")).get("productInfo"))) {
            throw new EcAopServerBizException("9999", "号码:" + serialNumber + "调三户未返回产品信息");
        }
        threePartRet.put("productInfo", ((Map) threePartRet.get("userInfo")).get("productInfo"));
        // 放入userId
        msg.put("userId", ((Map) threePartRet.get("userInfo")).get("userId"));
        return threePartRet;
    }

    /**
     * 获取退订产品的开始时间,默认为当前时间
     * @param threePartRet
     * @param unBookProductId
     * @return
     */
    private String getUnBookProductStartDate(Map threePartRet, String unBookProductId) {
        List<Map> productInfo = (List<Map>) threePartRet.get("productInfo");
        if (!IsEmptyUtils.isEmpty(productInfo)) {
            for (Map product : productInfo) {
                // 获取当前有效的主产品的开始时间
                String nowDate = GetDateUtils.getDate();
                if (unBookProductId.equals(product.get("productId"))
                        && 0 > nowDate.compareTo(String.valueOf(product.get("productInactiveTime")))) {
                    return String.valueOf(product.get("productActiveTime"));
                }
            }
        }
        return GetDateUtils.getDate();
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
