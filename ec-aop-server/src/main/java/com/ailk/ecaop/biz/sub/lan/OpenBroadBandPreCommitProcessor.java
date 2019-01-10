package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.biz.cust.CustCheckProcessor;
import com.ailk.ecaop.biz.number.ChkLineNumProcessor;
import com.ailk.ecaop.biz.number.QryLangLineNumProcessor;
import com.ailk.ecaop.biz.user.CheckUserInfoProcessor;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("openBroadBandPreCommit")
public class OpenBroadBandPreCommitProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String methodCode = exchange.getMethodCode();
        Map custInfo = new HashMap();
        Map acctInfo = new HashMap();

        // 调用客户信息校验之前，请求参数的保留备份
        Map body = exchange.getIn().getBody(Map.class);

        // 请求参数的基本校验
        CheckN4BroadBandPreCommit check = new CheckN4BroadBandPreCommit();
        check.checkInputParam(body);

        // 共线信息跟合帐信息的获取
        Map shareInfo = getShareInfo(body);
        Map extendsAcctInfo = getExtendsAcctInfo(body);

        // 判断共线信息和合帐信息是否相同
        boolean shareNumberEuqalAcct = shareNumberEqualExtendsAcct(shareInfo, extendsAcctInfo);

        // 方法名的校验,校验方法名的合法性和如何走分支
        checkMethod(body, methodCode);

        // 非共线情况时,根据入参校验客户信息,调用枢纽客户信息查询接口
        if ("0".equals(shareInfo.get("shareFalg"))) {
            exchange.getIn().setBody(body);
            CustCheckProcessor custCheck = new CustCheckProcessor();
            custCheck.process(exchange);
            custInfo = (Map) exchange.getOut().getBody(Map.class).get("customer");
        }
        else { // 共线时，调用三户信息查询接口
            Map msg = (Map) body.get("msg");
            msg.put("getMode", "100000001000");
            msg.putAll(shareInfo);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            CheckUserInfoProcessor checkUser = new CheckUserInfoProcessor();
            checkUser.process(exchange);
            custInfo = (Map) exchange.getOut().getBody(Map.class).get("custInfo");

            // 共线号码与合帐号码相同时,直接继承帐户信息
            if (shareNumberEuqalAcct) {
                acctInfo = (Map) exchange.getOut().getBody(Map.class).get("acctInfo");
            }
        }

        // 将一开始的信息重新放入
        Map checkUserInfo = JSON.parseObject((String) body.get("msg"));

        // 只有isNewAcct标识为1时,才会调用三户信息查询接口,表明此时是继承老帐户信息
        if ("1".equals(checkUserInfo.get("isNewAcct")) && !shareNumberEuqalAcct) {
            Map msg = JSON.parseObject((String) body.get("msg"));
            msg.put("getMode", "000000001000");
            msg.putAll(extendsAcctInfo);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            CheckUserInfoProcessor checkUser = new CheckUserInfoProcessor();
            checkUser.process(exchange);
            acctInfo = (Map) exchange.getOut().getBody(Map.class).get("acctInfo");
        }
        else if (!shareNumberEuqalAcct) { // 非共线且非合帐时,创建新帐户信息
            createAcct(acctInfo, checkUserInfo);
        }

        // 调用空闲号码查询接口
        Map qryLangNumberInfo = JSON.parseObject((String) body.get("msg"));
        LanUtils lan = new LanUtils();
        qryLangNumberInfo.put("cityCode", lan.dealCityCode(qryLangNumberInfo));
        body.put("msg", qryLangNumberInfo);
        exchange.getIn().setBody(body);
        QryLangLineNumProcessor qryLangLineNum = new QryLangLineNumProcessor();
        qryLangLineNum.process(exchange);

        // 处理返回号码
        dealNumberInfo(exchange, body);

        // 调用号码预占接口
        exchange.getIn().setBody(body);
        ChkLineNumProcessor chkLinNum = new ChkLineNumProcessor();
        chkLinNum.process(exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        ArrayList<Map> respNumber = (ArrayList<Map>) retMap.get("respNumber");
        for (Map res : respNumber) {
            if ("0000".equals(res.get("respNumCode"))) {
                continue;
            }
            throw new EcAopServerBizException(res.get("respNumCode").toString(), "ChkLineNum接口返回:"
                    + res.get("respNumDesc").toString());
        }

        // 调用预提交接口
        Map msg = JSON.parseObject((String) body.get("msg"));
        dealUserInfo(msg);
        msg.put("customer", custInfo);
        msg.put("account", acctInfo);
        msg.put("product", dealProduct(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        SoleBusiAccnt soleBusiAccnt = new SoleBusiAccnt();
        soleBusiAccnt.process(exchange);
        Map message = exchange.getOut().getBody(Map.class);
        if (null != message.get("soleBusiAccntInfo")) {
            Map soleBusiAccntInfo = (Map) message.get("soleBusiAccntInfo");

            // 处理返回参数
            Map result = dealReturn(soleBusiAccntInfo);
            if (null == result.get("userName")) {
                result.put("userPasswd", msg.get("userPasswd"));
                result.put("userName", msg.get("areaCode").toString() + msg.get("number"));
            }
            exchange.getOut().setBody(result);
        }
    }

    /**
     * 处理号码信息,返回的是一个List,只取头一个号码信息
     */
    private void dealNumberInfo(Exchange exchange, Map body) {
        Map msg = JSON.parseObject((String) body.get("msg"));
        Map userInfo = null == msg.get("userInfo") ? new HashMap() : (Map) msg.get("userInfo");

        // 从空闲号码查询的返回报文中获取空闲号码
        Map result = exchange.getOut().getBody(Map.class);
        List<Map> numberInfo = (List<Map>) result.get("numInfo");
        if (0 == numberInfo.size()) {
            throw new EcAopServerBizException("9999", "省分无固网号码");
        }
        Map multiNumber = numberInfo.get(0);
        String number = multiNumber.get("number").toString();
        String area = multiNumber.get("areaCode").toString();
        multiNumber.put("number", number.substring(area.length(), number.length()));
        userInfo.put("number", number);
        msg.put("userInfo", userInfo);
        msg.putAll(multiNumber);
        msg.put("areaCode", area);
        body.put("msg", msg);
    }

    /**
     * 产品的处理
     */
    private ArrayList<Map> dealProduct(Map msg) {
        ArrayList<Map> product = new ArrayList<Map>();
        Map inputMap = new HashMap();
        inputMap.put("province", msg.get("province"));
        inputMap.put("eparchy", msg.get("city"));
        inputMap.put("product", msg.get("product"));
        DealProductInfo dealProd = new DealProductInfo();
        product = dealProd.dealProduct(inputMap);
        return product;
    }

    /**
     * 处理用户信息
     */
    private void dealUserInfo(Map msg) {
        Map userInfo = null == msg.get("userInfo") ? new HashMap() : (Map) msg.get("userInfo");
        String useNumber = userInfo.get("number").toString();
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String contactPerson = msg.get("contactPerson").toString();
        String contactPhone = msg.get("contactPhone").toString();
        if (useNumber.length() < 6) {
            throw new EcAopServerBizException("9999", "宽带号码:'" + useNumber + "'长度不足6位");
        }
        ArrayList<Map> attrInfo = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        Map inputMap = new HashMap();
        inputMap.put("product", msg.get("product"));
        inputMap.put("province", msg.get("province"));
        inputMap.put("eparchy", msg.get("city"));
        inputMap.put("accessMode", newUserInfo.get("accessMode"));
        Esql dao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/prd/LanProductInfoQuery.esql");
        attrInfo = dao.id("qryUserAttr").params(inputMap).execute();
        attrInfo.add(lan.createAttrInfo("LINK_NAME", contactPerson));
        attrInfo.add(lan.createAttrInfo("LINK_PHONE", contactPhone));
        userInfo.put("brandCode", "2000");
        userInfo.put("provinceBrandCode", msg.get("provinceBrandCode"));
        userInfo.put("productId", msg.get("mainProd"));
        userInfo.put("eparchyCode", msg.get("city"));
        userInfo.put("cityCode", lan.dealCityCode(msg));
        userInfo.put("userTypeCode", "0");
        userInfo.put("serialNumber", userInfo.get("number"));
        Object userPasswd = msg.get("userPasswd");
        if (null != userPasswd) {
            userInfo.put("userPasswd", userPasswd.toString());
        }
        userInfo.put("attrInfo", attrInfo);
        lan.dealDevelopInfo(userInfo, msg);
    }

    /**
     * 费用的处理
     */
    private void dealFee(Map feeInfo) {
        feeInfo.put("maxRelief", null==feeInfo.get("maxRelief") || "0".equals(feeInfo.get("maxRelief"))?"0":feeInfo.get("maxRelief")+"0");
        feeInfo.put("origFee", null==feeInfo.get("origFee") || "0".equals(feeInfo.get("origFee"))?"0":feeInfo.get("origFee")+"0");
        feeInfo.remove("operatorType");
    }

    /**
     * 判断方法名,根据不同的方法名进行不同的校验,走不同的分支
     * 宽带无条件受理新装：0132
     * 宽带无条件受理加装：0133
     * 宽带新装：0009
     * 宽带加装：0011
     */
    private void checkMethod(Map body, String methodCode) {
        Map msg = new HashMap(JSON.parseObject(String.valueOf(body.get("msg"))));
        if ("bsop".equals(methodCode)) {
            if ("0".equals(msg.get("shareFalg"))) {
                msg.put("tradeTypeCode", "0009");
            }
            else {
                msg.put("tradeTypeCode", "0011");
            }
        }
        else if ("bson".equals(methodCode)) {
            if ("0".equals(msg.get("shareFalg"))) {
                msg.put("tradeTypeCode", "0132");
            }
            else {
                msg.put("tradeTypeCode", "0133");
            }
        }
        else {
            throw new EcAopServerBizException("9999", "method:'" + methodCode + "'使用方式错误！");
        }
        body.put("msg", msg);
    }

    /**
     * 创建帐户信息
     */
    private void createAcct(Map acctInfo, Map checkUserInfo) {
        acctInfo.put("isNew", "0");
        Map newAcctInfo = new HashMap();
        Map newUserInfo = (Map) checkUserInfo.get("newUserInfo");
        newAcctInfo.put("payName", newUserInfo.get("certName"));
        newAcctInfo.put("payModeCode", "0");
        newAcctInfo.put("payPasswd", "000000");
        newAcctInfo.put("payAddress", newUserInfo.get("certAddress"));
        newAcctInfo.put("payPostCode", "000000");
        newAcctInfo.put("payContact", checkUserInfo.get("contactPerson"));
        newAcctInfo.put("payContactPhone", checkUserInfo.get("contactPhone"));
        acctInfo.put("newAcctInfo", newAcctInfo);
    }

    /**
     * 处理返回信息，美化返回给商城的参数
     */
    private Map dealReturn(Map soleBusiAccntInfo) {
        Map ret = new HashMap();
        ret.put("provOrderId", soleBusiAccntInfo.get("provinceOrderId"));
        if (null != soleBusiAccntInfo.get("preFeeInfoRsp")) {
            List<Map> feeInfo = (List<Map>) soleBusiAccntInfo.get("preFeeInfoRsp");
            if (0 != feeInfo.size()) {
                for (Map fee : feeInfo) {
                    dealFee(fee);
                }
                ret.put("feeInfo", feeInfo);
            }
        }
        if (soleBusiAccntInfo.get("totalFee").toString().startsWith("0")) {
            ret.put("totalFee", soleBusiAccntInfo.get("totalFee"));
        }
        else {
            ret.put("totalFee",null==soleBusiAccntInfo.get("totalFee") || "0".equals(soleBusiAccntInfo.get("totalFee"))?"0":soleBusiAccntInfo.get("totalFee")+ "0");
        }
        Map broadbandInfo = (Map) soleBusiAccntInfo.get("broadbandInfo");
        if (null == broadbandInfo) {
            throw new EcAopServerBizException("9999", "省分未返回宽带帐户信息");
        }
        ret.put("userName", broadbandInfo.get("broadbandId"));
        ret.put("userPasswd", broadbandInfo.get("broadbandPassword"));
        return ret;
    }

    /**
     * 判断共线号码和合帐号码是否一致
     * 若一致，返回true,反之，返回false
     */
    private boolean shareNumberEqualExtendsAcct(Map shareInfo, Map extendsAcctInfo) {

        // 不共线或不合帐时,无需继续判断
        if ("0".equals(shareInfo.get("shareFalg")) || "0".equals(extendsAcctInfo.get("createOrExtendsAcct"))) {
            return false;
        }

        // 选择合帐,合帐方式为移网号码时,无需继续判断
        if ("0000".equals(extendsAcctInfo.get("serviceClassCode"))) {
            return false;
        }
        String shareAreaCode = shareInfo.get("areaCode").toString();
        String shareSerialNumber = shareInfo.get("serialNumber").toString();
        String debutyAreaCode = extendsAcctInfo.get("areaCode").toString();
        String debutySerialNumber = extendsAcctInfo.get("serialNumber").toString();

        if (shareAreaCode.equals(debutyAreaCode) && shareSerialNumber.equals(debutySerialNumber)) {
            return true;
        }
        return false;
    }

    /**
     * 缓存共线信息
     */
    private Map getShareInfo(Map body) {
        Map msg = (Map) body.get("msg");
        Map shareInfo = new HashMap();
        shareInfo.put("shareFalg", msg.get("shareFalg"));
        if ("1".equals(shareInfo.get("shareFalg"))) {
            shareInfo.put("serviceClassCode", "0100");
            shareInfo.put("areaCode", msg.get("shareAreaCode"));
            shareInfo.put("serialNumber", msg.get("shareSerialNumber"));
        }
        return shareInfo;
    }

    /**
     * 缓存合帐信息
     */
    private Map getExtendsAcctInfo(Map body) {
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        Map extendsAcctInfo = new HashMap();
        extendsAcctInfo.put("createOrExtendsAcct", newUserInfo.get("createOrExtendsAcct"));
        if ("1".equals(extendsAcctInfo.get("createOrExtendsAcct"))) {
            extendsAcctInfo.put("serviceClassCode", newUserInfo.get("serviceClassCode"));
            extendsAcctInfo.put("areaCode", newUserInfo.get("debutyAreaCode"));
            extendsAcctInfo.put("serialNumber", newUserInfo.get("debutySerialNumber"));
        }
        return extendsAcctInfo;
    }
}
