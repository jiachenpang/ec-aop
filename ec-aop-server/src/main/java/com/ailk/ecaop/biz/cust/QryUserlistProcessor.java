package com.ailk.ecaop.biz.cust;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.processor.TranscodeMappingProcessor;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("QryUserlist")
public class QryUserlistProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        LanUtils lan = new LanUtils();
        // 准备参数
        lan.preData("ecaop.masb.qcus.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive4cbss.0002");
        urlDecode(exchange);
        TranscodeMappingProcessor transcode = new TranscodeMappingProcessor();
        String[] chkUsrStr = { "ecaop.masb.qcus.template" };
        transcode.applyParams(chkUsrStr);
        transcode.process(exchange);
        Map qryUserMap = exchange.getOut().getBody(Map.class);
        Map userRetMap = QryUserInfoReturn(qryUserMap);
        if (userRetMap.get("groupId") == null) {
            throw new EcAopServerBizException("9999", "群组号为空");
        }
        // 传入groupid
        Map body = exchange.getIn().getBody(Map.class);
        // msg从字符串转为map
        boolean isString = body.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
        msg.put("userId", userRetMap.get("groupId"));
        msg.put("getMode", "101000000");
        exchange.getIn().getBody(Map.class).put("msg", msg);
        // 从三户接口查询数据
        lan.preData("ecaop.trade.cbss.checkUserParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        userRetMap.remove("groupId");
        Map threeuserMap = exchange.getOut().getBody(Map.class);

        if (!(threeuserMap == null || threeuserMap.isEmpty())) {
            // 判断custInfo是否为空
            List<Map> custInfoList = (List<Map>) threeuserMap.get("custInfo");
            if (!(custInfoList == null || custInfoList.isEmpty())) {
                String[] threeuserArray = { "certTypeCode", "certCode", "custName",
                        "certAddr" };
                MapUtils.arrayPut(userRetMap, custInfoList.get(0), threeuserArray);
                String certTypeCode = userRetMap.get("certTypeCode").toString();
                userRetMap.put("certTypeCode", certTypeCodeChange2Mall(certTypeCode));
            }
        }
        elementListRet(threeuserMap, userRetMap);
        exchange.getOut().setBody(userRetMap);
    }

    private Map QryUserInfoReturn(Map TranscodeMap) {
        if (TranscodeMap == null || TranscodeMap.isEmpty()) {
            throw new EcAopServerBizException("9999", "未获取成员列表信息！");
        }
        // acctRationalInfo可以为空是200，抛异常让它不为200（）
        Map userMap = (Map) TranscodeMap.get("acctRationalInfo");
        if (userMap == null || userMap.isEmpty()) {
            throw new EcAopServerBizException("9999", "账户付费关系查询为空!");
        }
        List<Map> userInfoList = (List<Map>) userMap.get("userInfo");
        if (userInfoList == null || userInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "用户信息为空!");
        }
        // 从userInfo节点获取服务号码和主号码放入list中
        String[] userInfoArray = { "userNumber", "mainUserNum" };
        List<Map> userInfo = new ArrayList();
        // 计算出满足条件的count值
        int count = -1;
        for (int i = 0; i < userInfoList.size(); i++) {
            Map tempMap = userInfoList.get(i);
            if ("07".equals(tempMap.get("netType")) && "03".equals(tempMap.get("groupType"))) {
                count = i;
                Map userInfoMap = new HashMap();
                Object mainUserNum = tempMap.get("mainUserNum");
                if (mainUserNum == null || "".equals(mainUserNum)) {
                    userInfoMap.put("userNumber", tempMap.get("userNumber"));
                    userInfoMap.put("mainUserNum", 0);
                }
                else {
                    MapUtils.arrayPut(userInfoMap, tempMap, userInfoArray);
                }
                userInfo.add(userInfoMap);
            }
        }
        if (userInfo == null || userInfo.isEmpty()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "未获取服务号码和主号码标志!");
        }
        MapUtils.arrayPut(userMap, userInfoList.get(count), new String[] { "accProvince", "accCity", "productId",
                "productName", "groupId" });
        userMap.put("userInfo", userInfo);
        return userMap;
    }

    private void urlDecode(Exchange exchange) {
        String urlString = (String) exchange.getOut().getBody();
        try {
            urlString = URLDecoder.decode(urlString, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new EcAopRequestBizException(e.getMessage());
        }
        exchange.getOut().setBody(urlString);
    }

    private void elementListRet(Map threeuserMap, Map userListretMap) {
        List<Map> userInfoList = (List<Map>) threeuserMap.get("userInfo");
        if (userInfoList == null || userInfoList.isEmpty()) {
            return;
        }
        List elementList = new ArrayList();
        for (Map tempMap : userInfoList) {
            if (!userListretMap.get("productId").equals(tempMap.get("productId"))) {
                continue;
            }
            List<Map> discntInfoList = (List<Map>) tempMap.get("discntInfo");
            if (discntInfoList == null || discntInfoList.isEmpty()) {
                continue;
            }
            for (Map temp1 : discntInfoList) {
                if (userListretMap.get("productId").equals(temp1.get("productId"))) {
                    Map threeUserRetMap = new HashMap();
                    threeUserRetMap.put("elementId", temp1.get("discntCode"));
                    MapUtils.emptyPut(threeUserRetMap, temp1, "packageId");
                    elementList.add(threeUserRetMap);
                }
            }
        }
        if (0 != elementList.size()) {
            userListretMap.put("elementList", elementList);
        }
    }

    private String certTypeCodeChange2Mall(String certTypeCode) {
        if (1 == certTypeCode.length()) {
            return CertTypeChangeUtils.certTypeCbss2Mall(certTypeCode);
        }
        return CertTypeChangeUtils.certTypeFbs2Mall(certTypeCode);
    }

}
