package com.ailk.ecaop.biz.sub.gdjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.processor.Xml2Json4FbsNoErrorMappingProcessor;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.N25ChgToCbssProduct;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

/**
 * 23转4开户申请--适用于智慧沃家融合业务--新产品逻辑
 * @author GaoLei
 */
@EcRocTag("newTransTo4GApply")
@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class NewTransTo4GApplyProcessor extends BaseAopProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransTo4GApplyProcessor.class);

    protected ParametersMappingProcessor[] pmp = null;

    public NewTransTo4GApplyProcessor(ParametersMappingProcessor[] pmp) {
        this.pmp = pmp;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Map rtnMap = applyChangeTo4G(exchange);
        exchange.getOut().setBody(rtnMap);
    }

    /**
     * 23转4处理申请
     * @param exchange
     * @return
     * @throws Exception
     */
    public Map applyChangeTo4G(Exchange exchange) throws Exception {
        Map msg = new HashMap();
        Map body = exchange.getIn().getBody(Map.class);
        Map tempbody = new HashMap(body);
        Map appendData = new HashMap();
        Map appendMap = new HashMap();
        String appcode = exchange.getAppCode();
        msg = (Map) tempbody.get("msg");
        Object modeCode = msg.get("eModeCode");
        String packageId = "";
        String elementDiscntId = "";
        String province = (String) msg.get("province");
        String provinceCode = "00" + province;
        String strEparchyCode = ChangeCodeUtils.changeEparchy(msg);
        Map phoneTemplate = (Map) msg.get("phoneTemplate");
        Object serialNumber = phoneTemplate.get("serialNumber");
        String mainCardTag = (String) phoneTemplate.get("mainCardTag");
        if (StringUtils.isEmpty(mainCardTag)) {
            mainCardTag = "1";// 非加入主卡 0加入主卡
            msg.put("mainCardTag", mainCardTag);
        }
        String combAdvance = null;
        Map mixTemplate = (Map) msg.get("mixTemplate");
        String addType = (String) mixTemplate.get("addType");
        msg.put("addType", mixTemplate.get("addType"));
        if ("0".equals(mainCardTag) && "3".equals(addType)) {
            combAdvance = "1";
        }
        String netTypeCode = "50";
        String serType = (String) phoneTemplate.get("serType");
        String newUserPwd = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        String strPrepayTag = serType;
        String custPwd = "123456";
        // 23G转4G客户校验 ,先取出存好的NET_TYPE_CODE -->serCode
        Object serClassCode = phoneTemplate.get("serClassCode");
        if (null == serClassCode || "".equals(serClassCode)) {
            Map operInfo = getOperInfo(serialNumber);
            if (null != operInfo && operInfo.size() > 0) {
                serClassCode = operInfo.get("netTypeCode");
            }
        }
        msg.put("tradeTypeCode", "0448");
        msg.put("serviceClassCode", serClassCode);
        msg.put("serialNumber", serialNumber);
        msg.remove("areaCode");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // copy一个Exchange，用于调3GE接口查询终端信息
        Exchange qryTermExchange = ExchangeUtils.ofCopy(exchange, msg);
        // TransTo4GCheckProcessor to4Chk = new TransTo4GCheckProcessor();
        Map checkResult = check23GTransfer(exchange, true);
        Map oldCust = (Map) checkResult.get("custInfo");
        Map oldUser = (Map) checkResult.get("userInfo");
        Map oldAcct = (Map) checkResult.get("acctInfo");

        Map postInfo = (Map) checkResult.get("postInfo");
        String srcType = (String) oldCust.get("certTypeCode");
        // 证件类型从全业务-->CBSS
        String certType = CertTypeChangeUtils.certTypeMall2Cbss(CertTypeChangeUtils.certTypeFbs2Mall(srcType));
        String certCode = (String) oldCust.get("certCode");
        String linkName = (String) oldCust.get("custName");
        Object linkPhone = serialNumber;
        String linkAddress = (String) oldCust.get("postAddress");
        String custName = (String) oldCust.get("custName");
        String certAddress = (String) oldCust.get("certAddr");
        String certEndDate = "";
        if (StringUtils.isNotEmpty((String) oldCust.get("certEndDate"))) {
            GetDateUtils.transDate((String) oldCust.get("certEndDate"), 19);
        }
        String oldInDate = GetDateUtils.transDate((String) oldUser.get("openDate"), 19);
        String creditClass = TradeManagerUtils.decodeCreditClass((String) oldCust.get("creditClass"));
        String sex = null == oldCust.get("sex") ? "2" : "1".equals(oldCust.get("sex")) == true ? "F" : "M";
        boolean isExistCust = false;
        Map phoneCustInfo = (Map) phoneTemplate.get("phoneCustInfo");
        Object oldCustId = "";
        // Map.get(key)需要判空和String类型转换 modiyfy by wangrj3
        if (null != phoneCustInfo && null != phoneCustInfo.get("custId")
                && !"".equals(phoneCustInfo.get("custId").toString())) {
            isExistCust = true;
            oldCustId = phoneCustInfo.get("custId");
        }
        // 账户资料 begin
        Map baseObject = Maps.newHashMap();
        Map extObject = Maps.newHashMap();
        exchange.getIn().setBody(body);
        Object tradeId = msg.get("subscribeId");
        exchange.getIn().setBody(body);
        String payRelId = GetSeqUtil.getSeqFromCb(exchange, "SEQ_PAYRELA_ID");
        exchange.getIn().setBody(body);
        Object custId = isExistCust ? oldCustId : GetSeqUtil.getSeqFromCb(exchange, "SEQ_CUST_ID");
        // Object custId = GetSeqUtil.getSeqFromCb(exchange, "SEQ_CUST_ID");
        exchange.getIn().setBody(body);
        // 取融合业务中的虚拟用户的账户ID
        String acctId = msg.get("acctId").toString();
        String userId = (String) msg.get("vitualUserId");
        if (StringUtils.isEmpty(userId)) {// 如果融合业务中未传虚拟用户ID，则去CB生成一个
            exchange.getIn().setBody(body);
            userId = GetSeqUtil.getSeqFromCb(exchange, "SEQ_USER_ID");
        }
        // 当前时间 yyyy-MM-dd HH:mm:ss
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        // 次月1号0时0分0秒
        String realStartDate = GetDateUtils.transDate(GetDateUtils.getNextMonthFirstDayFormat(), 19);
        //zsq
        if ("81".contains(province) && "scpr".equals(appcode)) {
            try {
                realStartDate = GetDateUtils.transDate(realStartDate, 14);
                acceptDate = GetDateUtils.transDate(acceptDate, 14);
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException("9999", "时间格式转换失败");
            }
        }
        extObject.put("itemStartDate", realStartDate);
        msg.put("realStartDate", realStartDate);
        //zsq
        extObject.put("itemEndDate", "2050-12-31 23:59:59");
        if ("81".contains(province) && "scpr".equals(appcode)) {
            extObject.put("itemEndDate", "20501231235959");
        }
        String cycleId = TradeManagerUtils.getCycleId(realStartDate);
        String tradeStaffId = TradeManagerUtils.getStringValue(msg, "operatorId");

        // 获取员工信息
        msg.put("tradeStaffId", tradeStaffId);
        msg.put("checkMode", "1");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        Map staffInfo = TradeManagerUtils.getStaffInfo(exchange);
        String departId = TradeManagerUtils.getStringValue(staffInfo, "departId");
        String cityCode = TradeManagerUtils.getStringValue(staffInfo, "city");
        msg.put("city", cityCode);
        String userCityCode = (String) (StringUtils.isEmpty((String) oldUser.get("cityCode")) ? cityCode : oldUser
                .get("cityCode"));

        String productId = "";
        String mStrBrandCode = "";
        String mStrProductTypeCode = "";
        // 组合版使用新的产品逻辑 by wangmc 20170309 isShare=false:组合版
        msg.put("subServInfos", checkResult.get("subServInfos"));
        msg.put("userId", userId);
        msg.put("eparchyCode", strEparchyCode);
        msg.put("serinalNamber", serialNumber);
        new N25ChgToCbssProduct().getMixProductInfo(msg, qryTermExchange, extObject);
        msg.put("ordersId", msg.get("subscribeId"));
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");
        productId = (String) msg.get("mProductId");
        mStrBrandCode = (String) msg.get("mBrandCode");
        mStrProductTypeCode = (String) msg.get("mProductTypeCode");

        System.out.println("=====融合组合版迁转新产品处理逻辑调用后extObject:" + extObject);
        // --------------------------BASE数据组织------------------------------------------
        // 主台账组织
        baseObject.put("inModeCode", msg.get("eModeCode"));
        baseObject.put("tradeTypeCode", "0448");
        // baseObject.put("TRADE_LCU_NAME","TCS_UserTransferReg");
        baseObject.put("eparchyCode", strEparchyCode);
        baseObject.put("areaCode", strEparchyCode);
        baseObject.put("tradeStaffId", tradeStaffId);
        baseObject.put("tradeDepartId", departId);
        baseObject.put("subscribeId", tradeId);
        baseObject.put("tradeId", msg.get("tradeId"));
        baseObject.put("userDiffCode", "00");
        baseObject.put("brandCode", mStrBrandCode);
        baseObject.put("serinalNamber", serialNumber);
        baseObject.put("productId", productId);
        baseObject.put("custId", custId);
        baseObject.put("custName", custName);
        baseObject.put("userId", userId);
        baseObject.put("acctId", acctId);
        baseObject.put("usecustId", custId);
        baseObject.put("acceptDate", acceptDate);
        baseObject.put("cityCode", cityCode);
        baseObject.put("netTypeCode", "50");
        // baseObject.put("CITY_CODE", userCityCode);
        baseObject.put("feeState", "0");
        baseObject.put("provinceCode", provinceCode);
        baseObject.put("contact", linkName);
        baseObject.put("contactAddress", linkAddress);
        baseObject.put("contactPhone", linkPhone);
        baseObject.put("termIp", "0.0.0.0");
        // baseObject.put("INVOICE_NO", "");
        // baseObject.put("OLDFEE", "");
        // baseObject.put("FEE", "");
        baseObject.put("mainDiscntCode", "");
        baseObject.put("productSpec", "");
        baseObject.put("nextDealTag", "Z");
        if (null != msg.get("remark")) {
            baseObject.put("remark", msg.get("remark"));
        }
        else {
            baseObject.put("remark", "");
        }
        baseObject.put("standardKindCode", staffInfo.get("channelType"));
        // BASE数据组织结束
        // ---------------------------------BASE数据组织结束--------------------------------------------------
        // EXT数据组织开始
        // 收入集团归集处理
        if ("1".equals(phoneTemplate.get("groupFlag"))) {// 收入集团归集
            Map jtcpMap = new HashMap();
            jtcpMap.put("province", "00" + msg.get("province"));
            jtcpMap.put("groupId", phoneTemplate.get("groupId"));
            jtcpMap.put("operatorId", msg.get("operatorId"));
            jtcpMap.put("city", ChangeCodeUtils.changeEparchy(msg));
            jtcpMap.put("district", msg.get("district"));
            jtcpMap.put("channelId", msg.get("channelId"));
            jtcpMap.put("channelType", msg.get("channelType"));
            extObject.put("tradeRelation", dealJtcp(exchange, jtcpMap, msg));
        }
        // 如果不存在客户信息则创建新客户
        if (!isExistCust) {
            // 台账客户
            Map custItem = new HashMap();
            custItem.put("sustId", custId);
            custItem.put("custName", custName);
            custItem.put("custType", "0");
            custItem.put("custPasswd", custPwd);
            custItem.put("developDepartId", oldUser.get("developDepartId"));
            custItem.put("custState", "0");
            custItem.put("psptTypeCode", certType);
            custItem.put("psptId", certCode);
            custItem.put("openLimit", "0");
            custItem.put("eparchyCode", strEparchyCode);
            custItem.put("cityCode", cityCode);
            custItem.put("inDate", acceptDate);
            custItem.put("creditValue", "0");
            custItem.put("rsrvTag1", oldCust.get("idMark")); // 2：实名-系统、3：实名-公安、4：实名-二代
            custItem.put("removeTag", "0");
            custItem.put("xDatatype", "NULL");

            Map custObjItem = new HashMap();
            custObjItem.put("item", custItem);
            extObject.put("tradeCustomer", custObjItem);

            // 台账个人客户
            Map custPersonItem = new HashMap();
            custPersonItem.put("custId", custId);
            custPersonItem.put("custName", custName);
            custPersonItem.put("psptTypeCode", certType);
            custPersonItem.put("psptId", certCode);
            custPersonItem.put("psptEndDate", certEndDate);
            custPersonItem.put("postCode", "");
            custPersonItem.put("psptAddr", certAddress);
            custPersonItem.put("postAddress", linkAddress);
            custPersonItem.put("phone", linkPhone);
            custPersonItem.put("homeAddress", "");
            custPersonItem.put("email", "");
            custPersonItem.put("sex", sex);
            custPersonItem.put("contact", linkName);
            custPersonItem.put("contactPhone", linkPhone);
            custPersonItem.put("birthday", "");
            custPersonItem.put("job", "");
            custPersonItem.put("workName", "");
            custPersonItem.put("marriage", "");
            custPersonItem.put("eparchyCode", strEparchyCode);
            custPersonItem.put("cityCode", cityCode);
            custPersonItem.put("removeTag", "0");
            custPersonItem.put("xDatatype", "NULL");

            Map custPersonObjItem = Maps.newHashMap();
            custPersonObjItem.put("item", custPersonItem);
            extObject.put("tradeCustPerson", custPersonObjItem);
        }
        String payModeCode = (String) oldAcct.get("payModeCode");
        String pmd = "0";
        // 台账托收
        List<Map> consignInfoList = (List<Map>) oldAcct.get("consign");
        if (!CollectionUtils.isEmpty(consignInfoList)) {
            Map consignInfo = consignInfoList.get(0);
            Map acctConsignItem = new HashMap();
            if (StringUtils.isNotBlank(TradeManagerUtils.getStringValue(consignInfo, "bankCode"))
                    && StringUtils.isNotBlank(TradeManagerUtils.getStringValue(consignInfo, "bankAcctNo"))) {
                acctConsignItem.put("acctId", acctId);
                acctConsignItem.put("payModeCode", payModeCode);
                acctConsignItem.put("consignMode", consignInfo.get("consignMode"));
                acctConsignItem.put("bankAcctNo", consignInfo.get("bankAcctNo"));
                acctConsignItem.put("bankAcctName", consignInfo.get("bankAcctName"));
                List<Map> accountInfoList = (List<Map>) oldAcct.get("accountInfo");
                if (!CollectionUtils.isEmpty(accountInfoList)) {
                    Map acc = accountInfoList.get(0);
                    acctConsignItem.put("bankBusiKind", acc.get("bankCardType"));// 银行账号类型
                    acctConsignItem.put("bankAcctNo", acc.get("accountLastFour"));// 银行卡号后四位
                    acctConsignItem.put("bankAcctName", acc.get("bankAccountName"));// 银行账户户名
                }
                acctConsignItem.put("superBankCode", consignInfo.get("superBankCode")); // new
                acctConsignItem.put("agreementNo", consignInfo.get("agreementNo")); // 签约协议号
                acctConsignItem.put("acctBalanceID", consignInfo.get("acctBalanceId")); // new
                acctConsignItem.put("assistantTag", "0");
                acctConsignItem.put("bankCode", consignInfo.get("bankCode"));
                acctConsignItem.put("startCycleId", cycleId);
                acctConsignItem.put("xDatatype", "NULL");
                Map acctConsignObjItem = new HashMap();
                acctConsignObjItem.put("item", acctConsignItem);
                extObject.put("tradeAcctConsign", acctConsignObjItem);
                pmd = payModeCode;
            }
        }
        if ("0".equals(msg.get("isNewAcct"))) {
            // 台账账户
            Map acctItem = new HashMap();
            acctItem.put("acctId", acctId);
            acctItem.put("payName", custName);
            acctItem.put("custId", custId);
            acctItem.put("acctPasswd", "");
            acctItem.put("payModeCode", pmd);
            acctItem.put("scoreValue", "0");
            acctItem.put("creditClassId", "0");
            acctItem.put("basicCreditValue", "0");
            acctItem.put("creditValue", "0");
            acctItem.put("creditControlId", "0");
            acctItem.put("openDate", acceptDate);
            acctItem.put("eparchyCode", strEparchyCode);
            acctItem.put("cityCode", cityCode);
            acctItem.put("removeTag", "0");
            acctItem.put("xDatatype", "NULL");

            Map acctObjItem = new HashMap();
            acctObjItem.put("item", acctItem);
            extObject.put("tradeAcct", acctObjItem);
        }

        // 台账付费关系
        Map payRelItem = new HashMap();
        payRelItem.put("acctId", acctId);
        payRelItem.put("userId", userId);
        payRelItem.put("payrelationId", payRelId);
        payRelItem.put("payitemCode", "-1");
        payRelItem.put("acctPriority", "0");
        payRelItem.put("userPriority", "0");
        payRelItem.put("bindType", "1");
        payRelItem.put("defaultTag", "1");
        payRelItem.put("startAcycId", cycleId);
        payRelItem.put("endAcycId", "203712");
        payRelItem.put("actTag", "1");
        payRelItem.put("limitType", "0");
        payRelItem.put("limit", "0");
        payRelItem.put("complementTag", "0");
        payRelItem.put("addupMethod", "0");
        payRelItem.put("addupMonths", "0");
        payRelItem.put("xDatatype", "NULL");

        Map payRelObjItem = new HashMap();
        payRelObjItem.put("item", payRelItem);
        extObject.put("tradePayrelation", payRelObjItem);

        // 用户号码取号码后六位
        String userPwd = serialNumber.toString().substring(serialNumber.toString().length() - 6);
        // 若ECS传密码则用ECS传入的密码
        if (StringUtils.isNotEmpty(newUserPwd)) {
            userPwd = newUserPwd;
        }
        String userEparhcyCode = strEparchyCode;
        String tempEparchy = (String) oldUser.get("eparchyCode");
        if (StringUtils.isNotBlank(tempEparchy) && tempEparchy.length() == 3) {
            userEparhcyCode = TradeManagerUtils.getExEparchyCode(province, provinceCode, tempEparchy);
        }
        if (StringUtils.isNotBlank(tempEparchy) && tempEparchy.length() == 4) {
            userEparhcyCode = tempEparchy;
        }

        Map userItem = new HashMap();
        userItem.put("usecustId", custId);
        userItem.put("custId", custId);
        userItem.put("userId", userId);
        userItem.put("brandCode", mStrBrandCode);
        userItem.put("productId", productId);
        userItem.put("eparchyCode", userEparhcyCode);
        userItem.put("cityCode", userCityCode);
        userItem.put("userDiffCode", "00");
        userItem.put("userTypeCode", "0");
        userItem.put("serialNumber", serialNumber);
        userItem.put("netTypeCode", netTypeCode);
        userItem.put("scoreValue", "0");
        userItem.put("creditClass", creditClass);// 信用等级从老用户取
        userItem.put("basicCreditValue", "0");
        userItem.put("creditValue", "0");
        userItem.put("acctTag", "0");
        userItem.put("prepayTag", strPrepayTag);
        userItem.put("inDate", oldInDate);
        userItem.put("openDate", oldInDate);
        userItem.put("openMode", "0");
        userItem.put("openDepartId", departId);
        userItem.put("inDepartId", departId);
        // RTJ2018030200048-aop支持融合新装业务成员迁转时在cbss开户时间记录为bss原始开户时间的需求
        userItem.put("inDate", oldUser.get("inDate"));
        userItem.put("openDate", oldUser.get("openDate"));

        // 推荐人编码
        Map phoneRecomInfo = (Map) phoneTemplate.get("phoneRecomInfo");
        if (null == phoneRecomInfo || phoneRecomInfo.isEmpty()) {
            userItem.put("developDepartId", oldUser.get("developDepartId"));
            String devStaff = (String) oldUser.get("developStaffId");
            if (StringUtils.isNotBlank(devStaff)) {
                userItem.put("developStaffId", devStaff);
                userItem.put("inStaffId", devStaff);
            }
            else {
                userItem.put("developStaffId", tradeStaffId);
                userItem.put("inStaffId", tradeStaffId);
            }
        }
        else {
            userItem.put("developDepartId", phoneRecomInfo.get("recomDepartId"));
            userItem.put("developStaffId", phoneRecomInfo.get("recomPersonId"));
            userItem.put("inStaffId", phoneRecomInfo.get("recomPersonId"));
        }
        userItem.put("openStaffId", tradeStaffId);
        userItem.put("removeTag", "0");
        userItem.put("userStateCodeset", "0");
        userItem.put("mputeMonthFee", "0");
        userItem.put("productTypeCode", mStrProductTypeCode);
        userItem.put("userPasswd", userPwd);
        userItem.put("xDatatype", "NULL");
        userItem.put("assureCustId", "");

        Map userObjItem = new HashMap();
        userObjItem.put("item", userItem);
        extObject.put("tradeUser", userObjItem);

        // USER_SUBITEM 表数据拼装开始
        appendData.put("CITY_CODE", cityCode);
        appendData.put("EPARCHY_CODE", strEparchyCode);
        appendData.put("USER_PASSWD", userPwd);
        appendData.put("OLD_ACCT_ID_SF", oldAcct.get("acctId"));// 省份帐户 // 原有信息 省份帐户
        appendData.put("OLD_COMBO_FEE", oldUser.get("comboFee"));// 省份产品抵消值
        appendData.put("OLD_COMBO_NAME", oldUser.get("comboName"));// 省份产品名称
        appendData.put("OLD_CUST_ID_SF", oldCust.get("custId"));// 省份客户ID
        appendData.put("OLD_NET_TYPE_CODE_SF", oldUser.get("serviceClassCode"));// 原网别
        appendData.put("OLD_USER_ID_SF", oldUser.get("userId"));// 省份实例ID
        appendData.put("IS_TRANSMIT", "1");// 1
        if (null == phoneRecomInfo || phoneRecomInfo.isEmpty()) {
            appendData.put("DEVELOP_STAFF_ID", oldUser.get("developStaffId"));// 发展员工
            appendData.put("DEVELOP_DEPART_ID", oldUser.get("developDepartId"));// 发展部门

        }
        else {
            appendData.put("DEVELOP_STAFF_ID", phoneRecomInfo.get("recomPersonId"));// 发展员工
            appendData.put("DEVELOP_DEPART_ID", phoneRecomInfo.get("recomDepartId"));// 发展部门
        }
        appendData.put("MAIN_PRODUCT_ID", productId);// 主产品ID
        appendData.put("E_IN_MODE", modeCode);

        // 23转4靓号处理
        List<Map> superNumberList = (List<Map>) oldUser.get("superNumber");
        if (!CollectionUtils.isEmpty(superNumberList)) {
            // appendData.put("LOW_COST", oldUser.getSuperNumber().get(0).getMonthMinConsume());//靓号费
            // 8个8资费
            Map superNum = superNumberList.get(0);
            Map discntItem = new HashMap();
            String stDate = GetDateUtils.transDate((String) superNum.get("startDate"), 19);
            String nextMonth = GetDateUtils.getNextMonthFirstDayFormat();
            String maxEndDate = DateUtils.addMonths(nextMonth, "120");
            if (maxEndDate.compareTo((String) superNum.get("finishDate")) > 0) {
                maxEndDate = (String) superNum.get("finishDate");
            }
            String edDate = GetDateUtils.transDate(maxEndDate, 19);
            String itemId = TradeManagerUtils.getSequence(strEparchyCode, "SEQ_ITEM_ID");

            discntItem.put("id", userId);
            discntItem.put("idType", "1");
            discntItem.put("productId", "-1");
            discntItem.put("packageId", "-1");
            discntItem.put("discntCode", "88888888");
            discntItem.put("specTag", "0");
            discntItem.put("relationTypeCode", "");
            discntItem.put("itemId", itemId);
            discntItem.put("modifyTag", "0");
            discntItem.put("startDate", stDate);//
            discntItem.put("endDate", edDate);
            discntItem.put("userIdA", "-1");
            discntItem.put("xDatatype", "NULL");
            extObject = TradeManagerUtils.addTab("tradeDiscnt", extObject, discntItem);

            Map costItem = new HashMap();
            costItem.put("attrTypeCode", "3");
            costItem.put("transeId", "3");// 3-受理月末迁转 add by wangrj3
            costItem.put("attrCode", "lowCost");
            costItem.put("itemId", itemId);
            costItem.put("attrValue", superNum.get("monthMinConsume"));
            costItem.put("xDatatype", "NULL");
            costItem.put("startDate", stDate);
            costItem.put("endDate", edDate);

            extObject = TradeManagerUtils.addTab("tradeSubItem", extObject, costItem);
        }
        else {
            String itemId = TradeManagerUtils.getSequence(strEparchyCode, "SEQ_ITEM_ID");
            Map costItem = new HashMap();
            costItem.put("attrTypeCode", "0");
            costItem.put("attrCode", "transeId");
            costItem.put("itemId", itemId);
            costItem.put("attrValue", "3");
            costItem.put("xDatatype", "NULL");
            extObject = TradeManagerUtils.addTab("tradeSubItem", extObject, costItem);
            Map workIdMap = new HashMap();
            workIdMap.put("attrTypeCode", "0");
            workIdMap.put("attrCode", "WorkId");
            workIdMap.put("itemId", itemId);
            workIdMap.put("attrValue", "1");
            workIdMap.put("xDatatype", "NULL");
            extObject = TradeManagerUtils.addTab("tradeSubItem", extObject, workIdMap);
        }
        // /TRADE_TYPE_CODE,NET_TYPE_CODE,BRAND_CODE,PRODUCT_ID,EPARCHY_CODE,PROVINCE_CODE,USER_ID
        // baseObject.put("provinceCode", provinceCode);
        // baseObject.put("subSysCode", "MALL");
        // baseObject.put("TRADE_TYPE_CODE", ((String) baseObject.get("tradeTypeCode")).substring(1));
        System.out.println("========================tradeSubItem" + extObject);
        appendMap.put("APPEND_MAP", appendData);
        // USER_SUBITEM 表数据拼装结束
        // String advancePay = "";
        // String paymentId = "";
        // String foregift = "";
        // String foregiftCode = "";

        // /--------TRADE_POST---------------------
        if (postInfo != null) {
            // Map.get(key)需要判空和String类型转换 modiyfy by wangrj3
            if (postInfo.get("postTag") != null && "1".equals(postInfo.get("postTag").toString())) {
                Map tradePost = new HashMap();
                tradePost.put("xDatatype", "NULL");
                tradePost.put("id", userId);
                String[] copyArray = { "idType", "postName", "postTag", "postContent", "postTypeSet", "postCyc",
                        "postAddress", "postCode", "email", "faxNbr" };
                MapUtils.arrayPut(tradePost, postInfo, copyArray);
                tradePost.put("startDate", realStartDate);
                tradePost.put("endDate", "2050-12-31 23:59:59");
                extObject = TradeManagerUtils.addTab("tradePost", extObject, tradePost);
            }
        }

        // /拼装SUB_ITEM TRADE_ITEM 台账 开始

        TradeManagerUtils.dealItemInfo(appendMap, baseObject, extObject);
        System.out.println("========================tradeSubItem1" + extObject);
        // /拼装SUB_ITEM 台账 结束

        // // 准备参数
        fillRecordInExt(extObject, msg.get("aloneTcsCompIndex")); // 记录工单序号，1为最后一笔工单
        Map tradeMap = Maps.newHashMap();
        msg.put("base", baseObject);
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            extObject.put("tradeRelation", preDataForTradeRelation(msg));
            List<Map> subItemList = (List<Map>) baseObject.get("tradeSubItem");
            if (!IsEmptyUtils.isEmpty(subItemList)) {
                subItemList.add(MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", "MAIN_CARD_TAG",
                        "attrValue", "0", "itemId", msg.get("userId"), "startDate", msg.get("nextMon1stDay"),
                        "endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
            }
            List<Map> itemList = (List<Map>) msg.get("tradeItem");
            if (!IsEmptyUtils.isEmpty(subItemList)) {
                itemList.add(MapUtils.asMap("xDatatype", "NULL", "attrCode", "MAIN_CARD_TAG", "attrValue", "0"));
            }
        }
        // 增加行销标示
        if ("1".equals(msg.get("markingTag"))) {
            TradeManagerUtils.addTab("tradeItem", extObject,
                    MapUtils.asMap("xDatatype", "NULL", "attrCode", "MARKING_APP", "attrValue", "1"));
        }
        // RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求（移网迁转周转金）
        if ("0".equals(msg.get("deductionTag"))) {
            TradeManagerUtils.addTab("tradeItem", extObject,
                    MapUtils.asMap("xDatatype", "NULL", "attrCode", "FEE_TYPE", "attrValue", "1"));
        }
        // RHA2018010500038-融合业务后激活需求
        // 压单标志为1且为写卡前置接口才生效
        if ("1".equals(msg.get("delayTag")) && "smno".equals(exchange.getMethodCode())) {
            TradeManagerUtils.addTab("tradeItem", extObject, MapUtils.asMap("xDatatype", "NULL", "attrCode", "E_DELAY_TIME_CEL", "attrValue", "1"));
        }
        // 订单是否同步到电子渠道激活
        if ("1".equals(msg.get("isAfterActivation")) && "smno".equals(exchange.getMethodCode())) {
            TradeManagerUtils.addTab("tradeItem", extObject, MapUtils.asMap("xDatatype", "NULL", "attrCode", "IS_AFTER_ACTIVATION", "attrValue", "1"));
        }

        dealMap2List(extObject, "tradeSubItem");
        dealMap2List(extObject, "tradeItem");
        dealMap2List(extObject, "tradePost");
        dealMap2List(extObject, "tradeDiscnt");
        dealMap2List(extObject, "tradeSp");
        dealMap2List(extObject, "tradeProduct");
        dealMap2List(extObject, "tradeProductType");
        dealMap2List(extObject, "tradeSvc");
        dealMap2List(extObject, "tradePurchase");
        dealMap2List(extObject, "tradeElement");
        dealMap2List(extObject, "tradefeeSub");
        System.out.println("extObject==================" + extObject);
        msg.put("ext", extObject);
        // 处理产品相关节点信息

        msg.put("trade", tradeMap);
        msg.put("ordersId", tradeId);
        msg.put("serinalNamber", serialNumber);
        // msg.put("SERVICE_CLASS_CODE", "");
        msg.put("operTypeCode", "0");
        msg.put("code", "0000");
        msg.put("detail", "转出校验成功");
        // msg.remove("serviceClassCode");
        msg.put("serviceClassCode", "00CP");
        return msg;
    }

    private Map dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        LanUtils lan = new LanUtils();
        try {
            lan.preData(pmp[6], jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// :地址
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
        Map item = new HashMap();
        List<Map> relaItem = new ArrayList<Map>();
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
        tradeRelation.put("tradeRelation", relaItem);
        return tradeRelation;

    }

    private void dealMap2List(Map extObject, String key) {
        if (null == extObject.get(key)) {
            return;
        }
        List<Map> keyList = (List<Map>) extObject.get(key);
        if (0 == keyList.size()) {
            return;
        }
        List<Map> item = new ArrayList<Map>();
        for (Map keyMap : keyList) {
            item.add(keyMap);
        }
        Map tempMap = MapUtils.asMap("item", item);
        extObject.put(key, tempMap);
        System.out.println("1111111111111111222222222" + tempMap);
    }

    /**
     * 根据号码获取号码操作信息
     * @param serialNumber
     * @return
     */
    private Map getOperInfo(Object serialNumber) {
        Map inparam = new HashMap();
        inparam.put("serialNumber", serialNumber);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.selectOperInfo(inparam);
    }

    /**
     * 
     */
    private void fillRecordInExt(Map extObject, Object i) {
        Map objItem = new HashMap();
        objItem.put("attrCode", "ALONE_TCS_COMP_INDEX");
        objItem.put("attrValue", i);
        objItem.put("xDatatype", "NULL");
        TradeManagerUtils.addTab("tradeItem", extObject, objItem);
    }

    // 拼装TRADE_RELATION
    private Map preDataForTradeRelation(Map msg) {
        try {
            Map tradeRelation = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            // 主卡开户流程准备参数
            item.put("idA", msg.get("userId"));
            item.put("serialNumberA", msg.get("serialNumber"));
            item.put("roleCodeB", "1");
            item.put("relationAttr", "0");
            item.put("xDatatype", "NULL");
            item.put("relationTypeCode", "ZF");
            item.put("serialNumberB", msg.get("serialNumber"));
            item.put("idB", msg.get("userId"));
            item.put("roleCodeA", "0");
            item.put("modifyTag", "0");
            item.put("startDate", msg.get("realStartDate"));
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);

            Map item2 = new HashMap();
            // 主卡开户流程准备参数
            item2.put("idA", msg.get("XNUserId"));
            item2.put("serialNumberA", msg.get("virtualNumber"));
            item2.put("roleCodeB", "1");
            // item2.put("relationAttr", "0");
            item2.put("xDatatype", "NULL");
            item2.put("relationTypeCode", "8900");
            item2.put("serialNumberB", msg.get("serialNumber"));
            item2.put("idB", msg.get("userId"));
            item2.put("roleCodeA", "0");
            item2.put("modifyTag", "0");
            item2.put("startDate", msg.get("realStartDate"));
            item2.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item2);
            tradeRelation.put("item", itemList);
            return tradeRelation;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RELATION节点报错" + e.getMessage());
        }

    }

    /**
     * 23转4转出校验,不抛错
     * @param exchange
     * @param needException
     * @return
     * @throws Exception
     */
    public Map check23GTransfer(Exchange exchange, boolean needException) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(pmp[5], exchange);
        // 直连全业务
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.UserTransferSer");
        if (!needException) {// 不抛异常
            Xml2Json4FbsNoErrorMappingProcessor proc = new Xml2Json4FbsNoErrorMappingProcessor();
            proc.applyParams(new String[] { "ecaop.23To4.check.template" });
            proc.process(exchange);
        }
        else {
            lan.xml2Json("ecaop.23To4.check.template", exchange);
        }
        Map checkResult = exchange.getOut().getBody(Map.class);
        // 校验是否没有返回三户信息
        String[] infoKeys = new String[] { "userInfo", "custInfo", "acctInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        Map temp = new HashMap();
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty(checkResult.get(infoKey))) {
                throw new EcAopServerBizException("9999", "调省份23转4校验未返回" + errorDetail.get(infoKey));
            }
            temp.put(infoKey, ((List<Map>) checkResult.get(infoKey)).get(0));
        }
        return temp;
    }
}
