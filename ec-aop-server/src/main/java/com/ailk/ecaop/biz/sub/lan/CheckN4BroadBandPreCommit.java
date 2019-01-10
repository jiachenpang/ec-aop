package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.common.helper.MagicNumber;

public class CheckN4BroadBandPreCommit {

    private final String MAIN_PRODUCT_TYPE = "01";

    public Map checkInputParam(Map body) {
        Map resourecInfo = new HashMap();
        Map msg = (Map) body.get("msg");
        dealContact(msg);
        if (null == msg.get("newUserInfo")) {
            throw new EcAopServerBizException("9999", "请填写客户资料");
        }
        Map newUserInfo = (Map) msg.get("newUserInfo");

        checkShareInfo(msg);
        checkAccessMode(newUserInfo);
        resourecInfo.putAll(dealExchInfo(msg));
        resourecInfo.put("appointment", checkAppoint(newUserInfo));

        if (null != newUserInfo.get("addressName")) {
            resourecInfo.put("addressName", newUserInfo.get("addressName"));
        }
        msg.put("resourecInfo", resourecInfo);

        // 处理合帐信息
        msg.putAll(checkAcctInfo(newUserInfo));

        // 处理产品信息,对请求的入参进行校验
        checkProductInfo(msg);

        msg.put("certType", newUserInfo.get("certType"));
        msg.put("certNum", newUserInfo.get("certNum"));
        body.put("msg", msg);
        return body;
    }

    /**
     * 校验接入方式
     * 
     * @param newUserInfo
     */
    private void checkAccessMode(Map newUserInfo) {
        if (null == newUserInfo.get("accessMode")) {
            throw new EcAopServerBizException("9999", "接入方式accessMode为空，请校验");
        }
    }

