package com.ailk.ecaop.biz.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.processor.QryPhoneNumberAttrProcessor;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("userCardCheck")
public class UserCardCheckProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.query.ucck.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    String[] custArray = { "custId", "custName", "custType", "certEndDate", "certAddr", "certTypeCode", "certCode" };
    String[] userArray = { "userId", "userState", "brandCode", "openDate", "productId", "productName",
            "serviceClassCode" };

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = new HashMap();
        msg = (Map) body.get("msg");
        msg.put("serviceType", "01");
        String serviceClassCode = (String) msg.get("serviceClassCode");
        Map numberBlong = getNumberRealProvince(exchange, msg);
        //new QryPhoneNumberAttrProcessor().getNumberBlong(msg.get("serialNumber").toString());
        Map out = new HashMap();
        // 四川抛错特殊处理为使用下游返回的状态码 by wangmc 20170815
        String province = (String) numberBlong.get("province");
        if ("11|17|18|76|91|97".contains((String) numberBlong.get("province"))) {
            msg.putAll(numberBlong);
            msg.put("operatorId", "LFZB0148");
            msg.put("district", "110");
            msg.put("channelType", "1010101");
            msg.put("channelId", "L0949");
            msg.put("tradeTypeCode", "0040");
            msg.put("msg", msg);
            exchange.getIn().setBody(body);
            try {
                lan.preData(pmp[0], exchange);// ecaop.trade.n6.checkUserParametersMapping
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
                lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
            }
            catch (EcAopServerBizException e) {
                throw new EcAopServerBizException("0091".equals(e.getCode()) ? "0091" : "9999", e.getMessage());
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", e.getMessage());
            }
            out = dealResultN6(exchange.getOut().getBody(Map.class));
        }
        else {
            exchange.getIn().setBody(body);
            try {
                lan.preData(pmp[1], exchange);// ecaop.query.ucck.ParametersMapping
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive.0400");
                lan.xml2Json4ONS("ecaop.query.ucck.template", exchange);
            }
            catch (EcAopServerBizException e) {
                throw new EcAopServerBizException("0091".equals(e.getCode()) ? "0091" : "9999", e.getMessage());
            }
            catch (EcAopServerSysException e) {
                if ("81".equals(province)) { // 四川特殊处理 by wangmc 20170815
                    throw new EcAopServerBizException(e.getCode(), e.getMessage());
                }
                throw new EcAopServerBizException("9999", e.getMessage());
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", e.getMessage());
            }
            out = exchange.getOut().getBody(Map.class);
            Map userInfo = (Map) out.get("userInfo");
            Map custInfo = (Map) out.get("custInfo");
            Object simCard = userInfo.get("simCard");
            if (null != simCard) {
                out.put("simCard", simCard);
            }

            Map newCustInfo = new HashMap();
            MapUtils.arrayPut(newCustInfo, custInfo, custArray);

            if (null != newCustInfo.get("certEndDate")) {
                newCustInfo.put("certEndDate", newCustInfo.get("certEndDate") + "235959");
            }
            String certType = getCertName(newCustInfo.get("certTypeCode"));
            if (null != certType) {
                newCustInfo.put("certType", certType);
            }
            // 修复大客户信息返回问题 by wangmc 20170621
            if (!IsEmptyUtils.isEmpty(custInfo)) {
                newCustInfo.put("vipTypeCode", custInfo.get("custLvl"));
            }
            out.put("custInfo", newCustInfo);

            Map newUserInfo = new HashMap();
            newUserInfo.put("serviceClassCode", serviceClassCode);
            MapUtils.arrayPut(newUserInfo, userInfo, userArray);
            String brand = getBrandName(newUserInfo.get("brandCode"));
            if (null != brand) {
                newUserInfo.put("brand", brand);
            }
            String userId = (String) newUserInfo.get("userId");
            if (null == userId || "".equals(userId)) {
                out.put("userInfo", null);
            }
            else {
                out.put("userInfo", newUserInfo);
            }
        }
        exchange.getOut().setBody(out);
    }

    /**
     * 2.调用号卡中心,获取号码的归属地
     *
     * @param exchange
     * @param msg
     * @return
     */
    public Map getNumberRealProvince(Exchange exchange, Map msg) throws EcAopServerBizException {
        // 调用aop已有的号码归属地查询接口
        Map numberMap = MapUtils.asMap("number", msg.get("serialNumber"));
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, numberMap);
        try {
            new QryPhoneNumberAttrProcessor().process(threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            String errorCode = "9999";
            String detail = e.getMessage();
            if (e instanceof EcAopServerBizException) {
                errorCode = ((EcAopServerBizException) e).getCode();
                detail = ((EcAopServerBizException) e).getDetail();
            }
            throw new EcAopServerBizException(errorCode, "获取手机号码[" + msg.get("numId") + "]归属地出错:" + detail);
        }
        Map retMap = threePartExchange.getOut().getBody(Map.class);
        // 如果为查询到,则使用发起方省份编码
        if (IsEmptyUtils.isEmpty(retMap.get("province"))) {
            retMap.put("province", msg.get("province"));
        }
        if (IsEmptyUtils.isEmpty(retMap.get("city"))) {
            retMap.put("city", msg.get("city"));
        }
        return retMap;
    }

    /**
     * 根据证件编码获得证件名称
     * 01:15位身份证 , 02:18位身份证, 03:驾驶证, 04:军官证 , 05:教师证 , 06:学生证 , 07:营业执照 , 08:护照, 09:武警身份证 ,
     * 10:港澳居民来往内地通行证 , 11:台湾居民来往大陆通行证 , 12:户口本 , 13:组织机构代码证, 14:事业单位法人证书 ,15:介绍信,20:小微企业客户证件,21:民办非企业单位登记证书,99:其它
     */
    private String getCertName(Object certTypeCode) {
        if (null == certTypeCode)
            return null;
        String[] codes = { "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15",
                "20", "21", "99" };
        String[] names = { "15位身份证", "18位身份证", "驾驶证", "军官证", "教师证", "学生证", "营业执照", "护照", "武警身份证", "港澳居民来往内地通行证",
                "台湾居民来往大陆通行证", "户口本", "组织机构代码证", "事业单位法人证书", "介绍信", "小微企业客户证件", "民办非企业单位登记证书", "其它" };
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(certTypeCode)) {
                return names[i];
            }
        }
        return null;
    }

    /**
     * 根据品牌编码获得品牌名称
     * 1:世界风, 2:如意通, 3:新势力, 4:新时空, 5:联通商务, 6:其他, 8:无线上网卡, 9:沃
     */
    private String getBrandName(Object brandCode) {
        if (null == brandCode) {
            return null;
        }
        String[] codes = { "1", "2", "3", "4", "5", "6", "8", "9" };
        String[] names = { "世界风", "如意通", "新势力", "新时空", "联通商务", "其他", "无线上网卡", "沃" };
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(brandCode)) {
                return names[i];
            }
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map dealResultN6(Map inMap) {
        Map custInfo = ((List<Map>) inMap.get("custInfo")).get(0);
        Map userInfo = ((List<Map>) inMap.get("userInfo")).get(0);
        if (null == custInfo || null == userInfo) {
            throw new EcAopServerBizException("9999", "北六省份未返回用戶信息或客戶信息");
        }
        Map out = new HashMap();
        MapUtils.emptyPut(out, userInfo, getSimCard(userInfo));
        Map newCustInfo = new HashMap();
        MapUtils.arrayPut(newCustInfo, custInfo, custArray);
        MapUtils.arrayPut(newCustInfo, custInfo, new String[] { "certType", "vipTypeCode" });
        // 修复大客户信息返回问题 by wangmc 20170621
        Map vipCustInfo = (Map) custInfo.get("vipCustInfo");
        if (!IsEmptyUtils.isEmpty(vipCustInfo)) {
            newCustInfo.put("vipTypeCode", "vipTypeCode");
        }
        Map newUserInfo = MapUtils.asMap("brand", userInfo.get("brand"));
        MapUtils.arrayPut(newUserInfo, userInfo, userArray);
        out.put("custInfo", newCustInfo);
        out.put("userInfo", newUserInfo);
        return out;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object getSimCard(Map user) {
        List<Map> resInfo = (List<Map>) user.get("resInfo");
        if (null != resInfo && (!resInfo.isEmpty())) {
            for (Map temp : resInfo) {
                if ("1".equals(temp.get("resTypeCode"))) {
                    return temp.get("resCode");
                }
            }
        }
        else {
            if (null != user.get("sinCardNo")) {
                return user.get("sinCardNo");
            }
        }
        return null;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
