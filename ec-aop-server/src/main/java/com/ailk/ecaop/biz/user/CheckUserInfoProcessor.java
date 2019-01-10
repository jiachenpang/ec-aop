package com.ailk.ecaop.biz.user;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("checkUserInfo")
@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class CheckUserInfoProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbssNumberRoute.checkUserParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trade.cbss.qryBlackParametersMapping", "ecaop.trade.n6NumberRoute.checkUserParametersMapping",
            "ecaop.trade.n6.checkUserParametersMapping.91", "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.masb.chku.checkUserParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[7];

    @Override
    public void process(Exchange exchange) throws Exception {
        String codeForCB = "";// 集团证件类型时返回2001（只针对CB修改）start1
        // Call the three information query interface.
        String methodCode = exchange.getMethodCode();
        String sysType = MagicNumber.SYS_TYPE_ESS;
        String essTag = MagicNumber.N25ESS;
        Boolean isFromN6 = false;
        LanUtils lan = new LanUtils();
        //CB预受理订单查询
        boolean isCbss = "cbsb".equals(exchange.getAppCode());
        Map checkUserInfo = exchange.getIn().getBody(Map.class);
        Map message = (Map) checkUserInfo.get("msg");
        String tradeTypeCode = (String) message.get("tradeTypeCode");
        if ("qctc".equals(methodCode) || "qcsp".equals(methodCode) || "sccc".equals(methodCode)
                || "bsoa".equals(methodCode) || "psoa".equals(methodCode) || "baoa".equals(methodCode)
                || "faoa".equals(methodCode) || "scccs".equals(methodCode)) {
            try {
                preParam4ThreePart(exchange);
                if (isCbss) {
                    lan.preData(pmp[0], exchange);
                }
                else {
                    lan.preData(pmp[1], exchange);
                }
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
                if ("qcsp".equals(methodCode)) {
                    lan.xml2JsonNoError("ecaop.trades.cbss.threePart.template", exchange);
                }
                else {
                    lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
                }
                String detail = (String) exchange.getOut().getBody(Map.class).get("detail");
                // 集团证件类型时返回2001（只针对CB修改）start2
                String certTypeCode = null;
                try {
                    certTypeCode = (((List<Map>) exchange.getOut().getBody(Map.class).get("custInfo")).get(0)
                            .get("certTypeCode")) + "";
                    if ("4|S|R|N|L|U|M|T|D".contains(certTypeCode)) {// CB的集团证件类型
                        codeForCB = "2001";
                    }
                }
                catch (Exception e) {
                    // 手动try不抛错，防止获取certTypeCode时空指针
                }
                // 集团证件类型时返回2001（只针对CB修改）end2
                if (null != detail && detail.contains("根据用户号码获取用户资料无数据")) {
                    throw new EcAopServerBizException("1204", detail);
                }
                sysType = MagicNumber.SYS_TYPE_CBSS;
                // cb黑名单校验
                Map blackHisMap = new HashMap();
                boolean isString = checkUserInfo.get("msg") instanceof String;
                Map msg = isString ? JSON.parseObject((String) checkUserInfo.get("msg")) : (Map) checkUserInfo
                        .get("msg");
                msg.put("areaCode", ChangeCodeUtils.changeEparchy(msg));
                String[] copyArray = { "operatorId", "province", "channelId", "city", "areaCode", "district",
                        "channelType" };
                MapUtils.arrayPut(blackHisMap, msg, copyArray);
                preDataMap(blackHisMap, exchange);
                Exchange qryBlackExchange = ExchangeUtils.ofCopy(exchange, blackHisMap);
                try {
                    lan.preData(pmp[2], qryBlackExchange);
                    CallEngine.wsCall(qryBlackExchange, "ecaop.comm.conf.url.cbss.services.checkBlackCust");
                    lan.xml2JsonNoError("ecaop.trades.cbss.blackHis.template", qryBlackExchange);
                }
                catch (EcAopServerBizException e) {
                    throw new EcAopServerBizException("9999", e.getMessage());
                }
                Map blackRetMap = qryBlackExchange.getOut().getBody(Map.class);
                if ("0000".equals(blackRetMap.get("code")) && "1".equals(blackRetMap.get("actTag"))) {
                    throw new EcAopServerBizException("9999", "此客户信息为黑名单用户！");
                }
                if ("8888".equals(blackRetMap.get("code"))) {
                    throw new EcAopServerBizException("9999", "其他错误");
                }

            }
            catch (EcAopServerBizException e) {
                e.printStackTrace();
                if (!e.getMessage().contains("根据用户号码获取用户资料无数据")) {
                    throw new EcAopServerBizException(dealRspCode4Bank(e.getCode()), e.getDetail());
                }
                exchange.getIn().setBody(checkUserInfo);
                preParam4ThreePartProvince(exchange.getIn().getBody(Map.class));
                boolean isString = exchange.getIn().getBody(Map.class).get("msg") instanceof String;
                Map msg = new HashMap();
                if (isString) {
                    msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
                }
                else {
                    msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
                }
                String provinceCode = (String) msg.get("province");
                // 2016-03-02 添加转码操作
                try {
                    if (isCbss) {
                        provinceCode = new UserCardCheckProcessor().getNumberRealProvince(exchange,
                                msg).get("province").toString();
                    }
                    if ("17|18|11|76|91|97".contains(provinceCode)) {
                        String[] copyArray = { "operatorId", "province", "channelId", "city", "district",
                                "channelType", "serviceClassCode", "serialNumber" };
                        // 为辽宁单独拉分支，支持返回acctInfo。2017-1-16 by cuij
                        Map threePartMap = new HashMap();
                        // 增加山东返回acctInfo 7/19 create zhaok
                        // 单独给沃小号系统增加三户返回节点分支
                        if ("wxsb".equals(exchange.getAppCode())) {
                            threePartMap = MapUtils.asMap("getMode", "1111111100000051", "tradeTypeCode",
                                    msg.get("tradeTypeCode") + "");
                        }
                        else if ("91|17".contains(provinceCode)) {
                            threePartMap = MapUtils.asMap("getMode", "1111111100000001", "tradeTypeCode",
                                    msg.get("tradeTypeCode") + "");
                        }
                        else {
                            threePartMap = MapUtils.asMap("getMode", "1111111100000001", "tradeTypeCode",
                                    msg.get("tradeTypeCode") + "");
                        }
                        MapUtils.arrayPut(threePartMap, msg, copyArray);
                        // 不下发0000的网别给省份 lixl 2016-07-07
                        if ("0000".equals(threePartMap.get("serviceClassCode"))) {
                            threePartMap.remove("serviceClassCode");
                        }

                        Exchange threeExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
                        if (isCbss) {
                            lan.preData(pmp[3], threeExchange);
                        }
                        else {
                            // 辽宁省份三户请求单独模板，带上参数serviceClassCode
                            if ("91".equals(provinceCode)) {
                                lan.preData(pmp[4], threeExchange);
                            }
                            else {
                                lan.preData(pmp[5], threeExchange);
                            }
                        }
                        CallEngine.wsCall(threeExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + provinceCode);
                        exchange.getOut().setBody(threeExchange.getOut().getBody());
                        if (MagicNumber.THREEPART_NO_ERROR_BIZKEY.equals(exchange.getBizkey())) {
                            lan.xml2JsonNoError("ecaop.trades.cbss.threePart.template", exchange);
                        }
                        else {
                            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
                        }
                        essTag = MagicNumber.N6ESS;
                        sysType = MagicNumber.SYS_TYPE_CBSS;
                        isFromN6 = true;
                    }
                    else {
                        lan.preData(pmp[6], exchange);
                        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.usrser");
                        if (MagicNumber.THREEPART_NO_ERROR_BIZKEY.equals(exchange.getBizkey())) {
                            lan.xml2JsonNoError("ecaop.masb.chku.checkUserTemplate", exchange);
                        }
                        else {
                            lan.xml2Json("ecaop.masb.chku.checkUserTemplate", exchange);
                        }
                        sysType = MagicNumber.SYS_TYPE_ESS;
                    }
                }
                catch (EcAopServerBizException e1) {
                    throw new EcAopServerBizException(dealRspCode4Bank(e1.getCode()), e1.getDetail());
                }
            }
            catch (Exception e) {
			e.printStackTrace();
                throw new EcAopServerBizException("9999", e.getMessage());
            }
        }
        else {
            exchange.setMethodCode("bsop");//宽带单装预提交
            lan.preData(pmp[6], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.usrser");
            lan.xml2Json("ecaop.masb.chku.checkUserTemplate", exchange);
        }
        exchange.setMethodCode(methodCode);
        String getMode = obtainGetMode(checkUserInfo);

        // Input the customer and account's information to exchange.
        Map retMap = dealReturn(getMode, exchange, sysType, essTag);
        retMap.put("simCard", dealSimCard(exchange, sysType).get("simCard"));
        String rspCode = (String) retMap.get("code");// 获取返回编码
        // 北六省份按照cb返回进行处理
        if (isFromN6) {
            sysType = "1";
            Object code = retMap.get("code");
            if (null != code) {
                retMap.put("code", dealRspCode4Bank((String) code));
            }
        }
        else {
            Object code = retMap.get("code");
            if (null != code) {
                retMap.put("code", dealRspCode4Bank((String) code));
            }
        }
        // 集团证件类型时返回2001（只针对CB修改）start3
        if ("2001".equals(codeForCB)) {
            retMap.put("code", codeForCB);
            retMap.put("detail", "该用户为集团客户!");
        }
        // 集团证件类型时返回2001（只针对CB修改）end3
        retMap.put("sysType", sysType);
        
        if (MagicNumber.SYS_TYPE_ESS.equals(sysType)) {// RHQ2018030100003-沃小号产品BSS实现--04_当tradeTypeCode为9999时新增返回编码1642、1643、1644
            if (null != rspCode) {
                retMap.put("code", dealRspCodeForEss(rspCode, tradeTypeCode, exchange.getAppCode()));
            }
        }

      //cb分支增加团购标志字段
        Map tempBody = exchange.getOut().getBody(Map.class);
        List<Map<String,String>> para = (List) tempBody.get("para");
        if(MagicNumber.SYS_TYPE_CBSS.equals(sysType)&&!IsEmptyUtils.isEmpty(para)){
        	for (Map<String, String> paraInfoMap : para) {
        		if(paraInfoMap.containsValue("WHETHER_GROUP_USER")){
        			if(StringUtils.isEmpty(paraInfoMap.get("paraValue"))){
        				throw new EcAopServerBizException("9999", "cb返回团购信息为空");
        			}else if("0".equals(paraInfoMap.get("paraValue"))){
        				retMap.put("grpUserFlag", "0");
        			}else if("1".equals(paraInfoMap.get("paraValue"))){
        				retMap.put("grpUserFlag", "1");
        			}
        			
        		}
			}
        }
        retMap.put("para", para);
        exchange.setProperty(Exchange.HTTP_STATUSCODE, 200);
        exchange.getOut().setBody(retMap);
    }

    private void preDataMap(Map blackHisMap, Exchange exchange) {
        Map retMap = new HashMap();
        Object body = exchange.getOut().getBody();
        if (body instanceof String) {
            retMap = JSON.parseObject((String) body);
        }
        else {
            retMap = exchange.getOut().getBody(Map.class);
        }
        List<Map> custInfoList = (List<Map>) retMap.get("custInfo");
        if (null == custInfoList || custInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "核心系统未返回客户信息");
        }
        Map custInfoMap = custInfoList.get(0);
        blackHisMap.put("psptTypeCode", custInfoMap.get("certTypeCode"));
        blackHisMap.put("psptId", custInfoMap.get("certCode"));
     
    }

    // 增加UISM卡号
    private Map dealSimCard(Exchange exchange, String sysType) {
        Map tempMap = new HashMap();
        Map userInfo = new HashMap();
        Object body = exchange.getOut().getBody();
        String bizkey = exchange.getBizkey();
        if (body instanceof String) {
            tempMap = JSON.parseObject((String) body);
        }
        else {
            tempMap = exchange.getOut().getBody(Map.class);
        }
        Map user = getThreePartInfo(tempMap, "userInfo", sysType, bizkey);
        if (null == user || user.isEmpty()) {
            return userInfo;
        }
        List<Map> resInfo = (List<Map>) user.get("resInfo");
        if (null != resInfo && (!resInfo.isEmpty())) {
            for (Map temp : resInfo) {
                if ("1".equals(temp.get("resTypeCode"))) {
                    userInfo.put("simCard", temp.get("resCode"));
                }
            }
        }
        else {
            if (null != user.get("sinCardNo")) {
                userInfo.put("simCard", user.get("sinCardNo"));
            }
        }
        return userInfo;
    }

    /**
     * Processing returns information.
     * 
     * @param getMode
     * @param exchange
     * @return
     */
    private Map dealReturn(String getMode, Exchange exchange, String sysType, String essTag) {
        String methodCode = exchange.getMethodCode();
        Map retMap = new HashMap();
        if (!"600".equals(exchange.getProperty(Exchange.HTTP_STATUSCODE).toString())) {
            if ("bsop".equals(methodCode) || "bson".equals(methodCode)) {
                retMap = dealReturn4Instser(getMode, exchange);
            }
            else {
                retMap = dealReturn4ThreePart(getMode, exchange, sysType, essTag);
            }
        }
        return retMap;
    }

    /**
     * Processing returns information for sole business account.
     * 
     * @param checkUserInfo
     * @param exchange
     * @return
     */
    private Map dealReturn4Instser(String getModeString, Exchange exchange) {
        Map retMap = new HashMap();
        retMap = exchange.getOut().getBody(Map.class);
        String[] getMode = getModeString.split("");

        // First signed as customer information.
        if ("1".equals(getMode[1])) {
            retMap.put("custInfo", dealCustInfo4Instser(retMap));
        }
        // Ninth signed as customer information.
        if ("1".equals(getMode[9])) {
            retMap.put("acctInfo", dealAcctInfo4Instser(retMap));
        }
        return retMap;
    }

    private Map dealReturn4ThreePart(String getModeString, Exchange exchange, String sysType, String essTag) {
        Map threePart = new HashMap();
        Map retMap = new HashMap();
        Object body = exchange.getOut().getBody();
        String bizkey = exchange.getBizkey();
        if (body instanceof String) {
            retMap = JSON.parseObject((String) body);
        }
        else {
            retMap = exchange.getOut().getBody(Map.class);
        }

        if ("560".equals(exchange.getProperty(Exchange.HTTP_STATUSCODE).toString())
                && MagicNumber.THREEPART_NO_ERROR_BIZKEY.equals(bizkey)) {
            return retMap;
        }

        String[] getMode = getModeString.split("");
        // First signed as customer information.
        if ("1".equals(getMode[1])) {
            threePart.put("custInfo", dealCustInfo4ThreePart(retMap, sysType, bizkey, essTag));
        }

        if ("1".equals(getMode[3])) {
            String appcode=exchange.getAppCode();
            threePart.put("userInfo", dealUserInfo4ThreePart(retMap, sysType, essTag, bizkey,appcode));
        }

        // Ninth signed as customer information.
        if ("1".equals(getMode[9])) {
            threePart.put("acctInfo", dealAcctInfo4ThreePart(retMap, sysType, bizkey));
        }
        if (MagicNumber.THREEPART_NO_ERROR_BIZKEY.equals(bizkey)) {
            MapUtils.arrayPut(threePart, retMap, new String[] { "code", "detail" });
            if (null == threePart.get("code")) {
                threePart.put("code", "0000");
                threePart.put("detail", "success");
            }
            else {
                threePart.put("code", retMap.get("code"));
                threePart.put("detail", retMap.get("detail"));
            }
        }
        return threePart;
    }

    /**
     * Handling customer information for sole business account.
     * 
     * @param retMap
     * @return
     */
    private Map dealCustInfo4Instser(Map retMap) {
        Map custInfo = (Map) retMap.get("custInfo");
        if (null == custInfo || custInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:省分未返回客户信息");
        }
        custInfo.put("isNew", "1");
        custInfo.put("custId", custInfo.get("custId"));
        return custInfo;
    }

    private Map getThreePartInfo(Map inMap, String getInfo, String sysType, String bizkey) {
        if ("1".equals(sysType)) {
            Map retMap = (Map) inMap.get(getInfo);
            if (MagicNumber.THREEPART_NO_ERROR_BIZKEY.equals(bizkey)) {
                return retMap;
            }
            if (null == retMap || retMap.isEmpty()) {
                return new HashMap();
            }
            return retMap;
        }
        List<Map> retList = (ArrayList<Map>) inMap.get(getInfo);
        // if (MagicNumber.THREEPART_NO_ERROR_BIZKEY.equals(bizkey)) {
        // return retList.get(0);
        // }
        if (null == retList || 0 == retList.size()) {
            return new HashMap();
        }
        return retList.get(0);
    }

    private Map dealCustInfo4ThreePart(Map retMap, String sysType, String bizkey, String essTag) {
        Map custInfo = new HashMap();
        Map cust = getThreePartInfo(retMap, "custInfo", sysType, bizkey);
        if (null == cust || cust.isEmpty()) {
            return custInfo;
        }
        String[] custArray = { "custId", "custName", "custType", "certEndDate", "certAddr", "certTypeCode", "certType",
                "certCode" };
        MapUtils.arrayPut(custInfo, cust, custArray);
        if (MagicNumber.SYS_TYPE_CBSS.equals(sysType)) {
            custInfo.put("certTypeCode", CertTypeChangeUtils.certTypeCbss2Mall((String) custInfo.get("certTypeCode")));
        }
        else if (MagicNumber.N25ESS.equals(essTag)) {
            custInfo.put("certTypeCode", CertTypeChangeUtils.certTypeFbs2Mall((String) custInfo.get("certTypeCode")));
        }
        MapUtils.emptyPut(custInfo, (Map) cust.get("vipCustInfo"), "vipClassId");
        MapUtils.emptyPut(custInfo, (Map) cust.get("vipCustInfo"), "vipTypeCode");
        return custInfo;
    }

    private Map dealUserInfo4ThreePart(Map retMap, String sysType, String essTag, String bizkey,String appcode) {
        Map userInfo = new HashMap();
        Map user = getThreePartInfo(retMap, "userInfo", sysType, bizkey);
        if (null == user || user.isEmpty()) {
            return userInfo;
        }
        List<Map> paraList = (List<Map>) retMap.get("para");
        if (null != paraList && (!paraList.isEmpty())) {
            for (Map paraMap : paraList) {
                if ("CERT_TAG".equals(paraMap.get("paraId"))) {
                    userInfo.put("checkType", paraMap.get("paraValue"));
                }
            }
        }
        List<Map> discntInfoList = (List<Map>) user.get("discntInfo");
        if (null != discntInfoList && (!discntInfoList.isEmpty())) {
            List<Map> attrInfoList = (List<Map>) discntInfoList.get(0).get("attrInfo");
            for (Map attrInfoMap : attrInfoList) {
                if ("lowCost".equals(String.valueOf(attrInfoMap.get("attrCode")))) {
                    userInfo.put("minConsume", attrInfoMap.get("attrValue"));
                }
                else if ("88888888|88888881".contains(String.valueOf(attrInfoMap.get("attrCode")))
                        && Long.valueOf(dealDate4AddMonth(1, 1)) < Long.valueOf((String) (attrInfoMap.get("endDate")))) {
                    userInfo.put("isNiceNumber", "1");
                }
                else {
                    userInfo.put("isNiceNumber", "2");
                }
            }
        }
        List<Map> attrInfoList = (List<Map>) user.get("attrInfo");
        if (null != attrInfoList && 0 != attrInfoList.size()) {
            List<Map> resourceInfo = new ArrayList<Map>();
            Map INSTALL_ADDRESS = new HashMap();
            for (Map attr : attrInfoList) {
                if ("ITM_PRD_RESPONSIBLE".equals(attr.get("attrCode"))) {
                    userInfo.put("itmPrdRespobsible", attr.get("attrValue"));
                }
                else if ("ITM_PRD_GROUP_TYPE".equals(attr.get("attrCode"))) {
                    userInfo.put("itmPrdGroupType", attr.get("attrValue"));
                }
                // 新增实名标示
                if ("REAL_CUST_NM".equals(attr.get("attrCode"))) {
                    userInfo.put("realNameTag", attr.get("attrValue"));
                }
                // 装机地址
                if ("INSTALL_ADDRESS".equals(attr.get("attrCode"))) {
                    INSTALL_ADDRESS.put("installAddr", attr.get("attrValue"));

                }
                if ("DETAIL_INSTALL_ADDRESS".equals(attr.get("attrCode"))) {
                    INSTALL_ADDRESS.put("addrName", attr.get("attrValue"));

                }
                if ("ADDRESS_ID".equals(attr.get("attrCode"))) {
                    INSTALL_ADDRESS.put("addrCode", attr.get("attrValue"));

                }
                if ("MOFFICE_ID".equals(attr.get("attrCode"))) {
                    INSTALL_ADDRESS.put("exchCode", attr.get("attrValue"));
                    List<Map> User = (List<Map>) retMap.get("userInfo");
                    String city = (String) User.get(0).get("cityCode");
                    INSTALL_ADDRESS.put("cityCode", city);

                }

            }
            resourceInfo.add(INSTALL_ADDRESS);
            userInfo.put("resourceInfo", resourceInfo);
        }
        // 增加三个三户返回 字段 provinceCode eparchyCode cityCode create by zhaok Date 7/18
        //从accInfo下面获取的prepayTag给去掉，改成从userInfo下面获取"prepayTag",by zhaok date 12/21
        String[] userArray = {"userId", "userState", "brandCode", "brand", "openDate", "landLevle", "roamStat",
                "productId", "productName", "nextProductId", "nextProductName", "subscType", "flag3G",
                "serviceClassCode", "checkType", "sinCardNo", "provinceCode", "eparchyCode", "cityCode", "prepayTag",
                "developStaffId","developDate","developCityCode","developDepartId"};// RGX2018071200047-关于在AOP接口增加查询返回发展渠道信息的需求
        MapUtils.arrayPut(userInfo, user, userArray);
        if (IsEmptyUtils.isEmpty(userInfo.get("subscType"))) {
            userInfo.put("subscType", user.get("subscrbType"));
        }
        // 返回新增资费信息elementInfo
        List<Map> discntInfo = (List<Map>) user.get("discntInfo");
        List<Map> svcInfo = (List<Map>) user.get("svcInfo");
        List<Map> resourceInfo = (List<Map>) user.get("resourceInfo");
        List<Map> spInfo = (List<Map>) user.get("spInfo");
        List<Map> elementInfoList = new ArrayList<Map>();
        List<Map> disinfos = dealElementList(discntInfo, "1");
        List<Map> svcinfos = dealElementList(svcInfo, "0");
        List<Map> productList = new ArrayList<Map>();
        elementInfoList.addAll(disinfos);
        elementInfoList.addAll(svcinfos);
        userInfo.put("elementInfo", elementInfoList);
        userInfo.put("svcInfo", svcInfo);
        if ((bizkey + "").equals(MagicNumber.THREEPART_NO_ERROR_BIZKEY))
        {
            //payServiceId转payUserId
            if (null != spInfo && spInfo.size() > 0)
            {
                for (Map sp : spInfo)
                {
                    if (null != sp.get("payServiceId"))
                    {
                        sp.put("payUserId", sp.get("payServiceId"));
                        sp.remove("payServiceId");
                    }
                }
            }
            userInfo.put("spInfo", spInfo);
        }
        if (null != resourceInfo && 0 != resourceInfo.size()) {
            userInfo.put("resourceInfo", changeResourceInfo(resourceInfo));
        }
        Map arrearageFeeInfo = (Map) user.get("arrearageFeeInfo");
        if (null != arrearageFeeInfo && !arrearageFeeInfo.isEmpty()) {
            if (arrearageFeeInfo.get("lastBalance").toString().startsWith("-")) {
                userInfo.put("arrearageFeeInfo", arrearageFeeInfo);
            }
        }
        String userState = userInfo.get("userState") + "";
        if (("1".equals(sysType) && MagicNumber.N25ESS.equals(essTag))
                || ("2".equals(sysType) && MagicNumber.N6ESS.equals(essTag))) {
            userInfo.put("userState", ChangeCodeUtils.changeUserState(userState));
        }
        if ("cpsb".equals(appcode)&&!IsEmptyUtils.isEmpty(user.get("userStateCodeset"))){
            userInfo.put("userState",user.get("userStateCodeset"));
        }
        Object activityInfo = null;
        Object zfInfo = null;
        Object uuInfoAdd = null;
        if (sysType.equals(MagicNumber.SYS_TYPE_CBSS)) {
            List<Map> productInfo = (ArrayList<Map>) user.get("productInfo");
            List<Map> activityList = new ArrayList<Map>();
            List<Map> uuInfoList = new ArrayList<Map>();
            if ("SS-CS-003".equals(bizkey)) {
                Map purchase = getThreePartInfo(retMap, "purchase", sysType, bizkey);// 获取cb的购买信息
                Map activityMap = new HashMap();
                activityMap.put("activityId", "");
                activityMap.put("activityName", "");
                activityMap.put("activityActiveTime", "");
                activityMap.put("activityInactiveTime", "");
                activityMap.put("imei", purchase.get("imei"));
                activityList.add(activityMap);
            }
            else {
                for (Map prod : productInfo) {
                    if (!"50".equals(prod.get("productMode"))) {
                        Map productMap = new HashMap();
                        productMap.put("subProductId", prod.get("productId"));
                        productMap.put("subProductName", prod.get("productName"));
                        productMap.put("subProductMode", prod.get("productMode"));
                        productMap.put("subProductActiveTime", prod.get("productActiveTime"));
                        productMap.put("subProductInactiveTime", prod.get("productInactiveTime"));
                        productList.add(productMap);
                    }
                    else {
                        Map activityMap = new HashMap();
                        activityMap.put("activityId", prod.get("productId"));
                        activityMap.put("activityName", prod.get("productName"));
                        activityMap.put("activityActiveTime", prod.get("productActiveTime"));
                        activityMap.put("activityInactiveTime", prod.get("productInactiveTime"));
                        activityList.add(activityMap);
                    }
                }
            }
            List<Map> uuInfo = (ArrayList<Map>) user.get("uuInfo");
            Map zCard = new HashMap();
            for (Map temp : uuInfo) {
                if ("ZF".equals(temp.get("relationTypeCode"))) {
                    zCard = MapUtils.asMap("endDate", temp.get("endDate"), "serialNumber", temp.get("serialNumberA"),
                            "codeTag", "1");
                    Map uuInfoMap = new HashMap();
                    uuInfoMap.put("endDate", temp.get("endDate"));
                    uuInfoMap.put("serialNumber", temp.get("serialNumberB"));
                    // 现在是23G主副卡返回的是反的， 返回规范是0： 副卡，1：主卡,给N6加分支 create by zhaok 
                    if (MagicNumber.N6ESS.equals(essTag)) {
                        uuInfoMap.put("codeTag", "0".equals(String.valueOf(temp.get("roleCodeB"))) ? 1 : 0);
                    }
                    else {
                        uuInfoMap.put("codeTag", temp.get("roleCodeB"));
                    }

                    uuInfoList.add(uuInfoMap);
                }
            }
            if (!zCard.isEmpty()) {
                uuInfoList.add(zCard);
            }
            List<Map> retList = new ArrayList<Map>();
            Map numMap = new HashMap();
            for (Map m : uuInfoList) {
                if (numMap.get(m.get("serialNumber")) == null) {
                    retList.add(m);
                    numMap.put(m.get("serialNumber"), m.get("serialNumber"));
                }
            }

            List<Map> uuInfoAddList = new ArrayList<Map>();
            for (Map temp : uuInfo) {
                Map uuInfoTempMap = new HashMap();
                String relationTypeCode = (String) temp.get("relationTypeCode");
                if (relationTypeCode.startsWith("89") || relationTypeCode.startsWith("88")) {
                    uuInfoTempMap.put("serialNumberA", temp.get("serialNumberA"));
                }
                uuInfoTempMap.put("relationTypeCode", relationTypeCode);
                uuInfoAddList.add(uuInfoTempMap);
            }
            activityInfo = 0 == activityList.size() ? null : activityList;
            zfInfo = 0 == retList.size() ? null : retList;
            uuInfoAdd = 0 == uuInfoAddList.size() ? null : uuInfoAddList;
        }
        else {
            activityInfo = user.get("activityInfo");
        }

        if (null != activityInfo || null != zfInfo || null != uuInfoAdd || productList.size() > 0) {
            userInfo.put("activityInfo", activityInfo);
            userInfo.put("zfInfo", zfInfo);
            userInfo.put("uuInfo", uuInfoAdd);
            userInfo.put("subProductInfo", productList);
        }
        return userInfo;
    }

    // 需幺转换映射关系
    private List<Map> changeResourceInfo(List<Map> resourceInfo) {
        if (null != resourceInfo && 0 != resourceInfo.size()) {
            for (Map element : resourceInfo) {
                // 根据规范增加installAddr 移除installAddress create by zhaok 6/15
                element.put("installAddr", element.get("installAddress"));
                element.put("addrCode", element.get("addressCode"));
                element.put("addrName", element.get("addressName"));
                element.remove("installAddress");
            }
        }
        return resourceInfo;
    }

    private List<Map> dealElementList(List<Map> elementInfo, String elementType) {
        List<Map> elementInfoList = new ArrayList<Map>();
        if (null != elementInfo && 0 != elementInfo.size()) {
            for (Map element : elementInfo) {
                Map ele = new HashMap();
                ele.put("productId", element.get("productId"));
                ele.put("packageId", element.get("packageId"));
                ele.put("idType", elementType);
                if ("1".equals(elementType)) {
                    ele.put("id", element.get("discntCode"));
                }
                else if ("0".equals(elementType)) {
                    ele.put("id", element.get("serviceId"));
                }
                ele.put("startDate", element.get("startDate"));
                ele.put("endDate", element.get("endDate"));
                elementInfoList.add(ele);
            }
        }
        return elementInfoList;
    }

    private Map dealAcctInfo4ThreePart(Map retMap, String sysType, String bizkey) {
        Map acctInfo = new HashMap();
        Map acct = getThreePartInfo(retMap, "acctInfo", sysType, bizkey);
        if (null == acct || acct.isEmpty()) {
            return acctInfo;
        }
        //从accInfo下面获取的prepayTag给去掉，改成从userInfo下面获取"prepayTag",by zhaok date 12/21
        String[] acctArray = {"acctId", "payName", "payModeCode", "accountCycle", "isGroupAcct",
                "payMode", "consolidatedAccounts"};
        MapUtils.arrayPut(acctInfo, acct, acctArray);
        Object consignObject = acct.get("consign");
        Map consign = new HashMap();
        if (null != consignObject) {
            boolean consignIsMap = consignObject instanceof Map;
            if (consignIsMap) {
                Map consignMap = (Map) consignObject;
                consignMap.remove("bankBusiKind");
                // 修改返回字段 create by zhaok
                consign.put("consign", consignMap);
                acctInfo.putAll(consign);
                // 修改返回字段 create by zhaok
                // acctInfo.putAll(consignMap);
            }
            else {
                ArrayList<Map> consignList = (ArrayList<Map>) consignObject;
                if (0 != consignList.size()) {
                    Map consignMap = consignList.get(0);
                    consignMap.remove("bankBusiKind");
                    // 修改返回字段 create by zhaok
                    consign.put("consign", consignMap);
                    acctInfo.putAll(consign);

                    // acctInfo.putAll(consignMap);
                }
            }

        }

        return acctInfo;
    }

    /**
     * Handling account information for sole business account.
     * 
     * @param retMap
     * @return
     */
    private Map dealAcctInfo4Instser(Map retMap) {
        Map acctInfo = new HashMap();
        Object acctObject = retMap.get("acctInfo");
        if (null == acctObject) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:省分未返回帐户信息");
        }
        Map acct = (Map) retMap.get("acctInfo");
        acctInfo.put("isNew", "1");
        acctInfo.put("acctId", acct.get("acctId"));
        return acctInfo;
    }

    /**
     * Obtain the string of named getMode
     * 
     * @param checkUserInfo
     * @return
     */
    private String obtainGetMode(Map checkUserInfo) {
        boolean isString = checkUserInfo.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) checkUserInfo.get("msg"));
        }
        else {
            msg = (Map) checkUserInfo.get("msg");
        }
        return msg.get("getMode").toString();
    }

    private String createGetMode(String infoList, String province) {
        infoList = infoList.replace("|", ",");
        String[] info = infoList.split(",");
        Map infoMap = new HashMap();
        for (int i = 0; i < info.length; i++) {
            infoMap.put(info[i], "1");
        }
        String[] getModeArray = {"0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
                "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"};
        if (null != infoMap.get("CUST")) {
            getModeArray[0] = "1";
        }
        if (null != infoMap.get("USER")) {
            getModeArray[2] = "1";
        }
        if (null != infoMap.get("ACCT")) {
            getModeArray[8] = "1";
        }
        getModeArray[5] = "1";
        getModeArray[6] = "1";
        getModeArray[10] = "1";
        getModeArray[14] = "1";
        getModeArray[17] = "1";

        // 安徽要求三户返回第8位：装机资源信息，对应输出参数中的RESOUREC_INFO
        if ("30".equals(province)) {
            getModeArray[7] = "1";
        }
        // 云南三户增加返回子产品信息 create by zhaok 10/20
        if ("86".equals(province)) {
            getModeArray[4] = "1";
        }

        String getMode = "";
        for (int i = 0; i < getModeArray.length; i++) {
            getMode += getModeArray[i];
        }
        return getMode;
    }

    private void preParam4ThreePart(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        // msg.put("getMode", createGetMode(msg.get("infoList").toString()));
        // 根据主副卡要求，对info_tag写死 //第16位由3修改为1 by wangmc 20180831
        msg.put("getMode", "1111111111100011111100001100001");
        // 辽宁固话与宽带同号情况需下发serviceClassCode
        if ("mpln".equals(exchange.getAppCode())) {
            msg.put("SERVICE_CLASS_CODE", msg.get("serviceClassCode"));//电信业务类别
        }
        body.put("msg", msg);
    }

    private void preParam4ThreePartProvince(Map body) {
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }

        msg.put("getMode", createGetMode(msg.get("infoList").toString(), msg.get("province").toString()));
        body.put("msg", msg);
    }

    private String dealRspCode4Bank(String code) {
        String[] orgCode = { "0000", "1202", "1014", "1124", "1203", "1204", "1205", "1406", "1303", "1014", "1305",
                "1306", "1307", "1303", "1309", "1404" };
        String[] aftCode = { "0000", "0100", "0002", "0003", "0200", "0300", "0400", "2000", "2001", "2002", "2003",
                "2004", "3001", "3002", "3003", "5001" };
        for (int i = 0; i < orgCode.length; i++) {
            if (orgCode[i].equals(code)) {
                return aftCode[i];
            }
        }
        return "9999";
    }

    /**
     * RHQ2018030100003-沃小号产品BSS实现--04_当tradeTypeCode为9999时新增返回编码1642、1643、1644、1645
     */
    private static String dealRspCodeForEss(String code, String tradeTypeCode, String appcode) {
        if ("1314".equals(tradeTypeCode) && "wxsb".equals(appcode)) {
            String[] orgCodeTrade = { "0000", "1202", "1014", "1124", "1203", "1204", "1205", "1406", "1303", "1014", "1305", "1306", "1307", "1303", "1309",
                    "1404", "1642", "1643", "1644", "1645" };
            String[] aftCodeTrade = { "0000", "0100", "0002", "0003", "0200", "0300", "0400", "2000", "2001", "2002", "2003", "2004", "3001", "3002", "3003",
                    "5001", "1642", "1643", "1644", "1645" };
            for (int i = 0; i < orgCodeTrade.length; i++) {
                if (orgCodeTrade[i].equals(code)) {
                    return aftCodeTrade[i];
                }
            }
        }

        String[] orgCode = { "0000", "1202", "1014", "1124", "1203", "1204", "1205", "1406", "1303", "1014", "1305", "1306", "1307", "1303", "1309", "1404" };
        String[] aftCode = { "0000", "0100", "0002", "0003", "0200", "0300", "0400", "2000", "2001", "2002", "2003", "2004", "3001", "3002", "3003", "5001" };
        for (int i = 0; i < orgCode.length; i++) {
            if (orgCode[i].equals(code)) {
                return aftCode[i];
            }
        }
        return "9999";
    }

    private String dealDate4AddMonth(int month, int day) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        cal.add(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return format.format(cal.getTime());
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }
}