    /**
     * 校验字符串是否全为数字
     */
    private boolean isNumber(String str) {
        try {
            Integer.parseInt(str);
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 处理装机时间
     * 
     * @param newUserInfo
     * @return
     */
    private Object checkAppoint(Map newUserInfo) {
        Object hopeDate = newUserInfo.get("hopeDate");
        if (null == hopeDate) {
            throw new EcAopServerBizException("9999", "请填写期待装机时间：hopeDate");
        }
        if (14 == hopeDate.toString().length()) {
            return hopeDate;
        }
        String[] str = hopeDate.toString().split("-");
        if (3 != str.length) {
            throw new EcAopServerBizException("9999", "预装机时间：'" + hopeDate + "'格式不符合'YYYY-MM-DD'格式");
        }
        return str[0] + str[1] + str[2] + "000000";
    }

    /**
     * 处理帐户信息的校验
     * 
     * @param newUserInfo
     * @return
     */
    private Map checkAcctInfo(Map newUserInfo) {

        Map dataMap = new HashMap();
        if ("0".equals(newUserInfo.get("createOrExtendsAcct"))) {
            dataMap.put("isNewAcct", "0");
            return dataMap;
        }

        Object debutySerialNumber = newUserInfo.get("debutySerialNumber");
        if (null == debutySerialNumber) {
            throw new EcAopServerBizException("9999", "继承老帐户时,合帐号码必传");
        }

        Object serviceClasscode = newUserInfo.get("serviceClassCode");
        if (null == serviceClasscode) {
            throw new EcAopServerBizException("9999", "请传serviceClassCode,0100:固网,0000:移动号码");
        }

        if (MagicNumber.LAN_SERVICE_CLASS.equals(serviceClasscode)) {
            if (null == newUserInfo.get("debutyAreaCode")) {
                throw new EcAopServerBizException("9999", "合帐号码格式不正确");
            }
        }
        else if (!MagicNumber.MOB_SERVICE_CLASS.equals(serviceClasscode)) {
            throw new EcAopServerBizException("9999", "合帐号码电信网别不正确");
        }

        // 定义标识，当isNewAcct为1时，表明是合帐，后面要调用三户查询接口
        dataMap.put("isNewAcct", "1");
        return dataMap;
    }

    /**
     * 检查产品信息
     * 
     * @param msg
     * @param newUserInfo
     * @param userInfo
     */
    private void checkProductInfo(Map msg) {
        Map newUserInfo = (Map) msg.get("newUserInfo");
        if (null == newUserInfo.get("productInfo")) {
            throw new EcAopServerBizException("9999", "请选择产品信息");
        }
        List<Map> productList = (List<Map>) newUserInfo.get("productInfo");
        String mainProd = "";
        for (Map product : productList) {
            if ("1".equals(product.get("productMode"))) {
                mainProd = product.get("productId").toString();
                msg.put("product", mainProd);
                msg.put("mainProd", mainProd);
                break;
            }
        }
        if ("".equals(mainProd)) {
            throw new EcAopServerBizException("9999", "请选择主产品！");
        }
        qryMainProductInfo(msg);
    }

    /**
     * 获取主产品相关信息
     * 
     * @param msg
     */
    private void qryMainProductInfo(Map msg) {

        // 入参的准备
        Map inputMap = new HashMap();
        String mainProd = msg.get("mainProd").toString();
        inputMap.put("product", mainProd);
        inputMap.put("province", msg.get("province"));
        inputMap.put("eparchy", msg.get("city"));

        Esql dao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/prd/LanProductInfoQuery.esql");
        ArrayList<Map> mainProduct = dao.id("qryMainProductInfo").params(inputMap).execute();
        if (0 == mainProduct.size()) {
            throw new EcAopServerBizException("9999", "根据商品ID:'" + mainProd + "'获取不到固网产品,请校验");
        }

        Map userInfo = null == msg.get("userInfo") ? new HashMap() : (Map) msg.get("userInfo");
        for (Map mainPro : mainProduct) {
            if (MAIN_PRODUCT_TYPE.equals(mainPro.get("productMode"))) {
                userInfo.put("prepayTag", mainPro.get("prePayTag"));
                msg.put("mainProd", mainPro.get("productId"));
                msg.put("provinceBrandCode", mainPro.get("brandCode"));
                break;
            }
        }
        msg.put("userInfo", userInfo);
    }

    /**
     * 校验共线号码信息
     * 
     * @param msg
     */
    private void checkShareInfo(Map msg) {
        if ("1".equals(msg.get("shareFalg"))) {

            Object shareSerialNumber = msg.get("shareSerialNumber");
            if (null == shareSerialNumber) {
                throw new EcAopServerBizException("9999", "共线时,共线固话号码必传");
            }

            Object shareAreaCode = msg.get("shareAreaCode");
            if (null == shareAreaCode) {
                throw new EcAopServerBizException("9999", "共线时,共线号码区号必传");
            }

            Map userInfo = null == msg.get("userInfo") ? new HashMap() : (Map) msg.get("userInfo");
            userInfo.put("relyNumber", shareAreaCode.toString() + shareSerialNumber.toString());
            msg.put("userInfo", userInfo);
        }
    }

    /**
     * 处理联系人信息和联系人电话
     * 校验通过时,将联系信息放入userInfo节点
     * 
     * @param msg
     */
    private void dealContact(Map msg) {
        String contactPerson = msg.get("contactPerson").toString();
        if (isNumber(contactPerson)) {
            throw new EcAopServerBizException("9999", "联系人姓名:'" + contactPerson + "'全为数字,不满足要求！");
        }

        String contactPhone = msg.get("contactPhone").toString();
        if (contactPhone.length() < 7) {
            throw new EcAopServerBizException("9999", "联系人电话:'" + contactPhone + "'长度不足7位");
        }
    }

    /**
     * 处理局向信息
     */
    private Map dealExchInfo(Map msg) {
        Map resourecInfo = null == msg.get("resourecInfo") ? new HashMap() : (Map) msg.get("resourecInfo");
        if (null != msg.get("newUserInfo")) {
            Map newUserInfo = (Map) msg.get("newUserInfo");
            List<Map> exchInfo = null == newUserInfo.get("exchInfo") ? new ArrayList<Map>() : (List<Map>) newUserInfo
                    .get("exchInfo");
            for (Map exch : exchInfo) {
                if ("SERVICE_CODE".equals(exch.get("key"))) {
                    resourecInfo.put("serviceCode", exch.get("value"));
                }
            }
            resourecInfo.put("installAddress", newUserInfo.get("installAddress"));
            if (null != newUserInfo.get("addressCode")) {
                resourecInfo.put("addressCode", newUserInfo.get("addressCode"));
            }
            resourecInfo.put("exchCode", newUserInfo.get("exchCode"));
        }
        return resourecInfo;
    }
}
