package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.OneNumWithMoreCardProduct;
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

/**
 * 一号多卡，主附卡开户处理申请4G
 * 
 * @author shenzy 20170210
 */
@EcRocTag("rioNewuOpenApp4CBSS")
public class RioNewuOpenApp4CBSSProcessors extends BaseAopProcessor implements ParamsAppliable {

    OneNumWithMoreCardProduct product = new OneNumWithMoreCardProduct();
    NumCenterUtils nc = new NumCenterUtils();
    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.masb.sbac.sglUniTradeParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Exchange exchangeNum = ExchangeUtils.ofCopy(exchange, msg);
        preDataForPreCommit(exchange, msg);
        Map msgTrade = new HashMap(msg);

        // 调预提交接口
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[2], exchange);
        try {
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", exchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用cbss统一预提交失败:" + e.getMessage());
        }
        callNumCenterPreemtedNum(exchangeNum);
        dealReturn(exchange, msgTrade);

    }

    private void preDataForPreCommit(Exchange exchange, Map msg) {
        changeAreaCode(msg);
        threepartCheck(ExchangeUtils.ofCopy(exchange, msg), msg);
        preBaseData(exchange, msg);
        preExtData(exchange, msg);
    }

    private void changeAreaCode(Map msg) {
        // 转换地市编码
        Map param = new HashMap();
        param.put("province", (msg.get("province")));
        param.put("city", msg.get("city"));
        String provinceCode = "00" + msg.get("province");
        msg.put("provinceCode", provinceCode);
        param.put("provinceCode", provinceCode);
        List<Map> eparchyCoderesult = dao.queryEparchyCode(param);
        if (eparchyCoderesult == null || eparchyCoderesult.size() == 0) {
            throw new EcAopServerBizException("9999", "地市转换失败");
        }
        String eparchyCode = (String) (eparchyCoderesult.get(0).get("PARAM_CODE"));
        msg.put("eparchyCode", eparchyCode);
        msg.put("date", GetDateUtils.getDate());

    }

    private void preExtData(Exchange exchange, Map msg) {
        Map ext = new HashMap();
        ext.put("tradeRelation", preDataForTradeRelation(msg));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg));
        ext.put("tradeUser", preDataForTradeUser(msg));
        ext.put("tradeItem", preDataForTradeItem(msg, exchange.getAppCode()));
        ext.put("tradeRes", preDataForTradeRes(msg));
        ext.put("tradePayrelation", preTradePayRelData(msg));
        ext.put("tradeAcct", preDataForTradeAcct(msg));
        ext.put("tradeProductType", preProductTpyeListData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeDiscnt", preDiscntData(msg));
        ext.put("tradeSvc", preTradeSvcData(msg));
        // ext.put("tradeSp", preTradeSpData(msg));
        // ext.put("tradefeeSub", preTradeFeeSub(msg));
        ext.put("tradeCustPerson", preCustPerData(msg));
        ext.put("tradeCustomer", preCustomerData(msg));
        // ext.put("tradeElement", preTradeElementData(msg));
        msg.put("ext", ext);
    }

    private Map preCustomerData(Map msg) {
        try {
            Map tradeCustomer = new HashMap();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("sustId", msg.get("custId"));
            item.put("custName", msg.get("custName"));
            item.put("custType", "0");
            item.put("custState", "0");
            item.put("psptTypeCode", msg.get("certType"));
            item.put("psptId", msg.get("certNum"));
            item.put("openLimit", "0");
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("developDepartId", msg.get("recomDepartId"));
            item.put("inDate", msg.get("date"));
            item.put("removeTag", "0");
            tradeCustomer.put("item", item);
            return tradeCustomer;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_CUSTOMER节点报错" + e.getMessage());
        }

    }

    private Map preCustPerData(Map msg) {
        try {
            Map tradeCustPerson = new HashMap();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("custId", msg.get("custId"));
            item.put("psptTypeCode", msg.get("certType"));
            item.put("psptId", msg.get("certNum"));
            item.put("psptEndDate", msg.get("certExpireDate"));
            item.put("psptAddr", msg.get("certAdress"));
            item.put("custName", msg.get("custName"));
            item.put("sex", msg.get("sex"));
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("postAddress", msg.get("contactAddress"));
            item.put("phone", msg.get("contactPhone"));
            item.put("contact", msg.get("contactPerson"));
            item.put("contactPhone", msg.get("contactPhone"));
            item.put("removeTag", "0");
            tradeCustPerson.put("item", item);
            return tradeCustPerson;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_CUST_PERSON节点报错" + e.getMessage());
        }

    }

    private Map preProductData(Map msg) {
        try {
            Map tradeProduct = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            for (int i = 0; i < product.getCbssProductInfo().size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("productMode", product.getCbssProductInfo().get(i)[3]);
                item.put("productId", product.getCbssProductInfo().get(i)[0]);
                item.put("brandCode", product.getCbssProductInfo().get(i)[2]);
                item.put("itemId", msg.get("itemId"));
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
                itemList.add(item);
            }
            tradeProduct.put("item", itemList);
            return tradeProduct;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT节点报错" + e.getMessage());
        }
    }

    private Map preTradeSvcData(Map msg) {
        try {
            Map svcList = new HashMap();
            List<Map> svList = new ArrayList();
            for (int i = 0; i < product.getCbssServiceInfo().size(); i++) {
                for (int j = 2; j < product.getCbssServiceInfo().get(i).length; j++) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("userId", msg.get("userId"));
                    item.put("serviceId", product.getCbssServiceInfo().get(i)[j]);
                    item.put("modifyTag", "0");
                    item.put("startDate", msg.get("date"));
                    item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    item.put("productId", product.getCbssServiceInfo().get(i)[0]);
                    item.put("packageId", product.getCbssServiceInfo().get(i)[1]);
                    item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                    item.put("userIdA", "-1");
                    svList.add(item);
                }
            }
            svcList.put("item", svList);
            return svcList;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SVC节点报错" + e.getMessage());
        }
    }

    private Map preDiscntData(Map msg) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            for (int i = 0; i < product.getCbssDiscntInfo().size(); i++) {
                for (int j = 2; j < product.getCbssDiscntInfo().get(i).length; j++) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("id", msg.get("userId"));
                    item.put("idType", "1");
                    item.put("userIdA", "-1");
                    item.put("productId", product.getCbssDiscntInfo().get(i)[0]);
                    item.put("packageId", product.getCbssDiscntInfo().get(i)[1]);
                    item.put("discntCode", product.getCbssDiscntInfo().get(i)[j]);
                    item.put("specTag", "1");
                    item.put("relationTypeCode", "");
                    item.put("startDate", msg.get("date"));
                    item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    item.put("modifyTag", "0");
                    item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                    itemList.add(item);
                }
            }

            tradeDis.put("item", itemList);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    private Map preProductTpyeListData(Map msg) {
        try {
            Map tradeProductType = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            for (int i = 0; i < product.getCbssProductInfo().size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("productMode", product.getCbssProductInfo().get(i)[3]);
                item.put("productId", product.getCbssProductInfo().get(i)[0]);
                item.put("productTypeCode", product.getCbssProductInfo().get(i)[1]);
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", "20501231235959");
                itemList.add(item);
            }
            tradeProductType.put("item", itemList);
            return tradeProductType;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT_TYPE节点报错" + e.getMessage());
        }
    }

    private Map preDataForTradeAcct(Map msg) {
        try {
            Map item = new HashMap();
            Map tradeAcct = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("acctId", msg.get("acctId"));
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("custId", msg.get("custId"));
            item.put("payName", msg.get("customerName"));
            item.put("payModeCode", getPayModeCode(msg));
            item.put("scoreValue", "0");
            item.put("creditClassId", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("creditControlId", "0");
            item.put("removeTag", "0");
            item.put("debutyUserId", msg.get("userId"));
            item.put("debutyCode", msg.get("serialNumB"));
            // item.put("removeDate", GetDateUtils.getDate());
            item.put("acctPasswd", "0");
            item.put("contractNo", "0");
            item.put("openDate", msg.get("date"));
            tradeAcct.put("item", item);
            return tradeAcct;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ACCT节点报错" + e.getMessage());
        }
    }

    private Map preTradePayRelData(Map msg) {
        try {
            Map tradePayRel = new HashMap();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("acctId", msg.get("acctId"));
            item.put("payitemCode", "-1");
            item.put("acctPriority", "0");
            item.put("userPriority", "0");
            item.put("bindType", "1");
            item.put("startAcycId", msg.get("cycId"));
            item.put("endAcycId", "203712");
            item.put("defaultTag", "1");
            item.put("limitType", "0");
            item.put("limit", "0");
            item.put("complementTag", "0");
            item.put("addupMonths", "0");
            item.put("addupMethod", "0");
            item.put("payrelationId", msg.get("payRelId"));
            item.put("actTag", "1");
            tradePayRel.put("item", item);
            return tradePayRel;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PAYRELATION节点报错" + e.getMessage());
        }
    }

    private Map preDataForTradeRes(Map msg) {
        try {
            Map tradeRes = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("reTypeCode", "0");
            item.put("resCode", msg.get("serialNumB"));
            item.put("modifyTag", "0");
            item.put("startDate", msg.get("date"));
            item.put("endDate", "20501231235959");
            itemList.add(item);
            List<Map> simCardDatas = (List<Map>) msg.get("simCardData");
            if (null != simCardDatas && simCardDatas.size() > 0) { // 卡信息传了为成卡
                for (Map simCardData : simCardDatas) {
                    Map item1 = new HashMap();
                    item1.put("xDatatype", "NULL");
                    item1.put("modifyTag", "0");
                    item1.put("reTypeCode", "1");
                    item1.put("resCode", simCardData.get("eiccid"));
                    item1.put("resInfo1", simCardData.get("eimsi"));
                    item1.put("resInfo2", "notRemote");
                    item1.put("resInfo4", "1000101");
                    item1.put("resInfo5", simCardData.get("cardBigType")); // cb那边这个最多4位
                    item1.put("resInfo6", "1234-12345678");// TODO:CB说写死
                    // item1.put("resInfo7", simCardData.get("ki"));
                    item1.put("startDate", msg.get("date"));
                    item1.put("endDate", "20501231235959");
                    itemList.add(item1);
                }
            }
            else {// 白卡插一条RES_TYPE_CODE为1 的数据
                Map item2 = new HashMap();
                item2.put("xDatatype", "NULL");
                item2.put("reTypeCode", "1");
                item2.put("resCode", "89860");
                item2.put("modifyTag", "0");
                item2.put("startDate", GetDateUtils.getDate());
                item2.put("endDate", "20501231235959");
                itemList.add(item2);
            }
            tradeRes.put("item", itemList);
            return tradeRes;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RES节点报错" + e.getMessage());
        }
    }

    private Map preDataForTradeItem(Map msg, String appCode) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeItem = new HashMap();
            List<Map> recomInfo = (List<Map>) msg.get("recomInfo");// 发展人属性下发
            if (null != recomInfo && recomInfo.size() > 0) {
                for (Map recomInfoo : recomInfo) {
                    String recomDepartId = (String) recomInfoo.get("recomDepartId");
                    Map developStaff = new HashMap();
                    developStaff.put("xDatatype", "NULL");
                    developStaff.put("attrValue", recomInfoo.get("recomPersonId"));
                    developStaff.put("attrCode", "DEVELOP_STAFF_ID");
                    Item.add(developStaff);
                    if (StringUtils.isNotEmpty(recomDepartId)) {
                        Map developDepart = new HashMap();
                        developDepart.put("xDatatype", "NULL");
                        developDepart.put("attrValue", recomDepartId);
                        developDepart.put("attrCode", "DEVELOP_DEPART_ID");
                        Item.add(developDepart);
                    }
                }
            }
            Map workTradeItem = new HashMap();
            workTradeItem.put("attrValue", "WORK_TRADE_ID");
            workTradeItem.put("attrCode", msg.get("ordersId"));
            Item.add(workTradeItem);

            Map userCall = new HashMap();
            userCall.put("attrValue", getNumberSrc((String) msg.get("provinceCode"), msg.get("serialNumB").toString()));
            userCall.put("attrCode", "nbrSrc");
            Item.add(userCall);

            Map numberType = new HashMap();
            numberType.put("attrValue", msg.get("serialNumB").toString().substring(0, 3));
            numberType.put("attrCode", "NumberType");
            Item.add(numberType);

            Map numThawPro = new HashMap();
            numThawPro.put("attrValue", "0");
            numThawPro.put("attrCode", "NumThawPro");
            Item.add(numThawPro);

            Map userPasswd = new HashMap();
            userPasswd.put("attrValue", msg.get("serialNumB").toString().substring(5, 11));
            userPasswd.put("attrCode", "USER_PASSWD");
            userPasswd.put("xDatatype", "NULL");
            Item.add(userPasswd);
            // 成卡标记
            if ("1".equals(msg.get("cardType"))) {
                Map isRemote = new HashMap();
                isRemote.put("attrValue", "1");
                isRemote.put("attrCode", "NOT_REMOTE");
                isRemote.put("xDatatype", "NULL");
                Item.add(isRemote);
            }

            Map newPasswd = new HashMap();
            newPasswd.put("attrValue", msg.get("serialNumB").toString().substring(5, 11));
            newPasswd.put("attrCode", "NEW_PASSWD");
            newPasswd.put("xDatatype", "NULL");
            Item.add(newPasswd);

            Map reopenTag = new HashMap();
            reopenTag.put("attrValue", "2");
            reopenTag.put("attrCode", "REOPEN_TAG");
            reopenTag.put("xDatatype", "NULL");
            Item.add(reopenTag);

            if ("0".equals(msg.get("deductionTag"))) {
                Map feeType = new HashMap();
                feeType.put("attrValue", "1");
                feeType.put("attrCode", "FEE_TYPE");
                feeType.put("xDatatype", "NULL");
                Item.add(feeType);
            }

            Map standardKindCode = new HashMap();
            standardKindCode.put("attrValue", "0371");
            standardKindCode.put("attrCode", "STANDARD_KIND_CODE");
            standardKindCode.put("xDatatype", "NULL");
            Item.add(standardKindCode);

            Map mainCardTag = new HashMap();
            Map viceCardTag = new HashMap();

            viceCardTag.put("attrValue", "1");
            mainCardTag.put("attrCode", "DEPUTY_CARD_TAG");
            mainCardTag.put("attrValue", "0");
            Item.add(mainCardTag);

            viceCardTag.put("attrCode", "EXISTS_ACCT");
            viceCardTag.put("xDatatype", "NULL");
            Item.add(viceCardTag);
            /*
             * String delayTag = (String) msg.get("delayTag");
             * if (StringUtils.isNotEmpty(delayTag) && "1".equals(delayTag)) {
             * Item.add(LanUtils.createTradeItem("E_DELAY_TIME_CEL", "1"));
             * }
             */
            /*
             * if (null != msg.get("tradeItem")) {
             * Item.addAll((List<Map>) msg.get("tradeItem"));
             * }
             */

            if ("X".equals(new ChangeCodeUtils().getInModeCode(appCode))) {
                Item.add(LanUtils.createTradeItem("WORK_TRADE_ID", msg.get("tradeId").toString()));
            }
            tradeItem.put("item", Item);
            return tradeItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ITEM节点报错" + e.getMessage());
        }
    }

    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("custId", msg.get("custId"));
            item.put("usecustId", msg.get("custId"));
            item.put("brandCode", product.getCbssProductInfo().get(0)[2]);
            item.put("productId", product.getCbssProductInfo().get(0)[0]);
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            // 活动类型
            item.put("productTypeCode", product.getCbssProductInfo().get(0)[1]);
            item.put("userPasswd",
                    msg.get("serialNumB").toString().substring(msg.get("serialNumB").toString().length() - 6));
            item.put("userTypeCode", "0");
            item.put("serialNumber", msg.get("serialNumB"));
            item.put("netTypeCode", "50");
            item.put("scoreValue", "0");
            item.put("creditClass", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("acctTag", "0");
            item.put("prepayTag", "1");
            item.put("inDate", msg.get("date"));
            item.put("openDate", msg.get("date"));
            item.put("openMode", "0");
            item.put("openDepartId", msg.get("channelId"));
            item.put("openStaffId", msg.get("operatorId"));
            item.put("inDepartId", msg.get("channelId"));
            item.put("inStaffId", msg.get("operatorId"));
            item.put("removeTag", "0");
            item.put("userStateCodeset", "0");
            item.put("userDiffCode", "00");
            item.put("mputeMonthFee", "0");
            List<Map> recomInfos = (List<Map>) msg.get("recomInfo");
            if (null != recomInfos && recomInfos.size() > 0) {
                for (Map recomInfo : recomInfos) {
                    item.put("developDate", GetDateUtils.getDate());
                    item.put("developStaffId", recomInfo.get("recomPersonId"));
                    item.put("developDepartId", recomInfo.get("recomDepartId"));
                }
            }
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

    private Map preDataForTradeSubItem(Map msg) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeSubItem = new HashMap();
            Map tradeid = new HashMap();
            String itemId = (String) msg.get("userId");
            tradeid.put("startDate", msg.get("date"));
            tradeid.put("attrValue", msg.get("tradeId"));
            tradeid.put("attrCode", "tradeId");
            tradeid.put("attrTypeCode", "3");
            tradeid.put("itemId", itemId);
            tradeid.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            Item.add(tradeid);

            Map netCode = new HashMap();
            netCode.put("attrValue", "50");
            netCode.put("attrCode", "NET_CODE");
            netCode.put("attrTypeCode", "0");
            netCode.put("itemId", itemId);
            Item.add(netCode);

            Map userCall = new HashMap();
            userCall.put("attrValue", msg.get("channelId"));
            userCall.put("attrCode", "USER_CALLING_AREA");
            userCall.put("attrTypeCode", "0");
            userCall.put("itemId", itemId);
            Item.add(userCall);

            Map initPasswd = new HashMap();
            initPasswd.put("attrValue", "0");
            initPasswd.put("attrCode", "INIT_PASSWD");
            initPasswd.put("attrTypeCode", "0");
            initPasswd.put("itemId", itemId);
            Item.add(initPasswd);

            Map userPasswd = new HashMap();
            userPasswd.put("attrValue", msg.get("serialNumB").toString().substring(5, 11));
            userPasswd.put("attrCode", "USER_PASSWD");
            userPasswd.put("attrTypeCode", "0");
            userPasswd.put("itemId", itemId);
            Item.add(userPasswd);

            Map mainCardTag = new HashMap();
            mainCardTag.put("startDate", msg.get("date"));
            mainCardTag.put("attrValue", "0");
            mainCardTag.put("attrCode", "MAINDEPUTY_CARD_DATE");
            mainCardTag.put("attrTypeCode", "0");
            mainCardTag.put("itemId", itemId);
            Item.add(mainCardTag);

            Map viceCardTag = new HashMap();
            viceCardTag.put("startDate", msg.get("date"));
            viceCardTag.put("attrValue", "0");
            viceCardTag.put("attrCode", "DEPUTY_CARD_TAG");
            viceCardTag.put("attrTypeCode", "0");
            viceCardTag.put("itemId", itemId);
            Item.add(viceCardTag);

            if (null != msg.get("subItemList")) {
                Item.addAll((List<Map>) msg.get("subItemList"));
            }
            Item.add(lan.createTradeSubItemE("LOCAL_NET_CODE", msg.get("eparchyCode"), itemId));
            Item.add(lan.createTradeSubItemE("BUSI_CODE", msg.get("eparchyCode"), itemId));
            Item.add(lan.createTradeSubItemE("CUSTOMER_GROUP", "0", itemId));
            Item.add(lan.createTradeSubItemE("groupFlag", "0", itemId));

            List<Map> recomInfo = (List<Map>) msg.get("recomInfo");
            if (null != recomInfo && recomInfo.size() > 0) {
                for (Map recomInfoo : recomInfo) {
                    Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON_ID", recomInfoo.get("recomPersonId"), itemId));
                    Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON", recomInfoo.get("recomPersonName"), itemId));
                }
            }
            Item.add(lan.createTradeSubItemE("CARD_TYPE", msg.get("cardType"), itemId));
            Item.add(lan.createTradeSubItemE("MAIN_NUM", msg.get("serialNumA"), itemId));
            Item.add(lan.createTradeSubItemE("TERMINAL_TYPE", msg.get("terminalType"), itemId));
            Item.add(lan.createTradeSubItemE("ATTACH_NAME", msg.get("attachName"), itemId));
            Item.add(lan.createTradeSubItemE("IMEI", msg.get("imei"), itemId));
            Item.add(lan.createTradeSubItemE("RELATION_TYPE_CODE", "RIO", itemId));

            // 附卡信息加到属性表
            List<Map> simCardData = (List<Map>) msg.get("simCardData");
            if (null != simCardData && simCardData.size() > 0) {
                for (Map simCardDataa : simCardData) {
                    Item.add(lan.createTradeSubItemE("EID", simCardDataa.get("eid"), itemId));
                    Item.add(lan.createTradeSubItemE("ICCID", simCardDataa.get("eiccid"), itemId));
                    Item.add(lan.createTradeSubItemE("IMSI", simCardDataa.get("eimsi"), itemId));
                    Item.add(lan.createTradeSubItemE("NEW_CARD_DATA", simCardDataa.get("ki"), itemId));
                }
            }

            if ("0".equals(msg.get("numSwitch"))) {// 号卡中心标志
                Item.add(lan.createTradeSubItemE("NUMERICAL_SELECTION", "2", itemId));
            }
            else {
                Item.add(lan.createTradeSubItemE("NUMERICAL_SELECTION", "1", itemId));
            }
            tradeSubItem.put("item", Item);
            return tradeSubItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SUB_ITEM节点报错" + e.getMessage());
        }

    }

    private Map preDataForTradeRelation(Map msg) {
        try {
            Map tradeRelation = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("idA", msg.get("idA"));
            item.put("idB", msg.get("userId"));
            item.put("serialNumberA", msg.get("serialNumA"));
            item.put("roleCodeB", "0");
            item.put("relationAttr", "0");
            item.put("xDatatype", "NULL");
            item.put("relationTypeCode", "RIO");
            item.put("serialNumberB", msg.get("serialNumB"));
            item.put("roleCodeA", "0");
            item.put("modifyTag", "0");
            item.put("startDate", GetDateUtils.getDate());
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);
            tradeRelation.put("item", itemList);
            return tradeRelation;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RELATION节点报错" + e.getMessage());
        }

    }

    /**
     * @param exchange
     * @param msg
     */
    private void threepartCheck(Exchange exchange, Map msg) {
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map preSubmitMap = Maps.newHashMap();
        MapUtils.arrayPut(preSubmitMap, msg, copyArray);
        // Exchange tradeEchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        msg.put("userId",
                GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_user_id", 1).get(0));
        msg.put("idA", "-1");
        String serialNumber = (String) msg.get("serialNumA");
        String orderid = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        // 查询cycId
        List<Map> cycIdresult = dao.querycycId(msg);
        String cycId = (String) (cycIdresult.get(0).get("CYCLE_ID"));
        msg.put("cycId", cycId);

        String payRelId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "SEQ_PAYRELA_ID");
        msg.put("payRelId", payRelId);
        msg.put("tradeId", orderid);
        msg.put("ordersId", orderid);
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "1111111111100013111100000100001");
        // msg.put("serviceClassCode", "00CP");
        msg.put("serialNumber", serialNumber);
        msg.put("operTypeCode", "0");
        lan.preData(pmp[0], exchange);
        try {
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用三户接口失败 " + e.getMessage());
        }
        Map checkUserMap = exchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) checkUserMap.get("custInfo");
        if (null == custInfo || 0 == custInfo.size()) {
            throw new EcAopServerBizException("9999", "客户信息未返回");
        }
        transMap(msg, custInfo.get(0));
        List<Map> userInfo = (List<Map>) checkUserMap.get("userInfo");
        if (null == userInfo || 0 == userInfo.size()) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        List<Map> acctInfo = (List<Map>) checkUserMap.get("acctInfo");
        if (null == acctInfo || 0 == acctInfo.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回账户信息");
        }
        List<Map> uuInfo = (List<Map>) userInfo.get(0).get("uuInfo");
        if (null == uuInfo || 0 == uuInfo.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回uuInfo信息");
        }
        Map tempMap = new HashMap();
        for (Map temMap : uuInfo) {
            String relationTypeCode = (String) temMap.get("relationTypeCode");
            String roleCodeB = (String) temMap.get("roleCodeB");
            if ("RIO".equals(relationTypeCode) && "1".equals(roleCodeB)) {
                tempMap.put("idA", temMap.get("userIdA"));
                tempMap.put("startDate", temMap.get("startDate"));
            }
        }
        if (tempMap.size() > 0) {
            msg.putAll(tempMap);
        }
        else {
            throw new EcAopServerBizException("9999", "cbss未返回主副卡uuInfo信息");
        }

        msg.put("acctId", checkNull(acctInfo.get(0), "acctId"));
        msg.put("custId", checkNull(custInfo.get(0), "custId"));
        msg.put("serialNumber", serialNumber);
    }

    private void preBaseData(Exchange exchange, Map msg) {
        try {
            String[] productInfo = product.getCbssProductInfo().get(0);
            String date = (String) msg.get("date");
            Map base = new HashMap();
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("startDate", date);
            base.put("tradeTypeCode", "0010");
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("areaCode", msg.get("eparchyCode"));
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
            String inModeCode = (String) new ChangeCodeUtils().getInModeCode(exchange.getAppCode());
            base.put("inModeCode", inModeCode);
            // 预受理系统要在tradeitem节点下下发WORK_TRADE_ID，所以放到msg里面用来判断
            msg.put("inModeCode", inModeCode);
            base.put("custId", msg.get("custId"));
            base.put("custName", msg.get("customerName"));
            base.put("acctId", msg.get("acctId"));
            base.put("serinalNamber", msg.get("serialNumB"));
            base.put("productId", productInfo[0]);
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", productInfo[2]);
            base.put("usecustId", msg.get("custId"));
            base.put("checktypeCode", "0");
            base.put("userId", msg.get("userId"));
            base.put("termIp", "132.35.81.217");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("cityCode", msg.get("district"));
            base.put("remark", new PreDataProcessor().getRemark(msg));
            if ("1".equals(msg.get("cardType"))) {// 成卡标记，成卡为1，白卡为2
                base.put("IS_REMOTE", "1");
            }
            else {
                base.put("IS_REMOTE", "2");

            }
            msg.put("base", base);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    /**
     * 处理返回
     * 
     * @param exchange
     * @param ordersId
     */
    private void dealReturn(Exchange exchange, Map msg) {
        Map body = (Map) exchange.getOut().getBody();
        List<Map> rspInfo = (List<Map>) body.get("rspInfo");
        for (Map rspInfoo : rspInfo) {
            body.put("provOrderId", rspInfoo.get("provOrderId"));
            msg.put("tradeId", rspInfoo.get("provOrderId"));
        }
        body.put("orderId", msg.get("ordersId"));
        msg.put("IS_REMOTE", "2");
        msg.put("remark", "RIO");
        if ("1".equals(msg.get("cardType"))) {
            msg.put("IS_REMOTE", "1");
            msg.put("remark", "CardRIO"); // 代表成卡
        }
        msg.put("serialNumber", msg.get("serialNumB"));
        msg.put("mProductId", product.getCbssProductInfo().get(0)[0]);
        msg.put("mBrandCode", product.getCbssProductInfo().get(0)[2]);

        msg.put("resourcesCode", "1111111");
        msg.put("ACTIVITY_TYPE", "01");
        System.out.println("RIOMSG" + msg);
        new TradeManagerUtils().insert2TradeSop(msg);
        body.remove("rspInfo");
    }

    /**
     * 校验参数是否为空,如果为空返回异常
     * 只针对三户信息校验
     */
    private Object checkNull(Map map, String key) {
        if (null == map) {
            throw new EcAopServerBizException("9999", map + "不能为空");
        }
        if (null == map.get(key)) {
            throw new EcAopServerBizException("9999", key + "不能为空");
        }
        return map.get(key);
    }

    /**
     * 遍历Map 封装参数到msg
     */
    private void transMap(Map msg, Map infoMap) {
        Map map = new HashMap();
        String[] inInfo = new String[] { "sex", "certType", "certNum", "certAdress", "customerName", "certExpireDate",
                "contactPerson", "contactAddress" };
        String[] custInfo = new String[] { "sex", "certTypeCode", "certCode", "certAddr", "custName", "certEndDate",
                "contact", "postAddress" };
        for (int i = 0; i < 8; i++) {
            if (null != infoMap.get(custInfo[i])) {
                map.put(inInfo[i], infoMap.get(custInfo[i]));
            }
            else {
                throw new EcAopServerBizException("9999", "三户接口返回节点" + custInfo[i] + "不能为空");
            }
        }
        msg.putAll(map);

    }

    /**
     * 获取蛋疼的NUMBER_SRC CBSS无此字段无法完工
     * nbrsrc 1.总部号码 2.南25省号码 3.北六号码
     * 
     * @param provinceCode
     * @param serialNumber
     * @return
     */
    private String getNumberSrc(String provinceCode, String serialNumber) {
        // 1 -> 176号段为 总部号码
        if (serialNumber.startsWith("175") || serialNumber.startsWith("176") || serialNumber.startsWith("185")) {
            return "1";
        }
        // 如果是北六省份号码 NUMBER_SRC 为3
        if ("0011|0076|0017|0018|0097|0091".contains(provinceCode)) {
            return "3";
        }
        return "2";
    }

    /**
     * 获取账户付费方式编码
     * 
     * @param msg
     * @return
     */
    private String getPayModeCode(Map msg) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map param = new HashMap();
        param.put("EPARCHY_CODE", msg.get("eparchyCode"));
        param.put("PARAM_CODE", msg.get("accountPayType"));
        param.put("PROVINCE_CODE", msg.get("provinceCode"));
        List<Map> payModeCoderesult = dao.queryPayModeCode(param);
        if (payModeCoderesult.size() > 0) {
            return payModeCoderesult.get(0).get("PARA_CODE1").toString();
        }
        return "0";
    }

    // 调号卡中心号码预占接口
    private void callNumCenterPreemtedNum(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String numSwitch = (String) msg.get("numSwitch");
        if (!"0".equals(numSwitch)) {
            return;
        }
        msg.put("serialNumber", msg.get("serialNumB"));
        // 获取sysCode
        new DealSysCodeExtractor().extract(exchange);
        // 准备参数
        dealReqPreemted(exchange);
        try {
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.preemptedNum");
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用号卡中心预占接口失败！" + e.getMessage());
        }
        nc.dealReturnHead(exchange);
    }

    private void dealReqPreemted(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        requestTempBody.put("SERIAL_NUMBER", msg.get("serialNumber"));
        Map resultMap = new HashMap();
        resultMap.put("PREEMPTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        // requestBody.put("UNI_BSS_HEAD", requestBody);
        exchange.getIn().setBody(requestBody);

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
