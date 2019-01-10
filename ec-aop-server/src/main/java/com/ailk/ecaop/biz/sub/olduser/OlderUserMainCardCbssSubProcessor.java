package com.ailk.ecaop.biz.sub.olduser;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.sub.PreDataProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.ParamDef;
import org.n3r.core.tag.ParamDefAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 该接口是用来处理CBSS一号多卡主卡设置业务
 * Created by Liu JiaDi on 2017/2/8.
 */
@EcRocTag("olderUserMainCardCbssSub")
public class OlderUserMainCardCbssSubProcessor extends BaseAopProcessor implements ParamDefAppliable {
    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = {"ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.rnma.preSub.ParametersMapping",
            "ecaop.trades.rnma.sub.paramtersmapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("operTypeCode", "0");
        //msg.put("serviceClassCode", "00CP");// FIXME
        // 转换地市编码
        Map param = new HashMap();
        param.put("province", (msg.get("province")));
        param.put("city", msg.get("city"));
        String provinceCode = "00" + msg.get("province");
        msg.put("OperProvinceCode", provinceCode);
        msg.put("district", ((String) msg.get("district")).length() > 6 ? ((String) msg.get("district")).substring(0, 6) : msg.get("district") + "");
        param.put("provinceCode", provinceCode);
        List<Map> eparchyCoderesult = dao.queryEparchyCode(param);
        if (eparchyCoderesult == null || eparchyCoderesult.size() == 0) {
            throw new EcAopServerBizException("9999", "地市转换失败");
        }
        String eparchyCode = (String) (eparchyCoderesult.get(0).get("PARAM_CODE"));
        msg.put("OperEparchyCode", eparchyCode);
        msg.put("date", GetDateUtils.getDate());
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        //三户接口
        preThreePartInfo(exchange);
        //预提交接口
        preSubCommit(exchange, msg);
        //正式提交接口
        lan.preData(pmp[3], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.rnma.sub.template", exchange);

        // 受理成功时，返回总部和省份订单
        Map outMap = new HashMap();
        outMap.put("provOrderId", msg.get("orderNo"));
        outMap.put("bssOrderId", msg.get("provOrderId"));
        exchange.getOut().setBody(outMap);
    }

    private void preSubCommit(Exchange exchange, Map rootmsg) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map ext = new HashMap();
        String orderid = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        msg.put("tradeId", orderid);
        //准备base参数
        msg.put("base", preBaseData(msg, exchange.getAppCode()));
        ext.put("tradeRelation", preDataForTradeRelation(msg));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg));
        ext.put("tradeItem", preDataForTradeItem(msg));
        msg.put("ext", ext);
        try {
            lan.preData(pmp[2], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.rnma.preSub.template", exchange);
        } catch (Exception e) {
            e.getMessage();
            throw new EcAopServerBizException("9999", "调用cbss预提交失败" + e.getMessage());
        }
        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        Map rspInfo = rspInfoList.get(0);
        String provOrderId = rspInfo.get("provOrderId").toString();
        String bssOrderId = rspInfo.get("bssOrderId").toString();
        Map orderMap = preOrderSubParam(rspInfo);
        orderMap.put("orderNo", provOrderId);
        orderMap.put("provOrderId", bssOrderId);
        rootmsg.putAll(orderMap);
        body.put("msg", rootmsg);
        exchange.getIn().setBody(body);
    }

    private Map preOrderSubParam(Map inMap) {
        Map outMap = new HashMap();
        List<Map> provinceOrderInfo = (ArrayList<Map>) inMap.get("provinceOrderInfo");
        if (null != provinceOrderInfo && 0 != provinceOrderInfo.size()) {
            outMap.put("subOrderSubReq", dealSubOrder(provinceOrderInfo));
        }
        outMap.put("origTotalFee", "0");
        outMap.put("operationType", "01");
        outMap.put("cancleTotalFee", provinceOrderInfo.get(0).get("totalFee"));
        return outMap;
    }

    private Map dealSubOrder(List<Map> provinceOrderInfo) {
        Map retMap = new HashMap();
        for (Map tempMap : provinceOrderInfo) {
            retMap.put("subOrderId", tempMap.get("subOrderId"));
            retMap.put("subProvinceOrderId", tempMap.get("subProvinceOrderId"));
            List<Map> feeList = (ArrayList<Map>) tempMap.get("preFeeInfoRsp");
            List<Map> retFee = new ArrayList<Map>();
            if (null != feeList && 0 != feeList.size()) {
                for (Map fee : feeList) {
                    Map tempFee = dealFeeInfo(fee);
                    retFee.add(tempFee);
                }
                retMap.put("feeInfo", retFee);
            }
        }
        return retMap;
    }

    private Map dealFeeInfo(Map inputMap) {
        Map retMap = new HashMap();
        retMap.put("feeCategory", inputMap.get("feeMode"));
        retMap.put("feeId", inputMap.get("feeTypeCode"));
        retMap.put("feeDes", inputMap.get("feeTypeName"));
        retMap.put("operateType", "1");
        retMap.put("origFee", inputMap.get("oldFee"));
        retMap.put("reliefFee", "0");
        retMap.put("realFee", inputMap.get("oldFee"));
        return retMap;
    }

    //拼装TRADE_RELATION
    private Map preDataForTradeRelation(Map msg) {
        try {
            Map tradeRelation = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("idA", msg.get("userId"));
            item.put("relationAttr", "0");
            item.put("serialNumberA", msg.get("serialNumber"));
            item.put("roleCodeB", "1");
            item.put("xDatatype", "NULL");
            item.put("relationTypeCode", "RIO");
            item.put("serialNumberB", msg.get("serialNumber"));
            item.put("idB", msg.get("userId"));
            item.put("roleCodeA", "0");
            item.put("modifyTag", "0");
            item.put("startDate", msg.get("date"));
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);
            tradeRelation.put("item", itemList);
            return tradeRelation;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RELATION节点报错" + e.getMessage());
        }

    }

    //拼装TRADE_SUB_ITEM
    private Map preDataForTradeSubItem(Map msg) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeSubItem = new HashMap();
            String userId = (String) msg.get("userId");
            Item.add(lan.createTradeSubItemE("MAIN_NUM", msg.get("serialNumber"), userId));
            Item.add(lan.createTradeSubItemE("RIO_MAIN_CARD_TAG", "0", userId));
            Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("custName"), userId));
            Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("serialNumber"), userId));
           /* Map recomInfo = (Map) msg.get("recomInfo");
            if (null != recomInfo && recomInfo.size() > 0) {
                Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON_ID", msg.get("recomPersonId"), userId));
                Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON", msg.get("recomPersonName"), userId));
            }*/
            tradeSubItem.put("item", Item);
            return tradeSubItem;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SUB_ITEM节点报错" + e.getMessage());
        }
    }

    // 拼装TRADE_ITEM
    private Map preDataForTradeItem(Map msg) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeItem = new HashMap();
            Map recomInfo = (Map) msg.get("recomInfo");//发展人属性下发
            if (null != recomInfo && !recomInfo.isEmpty()) {
                Item.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", recomInfo.get("recomPersonId")));
                String recomDepartId = (String) recomInfo.get("recomDepartId");
                if (StringUtils.isNotEmpty(recomDepartId)) {
                    Item.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", recomDepartId));
                }
            }
            Item.add(LanUtils.createTradeItem("MAIN_CARD_TAG", "0"));
            Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "0371"));
            tradeItem.put("item", Item);
            return tradeItem;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ITEM节点报错" + e.getMessage());
        }
    }

    //准备base参数
    private Map preBaseData(Map msg, String appCode) {
        try {
            String date = (String) msg.get("date");
            Map base = new HashMap();
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("startDate", date);
            base.put("tradeTypeCode", "0120");
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("areaCode", msg.get("OperEparchyCode"));
            base.put("foregift", "0");
            base.put("execTime", date);
            base.put("acceptDate", date);
            base.put("chkTag", "0");
            base.put("operFee", "0");
            base.put("cancelTag", "0");
            base.put("endAcycId", "203701");
            base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
            base.put("acceptMonth", RDate.currentTimeStr("MM"));
            base.put("netTypeCode", "50");
            base.put("advancePay", "0");
            base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
            base.put("custId", msg.get("custId"));
            base.put("custName", msg.get("custName"));
            base.put("acctId", msg.get("acctId"));
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("productId", msg.get("productId"));
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", "4G00");
            base.put("usecustId", msg.get("custId"));
            base.put("checktypeCode", "0");
            base.put("userId", msg.get("userId"));
            base.put("termIp", "132.35.81.217");
            base.put("eparchyCode", msg.get("OperEparchyCode"));
            base.put("cityCode", msg.get("district"));
            base.put("remark", new PreDataProcessor().getRemark(msg));
            return base;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    private void preThreePartInfo(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "1111111111100013111100000100001");
        String serinalNum = (String) msg.get("serialNumber");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        new LanUtils().preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        new LanUtils().xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        Map checkUserMap = exchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) checkUserMap.get("custInfo");
        if (IsEmptyUtils.isEmpty(custInfo)) {
            throw new EcAopServerBizException("9999", "cbss未返回客户信息");
        }
        List<Map> userInfo = (List<Map>) checkUserMap.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "cbss未返回用户信息");
        }
        List<Map> acctInfo = (List<Map>) checkUserMap.get("acctInfo");
        if (IsEmptyUtils.isEmpty(acctInfo)) {
            throw new EcAopServerBizException("9999", "cbss未返回账户信息");
        }
        List<Map> uuInfo = (List<Map>) userInfo.get(0).get("uuInfo");
        if (!IsEmptyUtils.isEmpty(uuInfo)) {
            for (Map temMap : uuInfo) {
                String relationTypeCode = (String) temMap.get("relationTypeCode");
                String roleCodeB = (String) temMap.get("roleCodeB");
                String serialNumberA = (String) temMap.get("serialNumberA");
                if ("RIO".equals(relationTypeCode) && "1".equals(roleCodeB) && serinalNum.equals(serialNumberA)) {
                    throw new EcAopServerBizException("9999", "号码" + serinalNum + "已经是主卡，无需重复设置");
                }
            }
        }
        msg.put("acctId", acctInfo.get(0).get("acctId"));
        msg.put("custId", custInfo.get(0).get("custId"));
        msg.put("custName", custInfo.get(0).get("custName"));
        msg.put("userId", userInfo.get(0).get("userId"));
        msg.put("productId", userInfo.get(0).get("productId"));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    @Override
    public void applyParamDef(ParamDef paramDef) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

    public static void main(String[] args) {
        Map temMap = new HashMap();
        String relationTypeCode = (String) temMap.get("relationTypeCode");
        String roleCodeB = (String) temMap.get("roleCodeB");
        String serialNumberA = (String) temMap.get("serialNumberA");
        if ("RIO".equals(relationTypeCode) && "1".equals(roleCodeB) && "1".equals(serialNumberA)) {
            throw new EcAopServerBizException("9999", "号码" + 1 + "已经是主卡，无需重复设置");
        }
    }
}
