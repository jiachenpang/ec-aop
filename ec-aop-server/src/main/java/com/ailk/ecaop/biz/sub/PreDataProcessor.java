package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.util.log.Log;
import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.sub.gdjk.ChangeProduct4GProcessor;
import com.ailk.ecaop.biz.sub.gdjk.MixChangeProduct4GProcessor;
import com.ailk.ecaop.biz.sub.gdjk.NewTransTo4GApplyProcessor;
import com.ailk.ecaop.biz.sub.gdjk.OpenApply4GProcessor;
import com.ailk.ecaop.biz.sub.gdjk.TransTo4GApplyProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class PreDataProcessor {

    public Map preMobileData(Exchange exchange, Object str, ParametersMappingProcessor[] pmp) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        String apptx = (String) ((Map) body.get("msg")).get("apptx");
        Log.info("mixtest,str=" + str + "body=" + body);
        if ("0".equals(str)) {
            return new OpenApply4GProcessor().openApp4GProcess(exchange, pmp);
        }
        if ("1".equals(str)) {
            // 获取到的是走老逻辑的appCode,若获取值为ZZZZ表示全量走老逻辑
            String useOldApp = EcAopConfigLoader.getStr("ecaop.global.param.product.database.appcode.mix");
            if ("ZZZZ".equals(useOldApp) || useOldApp.contains(exchange.getAppCode())) {
                return new ChangeProduct4GProcessor().chgProdPreDeal4G(exchange, pmp);
            }
            return new MixChangeProduct4GProcessor().newDealProduct(exchange, pmp);
        }
        if ("2".equals(str)) {
            // 获取到的是走老逻辑的appCode,若获取值为ZZZZ表示全量走老逻辑
            String useOldApp = EcAopConfigLoader.getStr("ecaop.global.param.product.database.appcode.mix.23t4");
            if ("ZZZZ".equals(useOldApp) || useOldApp.contains(exchange.getAppCode())) {
                new TransTo4GApplyProcessor(pmp).process(exchange);
                return exchange.getOut().getBody(Map.class);
            }
            new NewTransTo4GApplyProcessor(pmp).process(exchange);
            return exchange.getOut().getBody(Map.class);
        }
        return null;
    }

    public void preMixData(Map inputMap, String appCode) {
        Map ext = preMixExt(inputMap, appCode);
        inputMap.put("ext", ext);
        Map base = preMixBase(inputMap, appCode);
        base.put("brandCode", ext.get("brandCode"));

        // base下产品id，取tradeProduct下主产品的id（默认89017299） lixl 2016-6-7
        List<Map> tradeProductItem = (List<Map>) ((Map) ext.get("tradeProduct")).get("item");
        for (Map item : tradeProductItem) {
            if ("00".equals(item.get("productMode"))) {
                base.put("productId", item.get("productId"));
            }
        }
        if (null == base.get("productId") || "".equals(base.get("productId"))) {
            base.put("productId", "89017299");
        }
        inputMap.put("base", base);
        inputMap.put("mixVmproductId", ext.get("mproductId"));
    }

    private Map preMixExt(Map inputMap, String appCode) {
        Map template = (Map) inputMap.get("mixTemplate");
        List<Map> productList = (List<Map>) template.get("productInfo");
        Map ext = new HashMap();
        inputMap.put("brandCode", ext.get("brandCode"));
        inputMap.put("productTypeCode", ext.get("productTypeCode"));
        inputMap.put("mproductId", ext.get("mproductId"));
        Object custId = inputMap.get("custId");
        // 改为调用融合专用的处理产品方法 by wangmc 20170324
        ext.putAll(preProductInfo4Mix(productList, "00" + inputMap.get("province"), inputMap));
        System.out.println("mixBrand11" + ext);
        System.out.println("mixBrand22" + inputMap);
        inputMap.put("custId", custId);
        Map tradeUser = preMixTradeUsr(inputMap);
        Map tradePayrelationItem = new HashMap();
        List<Map> tradePayrelationItemList = new ArrayList<Map>();
        tradePayrelationItem.put("payitemCode", "-1");
        tradePayrelationItem.put("acctPriority", "0");
        tradePayrelationItem.put("userPriority", "0");
        tradePayrelationItem.put("bindType", "1");
        tradePayrelationItem.put("defaultTag", "1");
        tradePayrelationItem.put("limitType", "0");
        tradePayrelationItem.put("limit", "0");
        tradePayrelationItem.put("complementTag", "0");
        tradePayrelationItem.put("addupMonths", "0");
        tradePayrelationItem.put("addupMethod", "0");
        tradePayrelationItem.put("acctId", inputMap.get("acctId"));
        tradePayrelationItem.put("payrelationId", inputMap.get("payRelationId"));
        tradePayrelationItem.put("actTag", "1");
        tradePayrelationItemList.add(tradePayrelationItem);
        Map tradePayrelation = new HashMap();
        tradePayrelation.put("item", tradePayrelationItemList);
        ext.put("tradeUser", tradeUser);
        // if (!"1".equals(inputMap.get("isNewAcct"))) {// 1：继承老账户
        // ext.put("tradeAcct", preMixTradeAcct(inputMap));
        // }
        ext.put("tradeItem", preMixTradeItem(inputMap, appCode));
        ext.put("tradeSubItem", preMixTradeSubItem(inputMap));
        ext.put("tradePayrelation", tradePayrelation);
        return ext;
    }

    private Map preMixTradeAcct(Map inputMap) {
        Map tradeAcct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("acctId", inputMap.get("acctId"));
        // 补custId字段 add by wangrj3
        item.put("custId", inputMap.get("custId"));
        item.put("payName", ((Map) inputMap.get("custCheckRetMap")).get("custName"));
        item.put("debutyUserId", inputMap.get("userId"));
        item.put("openDate", GetDateUtils.getDate());
        MapUtils.arrayPutFix("0", item, new String[] { "payModeCode", "scoreValue", "creditClassId",
                "basicCreditValue", "creditValue", "creditControlId", "debutyCode", "removeTag" });
        itemList.add(item);
        tradeAcct.put("item", itemList);
        return tradeAcct;
    }

    private Map preMixTradeSubItem(Map inputMap) {
        LanUtils lan = new LanUtils();
        Map tradeSubItem = new HashMap();
        List items = new ArrayList();
        // Map template = new HashMap();
        // Map serialNumber = new HashMap();
        String id = (String) inputMap.get("userId");
        // new Code
        Map mainPhoneInfo = getMainPhoneTemplate((List<Map>) inputMap.get("phoneTemplate"));
        items.add(lan.createTradeSubItem("MAIN_USER_TAG", mainPhoneInfo.get("serialNumber"), id));
        items.add(lan.createTradeSubItem("MAIN_USER_NETCODE", "50", id));
        Map phoneCustInfo = (Map) mainPhoneInfo.get("phoneCustInfo");
        if (null == phoneCustInfo) {
            phoneCustInfo = ((List<Map>) inputMap.get("phoneTemplate")).get(0);
        }
        items.add(lan.createTradeSubItem("LINK_NAME", phoneCustInfo.get("contactPerson"), id));
        items.add(lan.createTradeSubItem("OTHERCONTACT", "", id));
        items.add(lan.createTradeSubItem("LINK_PHONE", phoneCustInfo.get("contactPhone"), id));
        List<Map> subItemList = (List<Map>) inputMap.get("subItem");
        if (!IsEmptyUtils.isEmpty(subItemList)) {
            for (Map subItem : subItemList) {
                items.add(lan.createTradeSubItem((String) subItem.get("attrCode"), subItem.get("attrValue"), id));
            }
        }

        // if (null != inputMap.get("mixTemplate")) {
        // template = (Map) inputMap.get("mixTemplate");
        // serialNumber = lan.createTradeSubItem("serialNumber", "1231231231231231", id);
        // }
        // else if (null != inputMap.get("broadBandTemplate"))
        // {
        // template = (Map) inputMap.get("broadBandTemplate");
        // serialNumber = lan.createTradeSubItem("serialNumber", template.get("acctSerialNumber"), id);
        // }
        // Map addressId = lan.createTradeSubItem("ADDRESS_ID", "01000105424828278", id);
        // Map shareNbr = lan.createTradeSubItem("SHARE_NBR", "", id);
        // Map useType = lan.createTradeSubItem("USETYPE", "1", id);
        // Map muduleExchId = lan.createTradeSubItem("MODULE_EXCH_ID", "", id);
        // Map wopayMoney = lan.createTradeSubItem("WOPAY_MONEY", "", id);
        // Map areaCode = lan.createTradeSubItem("AREA_CODE", "", id);
        // Map accessType = lan.createTradeSubItem("ACCESS_TYPE", "B31", id);
        // Map timeLimitId = lan.createTradeSubItem("TIME_LIMIT_ID", "2", id);
        // Map pointExchId = lan.createTradeSubItem("POINT_EXCH_ID", "01795", id);
        // Map userPwd = lan.createTradeSubItem("USER_PASSWD", "123456", id);
        // Map connectNetMode = lan.createTradeSubItem("CONNECTNETMODE", "1", id);
        // Map isWall = lan.createTradeSubItem("ISWAIL", "0", id);
        // Map tmlSn = lan.createTradeSubItem("TERMINAL_SN", "", id);
        // Map tmlMac = lan.createTradeSubItem("TERMINAL_MAC", "", id);
        // Map switchExchId = lan.createTradeSubItem("SWITCH_EXCH_ID", "", id);
        // Map isWopay = lan.createTradeSubItem("ISWOPAY", "0", id);
        // Map tmlSrcMode = lan.createTradeSubItem("TERMINALSRC_MODE", "A003", id);
        // Map areaExchId = lan.createTradeSubItem("AREA_EXCH_ID", "CLJ01000105", id);
        // Map mofficeId = lan.createTradeSubItem("MOFFICE_ID", "1", id);
        // Map communitId = lan.createTradeSubItem("COMMUNIT_ID", "0", id);
        // Map usrTypeCode = lan.createTradeSubItem("USER_TYPE_CODE", "0", id);
        // Map cbAccessType = lan.createTradeSubItem("CB_ACCESS_TYPE", "A11", id);
        // Map acctNbr = lan.createTradeSubItem("ACCT_NBR", "", id);
        // Map commpanyNbr = lan.createTradeSubItem("COMMPANY_NBR", "", id);
        // Map detailInstallAddress = lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", "", id);
        // Map townFlag = lan.createTradeSubItem("TOWN_FLAG", "C", id);
        // Map tmlModel = lan.createTradeSubItem("TERMINAL_MODEL", "", id);
        // Map speed = lan.createTradeSubItem("SPEED", "4", id);
        // Map locNetCode = lan.createTradeSubItem("LOCAL_NET_CODE", "0531", id);
        // Map exceptRate = lan.createTradeSubItem("EXPECT_RATE", "", id);
        // Map installAddr = lan.createTradeSubItem("INSTALL_ADDRESS", "", id);
        // Map tmlType = lan.createTradeSubItem("TERMINAL_TYPE", "0", id);
        // Map tmlBrand = lan.createTradeSubItem("TERMINAL_BRAND", "", id);
        // Map communitName = lan.createTradeSubItem("COMMUNIT_NAME", "", id);
        // Map initPwd = lan.createTradeSubItem("INIT_PASSWD", "1", id);
        // Map collType = lan.createTradeSubItem("COLLINEAR_TYPE", "X3", id);
        // Map acctPwd = lan.createTradeSubItem("ACCT_PASSWD", "123456", id);
        //
        // items.add(addressId);
        // items.add(shareNbr);
        // items.add(useType);
        // items.add(muduleExchId);
        // items.add(wopayMoney);
        // items.add(areaCode);
        // // items.add(serialNumber);
        // items.add(accessType);
        // items.add(timeLimitId);
        // items.add(pointExchId);
        // items.add(userPwd);
        // items.add(connectNetMode);
        // items.add(isWall);
        // items.add(tmlSn);
        // items.add(tmlMac);
        // items.add(switchExchId);
        // items.add(isWopay);
        // items.add(tmlSrcMode);
        // items.add(areaExchId);
        // items.add(mofficeId);
        // items.add(communitId);
        // items.add(usrTypeCode);
        // items.add(cbAccessType);
        // items.add(acctNbr);
        // items.add(commpanyNbr);
        // items.add(detailInstallAddress);
        // items.add(townFlag);
        // items.add(tmlModel);
        // items.add(speed);
        // items.add(locNetCode);
        // items.add(exceptRate);
        // items.add(installAddr);
        // items.add(tmlType);
        // items.add(tmlBrand);
        // items.add(communitName);
        // items.add(initPwd);
        // items.add(collType);
        // items.add(acctPwd);
        tradeSubItem.put("item", items);
        return tradeSubItem;
    }

    private Map getMainPhoneTemplate(List<Map> phoneTemplate) {
        for (Map phone : phoneTemplate) {
            if ("0".equals(phone.get("mainNumberTag"))) {
                return phone;
            }
        }
        return new HashMap();
    }

    private Map preMixTradeItem(Map inputMap, String appCode) {
        LanUtils lan = new LanUtils();
        // items.add(lan.createAttrInfoNoTime("REL_COMP_PROD_ID", "89017300"));
        // items.add(lan.createAttrInfoNoTime("COMP_DEAL_STATE", "0"));
        // items.add(lan.createAttrInfoNoTime("ROLE_CODE_B", "4"));
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createAttrInfoNoTime("STANDARD_KIND_CODE", "1"));
        // items.add(lan.createAttrInfoNoTime("PH_NUM", ""));
        // items.add(lan.createAttrInfoNoTime("EXTRA_INFO", ""));
        // items.add(lan.createAttrInfoNoTime("OPER_CODE", "1"));
        // items.add(lan.createAttrInfoNoTime("USER_PASSWD", "123456"));
        // items.add(lan.createAttrInfoNoTime("REOPEN_TAG", "2"));
        // items.add(lan.createAttrInfoNoTime("NEW_PASSWD", "123456"));
        // items.add(lan.createAttrInfoNoTime("EXISTS_ACCT", "1"));
        // items.add(lan.createAttrInfoNoTime("SFGX_2060", "N"));
        // items.add(lan.createAttrInfoNoTime("GXLX_TANGSZ", ""));
        // items.add(lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "1"));
        Map mainPhone = getMainPhoneTemplate((List<Map>) inputMap.get("phoneTemplate"));
        if (new ChangeCodeUtils().isWOPre(appCode)) {
            items.add(lan.createAttrInfoNoTime("WORK_TRADE_ID", mainPhone.get("ordersId")));
        }
        items.add(lan.createAttrInfoNoTime("MAIN_USER_TAG", mainPhone.get("serialNumber")));
        items.add(lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", inputMap.get("aloneTcsCompIndex")));
        Map phoneRecomInfo = (Map) inputMap.get("phoneRecomInfo");
        if (null == phoneRecomInfo) {
            throw new EcAopServerBizException("9999", "移网成员下发展人信息为空");
        }
        // 增加行销装备标示处理，by：cuij
        System.out.println("虚拟预提交行销标示" + inputMap.get("markingTag"));
        if ("1".equals(inputMap.get("markingTag"))) {
            items.add(lan.createAttrInfoNoTime("MARKING_APP", "1"));
        }
        // RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求（虚拟预提交周转金）
        if ("0".equals(inputMap.get("deductionTag")) && "smno".equals(inputMap.get("smnoMethodCode"))) {
            items.add(lan.createAttrInfoNoTime("FEE_TYPE", "1"));
        }
        Object mixrecomPersonId = inputMap.get("mixrecomPersonId");
        Object mixrecomDepartId = inputMap.get("mixrecomDepartId");
        if (mixrecomPersonId != null && mixrecomDepartId != null) {
            items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", mixrecomPersonId));
            items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", mixrecomDepartId));
        }
        else {
            items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", phoneRecomInfo.get("recomPersonId")));
            items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", phoneRecomInfo.get("recomDepartId")));
        }
        items.add(lan.createAttrInfoNoTime("PRE_START_TIME", GetDateUtils.getDate()));
        items.add(lan.createAttrInfoNoTime("SUB_TYPE", "0"));
        // RHQ2018080600048-CBSS收费明细报表-蜂行动

        List<Map> tradeItemList = (List<Map>) inputMap.get("tradeItem");
        if (!IsEmptyUtils.isEmpty(tradeItemList)) {
            for (Map tradeItem : tradeItemList) {
                items.add(LanUtils.createTradeItem((String) tradeItem.get("attrCode"), tradeItem.get("attrValue")));
            }
        }

        return MapUtils.asMap("item", items);
    }

    private Map preMixTradeUsr(Map inputMap) {
        String now = GetDateUtils.getDate();
        Object phoneRecomInfo = inputMap.get("phoneRecomInfo");
        Map tradeUser = MapUtils.asMap("xDatatype", MagicNumber.STRING_OF_NULL, "cityCode", inputMap.get("district"),
                "userPasswd", "123456", "userTypeCode", "C", "scoreValue", "0", "basicCreditValue", "0", "creditValue",
                "0", "acctTag", "0", "prepayTag", "0", "inDate", now, "openDate", now, "openMode", "0", "openDepartId",
                inputMap.get("channelId"), "openStaffId", inputMap.get("operatorId"), "removeTag", "0",
                "userStateCodeset", "0", "mputeMonthFee", "0", "developDate", now, "developEparchyCode",
                inputMap.get("city"), "developCityCode", inputMap.get("district"), "inNetMode", "0", "inStaffId",
                inputMap.get("operatorId"), "inDepartId", inputMap.get("channelId"));
        String[] copyArray = { "custId", "userId", "brandCode", "productTypeCode" };
        MapUtils.arrayPut(tradeUser, inputMap, copyArray);
        System.out.println("mixBrand33" + inputMap);
        tradeUser.put("brandCode", ((List<Map>) inputMap.get("productList")).get(0).get("brandCode"));
        tradeUser.put("productTypeCode", ((List<Map>) inputMap.get("productTypeList")).get(0).get("productTypeCode"));
        tradeUser.put("eparchyCode", inputMap.get("city"));
        tradeUser.put("pruductId", ((List<Map>) inputMap.get("productList")).get(0).get("pruductId"));
        System.out.println("mixBrand44" + tradeUser);
        /**
         * RSD2017101600021-AOP融合开户接口融合虚拟用户支持发展人信息录入功能需求申请 create by zhaok Date 2017-11-1 START
         * 如果外围传入融合发展人信息，就下发融合发展人的信息，没有还按照老流程下发传入移网发展人信息
         */
        Map mixTemplate = (Map) inputMap.get("mixTemplate");
        List<Map> mixRecomInfo = (List<Map>) mixTemplate.get("mixRecomInfo");
        if (null != mixRecomInfo && mixRecomInfo.size() > 0) {
            Map mixRecomInfoMap = mixRecomInfo.get(0);
            tradeUser.put("developStaffId", mixRecomInfoMap.get("recomPersonId"));
            tradeUser.put("developDepartId", mixRecomInfoMap.get("recomDepartId"));
            inputMap.put("mixrecomPersonId", mixRecomInfoMap.get("recomPersonId"));// 下面有用
            inputMap.put("mixrecomDepartId", mixRecomInfoMap.get("recomDepartId"));// 下面有用
        }
        else if (null != phoneRecomInfo) {
            // RSD2017101600021-AOP融合开户接口融合虚拟用户支持发展人信息录入功能需求申请 create by zhaok Date 2017-11-1 END
            Map phoneRecomInfoMap = (Map) phoneRecomInfo;
            tradeUser.put("developStaffId", phoneRecomInfoMap.get("recomPersonId"));
            tradeUser.put("developDepartId", phoneRecomInfoMap.get("recomDepartId"));
        }
        return MapUtils.asMap("item", tradeUser);

    }

    public String getRemark(Map inputMap) {
        // 备注字段放在para里
        String remark = "";
        List<Map> paraList = (List<Map>) inputMap.get("para");
        if (null != paraList && paraList.size() > 0) {
            for (Map para : paraList) {
                if ("remark".equalsIgnoreCase(para.get("paraId").toString())) {
                    remark = para.get("paraValue") + "";
                }
            }
        }
        return remark;
    }

    private Map preMixBase(Map inputMap, String appCode) {
        Map template = new HashMap();
        Map custInfo = new HashMap();
        Map product = new HashMap();
        template = (Map) inputMap.get("mixTemplate");
        custInfo = (Map) template.get("mixCustInfo");
        List<Map> productList = (List<Map>) template.get("productInfo");
        product = productList.get(0);
        String productId = product.get("productId") + "";
        Map custCheckRetMap = (Map) inputMap.get("custCheckRetMap");
        Map base = new HashMap();
        base.put("subscribeId", inputMap.get("tradeId"));
        base.put("tradeId", inputMap.get("tradeId"));
        base.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("netTypeCode", "00CP");
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        // base.put("productId", productId);
        // 暂时写死，用于验证cbss所述情况 lixl 2016-06-06
        // base.put("productId", "89017299");
        base.put("custId", custCheckRetMap.get("custId"));
        base.put("usecustId", custCheckRetMap.get("custId"));
        base.put("custName", custCheckRetMap.get("custName"));
        base.put("serinalNamber", inputMap.get("serialNumber"));
        base.put("eparchyCode", inputMap.get("city"));
        base.put("cityCode", inputMap.get("district"));
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", new OpenApply4GProcessor().decodeCheckType4G(custInfo.get("checkType")));
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", custInfo.get("contactPerson"));
        base.put("contactPhone", custInfo.get("contactPhone"));
        base.put("contactAddress", custInfo.get("contactAddress"));
        base.put("remark", inputMap.get("remark"));
        base.put("tradeTypeCode", "0031");
        base.put("userId", inputMap.get("userId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("userDiffCode", inputMap.get("addType"));// TODO：待确认
        return base;
    }

    /**
     * 该方法仅用于处理融合产品(老库) by wangmc 20170324 FIXME
     * @param productId
     * @param productMode
     * @param provinceCode
     * @param firstMonBillMode
     * @param msg msg中需要包含userId,serialNumber
     */
    public static Map preProductInfo4Mix(List<Map> productInfo, Object provinceCode, Map msg) {

        LanOpenApp4GDao dao = new LanOpenApp4GDao();

        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getDate();
        Map ext = new HashMap();
        String methodCode = msg.get("methodCode") + "";
        Map activityTimeMap = new HashMap();
        String isFinalCode = "";
        // 处理活动
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (null != activityInfo && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
                String actPlanId = String.valueOf(activityInfo.get(i).get("actPlanId"));
                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("COMMODITY_ID", actPlanId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> productInfoResult = dao.queryActivityProductInfo(proparam);
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到活动ID【" + actPlanId + "】的信息");
                }
                String newActPlanId = productInfoResult.get(0).get("PRODUCT_ID") + "";
                activityTimeMap = TradeManagerUtils.getEffectTime(newActPlanId, monthFirstDay, monthLasttDay);
                String actMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                String actMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                msg.put("resActivityper", activityTimeMap.get("resActivityper"));
                // 附加包处理
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("COMMODITY_ID", actPlanId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                // 处理活动的生效失效时间

                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", packageMap.get("ELEMENT_ID"));
                                    dis.put("activityStarTime", actMonthFirstDay);
                                    dis.put("activityTime", actMonthLasttDay);
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("activityStarTime", actMonthFirstDay);
                                    svc.put("activityTime", actMonthLasttDay);
                                    svcList.add(svc);
                                }
                            }
                        }
                    }
                }

                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = actPlanId;
                String addProductId = "";
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map addproparam = new HashMap();
                addproparam.put("PROVINCE_CODE", provinceCode);
                addproparam.put("COMMODITY_ID", commodityId);
                // 原始表查询活动用 productid
                addproparam.put("PRODUCT_ID", commodityId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", null);

                List<Map> addproductInfoResult = dao.queryAddProductInfo(addproparam);
                // 用于主副卡处理元素编码是A的问题
                msg.put("activityproductInfoResult", addproductInfoResult);
                if (null != addproductInfoResult && addproductInfoResult.size() > 0) {
                    for (int j = 0; j < addproductInfoResult.size(); j++) {

                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            dis.put("activityStarTime", actMonthFirstDay);
                            dis.put("activityTime", actMonthLasttDay);
                            discntList.add(dis);
                        }
                        if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            svc.put("activityStarTime", actMonthFirstDay);
                            svc.put("activityTime", actMonthLasttDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            // 暂不处理活动下的sp;
                        }

                    }
                }
                Map actProParam = new HashMap();
                actProParam.put("PRODUCT_ID", newActPlanId);
                List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
                if (actProductInfo == null || actProductInfo.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + newActPlanId + "】的产品信息");
                }
                strProductMode = String.valueOf(actProductInfo.get(0).get("PRODUCT_MODE"));
                strBrandCode = (String) actProductInfo.get(0).get("BRAND_CODE");
                strProductTypeCode = (String) actProductInfo.get(0).get("PRODUCT_TYPE_CODE");

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", newActPlanId);
                productTpye.put("productTypeCode", strProductTypeCode);
                // 算出活动的开始结束时间，预提交下发
                productTpye.put("activityStarTime", actMonthFirstDay);
                productTpye.put("activityTime", actMonthLasttDay);

                product.put("brandCode", strBrandCode);
                product.put("productId", newActPlanId);
                product.put("productMode", strProductMode);
                // 算出活动的开始结束时间，预提交下发
                product.put("activityStarTime", actMonthFirstDay);
                product.put("activityTime", actMonthLasttDay);

                productTypeList.add(productTpye);
                productList.add(product);

            }
        }

        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            String productMode = String.valueOf(productInfo.get(i).get("productMode"));
            String firstMonBillMode = String.valueOf(productInfo.get(i).get("firstMonBillMode"));
            String productId = String.valueOf(productInfo.get(i).get("productId"));
            String isIpOrInterTv = "";
            if (null != productInfo.get(i).get("isIpOrInterTv")) {
                isIpOrInterTv = (String) productInfo.get(i).get("isIpOrInterTv");
            }
            if ("0".equals(productMode)) {
                System.out.println("===========主产品产品处理");
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = productId;
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("COMMODITY_ID", commodityId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> productInfoResult = dao.queryProductInfo(proparam);
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                }
                // 处理速率,虚拟成员没有速率选择,使用默认的 by wangmc 20181012
                productInfoResult = TradeManagerUtils.chooseSpeed(productInfoResult, "");
                productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                if (productId.equals("-1")) {
                    productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
                }
                Map itparam = new HashMap();
                itparam.put("PROVINCE_CODE", provinceCode);
                itparam.put("PID", productId);
                itparam.put("COMMODITY_ID", commodityId);
                itparam.put("PTYPE", "U");
                List<Map> productItemInfoResult = dao.queryProductItemInfo(itparam);
                if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                }

                // 需要按偏移处理时间的资费 by wangmc 20180903
                NewProductUtils utils = new NewProductUtils();
                List<String> specialDiscnt = new DealNewCbssProduct().querySpealDealDiscnt(MapUtils.asMap(
                        "SELECT_FLAG", "SPACIL_DISCNT_MIXOPEN_MIX%"));
                for (int j = 0; j < productInfoResult.size(); j++) {

                    if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                        dis.put("productMode", productInfoResult.get(j).get("PRODUCT_MODE"));
                        // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20180903
                        if (utils.dealMainProDiscntDate(dis, specialDiscnt, productId, "N6")) {
                            continue;
                        }
                        discntList.add(dis);
                    }
                    if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map svc = new HashMap();
                        svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                        svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                        svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));

                        svcList.add(svc);
                    }
                    if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        String spId = "-1";
                        String partyId = "-1";
                        String spProductId = "-1";
                        Map spItemParam = new HashMap();
                        spItemParam.put("PTYPE", "X");
                        spItemParam.put("COMMODITY_ID", commodityId);
                        spItemParam.put("PROVINCE_CODE", provinceCode);
                        spItemParam.put("PID", productInfoResult.get(j).get("ELEMENT_ID"));
                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                    + productInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                        }

                        for (int l = 0; l < spItemInfoResult.size(); l++) {
                            Map spItemInfo = spItemInfoResult.get(l);
                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                spId = (String) (spItemInfo.get("ATTR_VALUE"));
                            }
                            if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                            }
                            if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                            }
                        }

                        Map sp = new HashMap();
                        sp.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                        sp.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                        sp.put("partyId", partyId);
                        sp.put("spId", spId);
                        sp.put("spProductId", spProductId);
                        sp.put("spServiceId", productInfoResult.get(j).get("ELEMENT_ID"));
                        spList.add(sp);
                    }

                }

                strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
                strBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", productItemInfoResult);
                strProductTypeCode = TradeManagerUtils.getValueFromItem("PRODUCT_TYPE_CODE", productItemInfoResult);

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", productId);
                productTpye.put("productTypeCode", strProductTypeCode);
                // 用于trade_user下面添加产品类型
                msg.put("mainProductTypeCode", strProductTypeCode);
                product.put("brandCode", strBrandCode);
                product.put("productId", productId);
                product.put("productMode", strProductMode);

                productTypeList.add(productTpye);
                productList.add(product);
                ext.put("brandCode", strBrandCode);
                ext.put("productTypeCode", strProductTypeCode);
                ext.put("mproductId", productId);
                // 附加包
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("COMMODITY_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                        System.out.println("走进来了么？？？？？？？" + packageElementInfo);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20180903
                                    if (utils.dealMainProDiscntDate(dis, specialDiscnt, productId, "N6")) {
                                        continue;
                                    }
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("COMMODITY_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                    }
                                    Map sp = new HashMap();
                                    sp.put("productId", packageMap.get("PRODUCT_ID"));
                                    sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                    spList.add(sp);
                                }
                            }
                        }
                    }
                }
            }
            if ("1".equals(productMode)) {
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = productId;
                String addProductId = "";
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";
                String addProMonthFirstDay = "";
                String addProMonthLasttDay = "";
                Map addproparam = new HashMap();
                addproparam.put("PROVINCE_CODE", provinceCode);
                addproparam.put("COMMODITY_ID", commodityId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> addproductInfoResult = new ArrayList<Map>();
                if (!StringUtils.isEmpty(isIpOrInterTv)) {
                    addproductInfoResult = dao.queryIptvOrIntertvProductInfo(addproparam);
                }
                else {
                    addproductInfoResult = dao.queryAddProductInfo(addproparam);
                }
                if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode)) {
                    addproductInfoResult = TradeManagerUtils.chooseSpeed(addproductInfoResult,
                            msg.get("phoneSpeedLevel") + "");
                }
                addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                isFinalCode = TradeManagerUtils.getEndDate(addProductId);
                System.out.println("开始==============" + monthFirstDay + monthLasttDay);

                System.out.println("=============isFinalCode" + isFinalCode);
                if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                    Map productDate = TradeManagerUtils.getEffectTime(addProductId, monthFirstDay, monthLasttDay);
                    addProMonthFirstDay = (String) productDate.get("monthFirstDay");
                    addProMonthLasttDay = (String) productDate.get("monthLasttDay");
                }
                else {
                    String activityTime = (String) activityTimeMap.get("monthLasttDay");
                    if (StringUtils.isEmpty(activityTime)) {
                        throw new EcAopServerBizException("9999", "所选附加产品" + commodityId + "生失效时间需要和合约保持一致，"
                                + "请检查合约信息是否已传或更换附加产品信息");
                    }
                    addProMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                    addProMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                }
                if (!"1".equals(isIpOrInterTv))// isIpOrInterTv为1的时候表示是互联网电视产品或iptv
                {
                    for (int j = 0; j < addproductInfoResult.size(); j++) {
                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String elementId = String.valueOf(addproductInfoResult.get(j).get("ELEMENT_ID"));
                            Map dis = new HashMap();
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            dis.put("activityTime", addProMonthLasttDay);
                            dis.put("activityStarTime", addProMonthFirstDay);
                            if (!"Y".equals(isFinalCode)) {
                                Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId,
                                        addProMonthFirstDay, addProMonthLasttDay);
                                dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                            }
                            else {
                                dis.put("activityStarTime", addProMonthFirstDay);
                                dis.put("activityTime", addProMonthLasttDay);
                            }
                            discntList.add(dis);
                        }
                        if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            svc.put("activityTime", addProMonthLasttDay);
                            svc.put("activityStarTime", addProMonthFirstDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", commodityId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + addproductInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                            }
                            Map sp = new HashMap();
                            sp.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            sp.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            sp.put("activityTime", addProMonthLasttDay);
                            sp.put("activityStarTime", addProMonthFirstDay);
                            spList.add(sp);
                        }

                    }
                }
                Map additparam = new HashMap();
                additparam.put("PROVINCE_CODE", provinceCode);
                additparam.put("PID", addProductId);
                additparam.put("COMMODITY_ID", commodityId);
                additparam.put("PTYPE", "U");
                List<Map> addProductItemInfoResult = dao.queryProductItemInfo(additparam);
                if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                }

                strProductMode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_MODE"));
                strBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", addProductItemInfoResult);
                strProductTypeCode = TradeManagerUtils.getValueFromItem("PRODUCT_TYPE_CODE", addProductItemInfoResult);

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", addProductId);
                productTpye.put("productTypeCode", strProductTypeCode);
                productTpye.put("activityTime", addProMonthLasttDay);
                productTpye.put("activityStarTime", addProMonthFirstDay);
                product.put("activityTime", addProMonthLasttDay);
                product.put("activityStarTime", addProMonthFirstDay);
                product.put("brandCode", strBrandCode);
                product.put("productId", addProductId);
                product.put("productMode", strProductMode);

                productTypeList.add(productTpye);
                productList.add(product);
                // 附加包
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("COMMODITY_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    dis.put("activityStarTime", addProMonthFirstDay);
                                    dis.put("activityTime", addProMonthLasttDay);
                                    if (!"Y".equals(isFinalCode)) {
                                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId,
                                                addProMonthFirstDay, addProMonthLasttDay);
                                        dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                        dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                    }
                                    else {
                                        dis.put("activityStarTime", addProMonthFirstDay);
                                        dis.put("activityTime", addProMonthLasttDay);
                                    }
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("activityStarTime", addProMonthFirstDay);
                                    svc.put("activityTime", addProMonthLasttDay);
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("COMMODITY_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                    }
                                    Map sp = new HashMap();
                                    sp.put("productId", packageMap.get("PRODUCT_ID"));
                                    sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                    sp.put("activityStarTime", addProMonthFirstDay);
                                    sp.put("activityTime", addProMonthLasttDay);
                                    spList.add(sp);
                                }
                            }
                        }
                    }
                }
            }
        }
        // dealRepeat(discntList);
        // dealRepeat(svcList);
        // dealRepeat(spList);
        // dealRepeat(productTypeList);
        // dealRepeat(productList);

        discntList = NewProductUtils.newDealRepeat(discntList, "discntList");
        svcList = NewProductUtils.newDealRepeat(svcList, "svcList");
        spList = NewProductUtils.newDealRepeat(spList, "spList");
        productTypeList = NewProductUtils.newDealRepeat(productTypeList, "productTypeList");
        productList = NewProductUtils.newDealRepeat(productList, "productList");

        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);
        // 增加活动结束时间
        // msg.put("activityTime", monthLasttDay);

        OpenApply4GProcessor openApplyPro = new OpenApply4GProcessor();
        ext.put("tradeProductType", openApplyPro.preProductTpyeListData(msg));
        ext.put("tradeProduct", openApplyPro.preProductData(msg));
        ext.put("tradeDiscnt", openApplyPro.preDiscntData(msg));
        ext.put("tradeSvc", openApplyPro.preTradeSvcData(msg));
        ext.put("tradeSp", openApplyPro.preTradeSpData(msg));
        return ext;
    }
}
