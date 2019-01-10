/*********************************************************************************************************************
 * 说明:此processor用于调用客户资料验证接口
 * ActivityCode:T2000501
 * 注意事项:此processor除负责准备接口入参、调用接口、处理回参外,还负责整理参数,即将枢纽返回的报文进行整理,放入Exchange,以备下一个processor使用
 * 方法dealNewCustInfo:用于处理新客户信息
 * 方法dealOldCustInfo:用于处理老客户信息
 * 根据不同的URL中不同的method,准备不同的数据,准备过程的相关说明如下：
 * method命名规则：
 * 前缀:
 * newCustInfo:处理新客户信息
 * oldCustInfo:处理老客户信息
 * 后缀:
 * instser:单业务新装预提交
 * REMARK：
 * 针对客户资料校验接口,需特殊处理,判断接口返回成功和失败时,使用的是code,而非HTTP_STATUSCODE
 * 因此返回新客户时,需要对HTTP_STATUSCODE进行重新设置
 ********************************************************************************************************************/
package com.ailk.ecaop.biz.cust;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.processor.URLDecodeProcessor;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("custCheck")
public class CustCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        body.put("methodCode", exchange.getMethodCode());
        Map customer = new HashMap();
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.cuck.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive.0002");
        URLDecodeProcessor decode = new URLDecodeProcessor();
        decode.process(exchange);
        lan.Xml2Json4Cust("ecaop.masb.cuck.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        if (null == retMap.get("code") || "0092".equals(retMap.get("code"))) {
            customer = dealOldCustInfo(retMap, body);
        }
        else {
            if ("0001".equals(retMap.get("code"))) {
                customer = dealNewCustInfo(body);
                exchange.setProperty(Exchange.HTTP_STATUSCODE, 200);
            }
            else {
                customer = retMap;
            }
        }
        exchange.getOut().setBody(customer);
    }

    public void process(Exchange exchange, boolean isNoDeal) throws Exception {
        if (!isNoDeal) {
            return;
        }
        exchange.setMethod("ecaop.trades.query.comm.cust.check");
        exchange.setMethodCode("cuck");
        TransReqParamsMappingProcessor trpmp = new TransReqParamsMappingProcessor();
        trpmp.process(exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
        Map custCheckRetMap = JSON.parseObject((String) exchange.getOut().getBody());
        int respCode = Integer.valueOf(exchange.getProperty(Exchange.HTTP_STATUSCODE).toString());
        String detail = (String) custCheckRetMap.get("detail");
        if (600 == respCode) {
            throw new EcAopServerBizException((String) custCheckRetMap.get("code"), detail);
        }
        if (560 == respCode && !"0001".equals(custCheckRetMap.get("code"))) {
            throw new EcAopServerBizException((String) custCheckRetMap.get("code"), detail);
        }
    }

    /**
     * 处理新客户信息
     * 
     * @param body
     */
    private Map dealNewCustInfo(Map body) {
        Map customer = new HashMap();
        Map msg = JSON.parseObject((String) body.get("msg"));
        String methodCode = body.get("methodCode").toString();
        if ("bsop".equals(methodCode) || "bson".equals(methodCode)) {
            customer.put("customer", newCustInfo4Instser(msg));
        }
        return customer;
    }

    /**
     * 处理老客户信息
     * 
     * @param retMap
     * @param body
     */
    private Map dealOldCustInfo(Map retMap, Map body) {
        Map customer = new HashMap();
        String methodCode = body.get("methodCode").toString();
        if ("bsop".equals(methodCode) || "bson".equals(methodCode)) {
            customer.put("customer", oldCustInfo4Instser(retMap));
        }
        return customer;
    }

    /**
     * 为单业务新装预提交准备新用户信息
     * 
     * @param body
     * @param cityCode
     * @return
     */
    private Map newCustInfo4Instser(Map msg) {
        Map custInfo = new HashMap();
        LanUtils lan = new LanUtils();
        Map newUserInfo = (Map) msg.get("newUserInfo");
        Map newCustInfo = null == msg.get("newCustInfo") ? new HashMap() : (Map) msg.get("newCustInfo");
        String certType = newUserInfo.get("certType").toString();
        newCustInfo.put("certType", CertTypeChangeUtils.certTypeMall2Fbs(certType));
        newCustInfo.put("custName", newUserInfo.get("certName"));
        newCustInfo.put("custType", "0");
        newCustInfo.put("certEndDate", newUserInfo.get("certExpireDate") + "000000");
        newCustInfo.put("certCode", newUserInfo.get("certNum"));
        newCustInfo.put("certAddr", newUserInfo.get("certAddress"));
        newCustInfo.put("provinceCode", msg.get("province"));
        newCustInfo.put("eparchyCode", msg.get("city"));
        newCustInfo.put("cityCode", lan.dealCityCode(msg));
        newCustInfo.put("contact", msg.get("contactPerson"));
        newCustInfo.put("contactPhone", msg.get("contactPhone"));
        String contactAddress = msg.get("contactAddress").toString();
        if (null == contactAddress || "".equals(contactAddress)) {
            contactAddress = newCustInfo.get("certAddr").toString();
        }
        newCustInfo.put("postAddress", contactAddress);
        lan.dealDevelopInfo(newCustInfo, msg);
        custInfo.put("isNew", "0");
        custInfo.put("newCustInfo", newCustInfo);
        return custInfo;
    }

    /**
     * 为单业务新装预提交准备老客户信息
     * 
     * @param body
     * @return
     */
    private Map oldCustInfo4Instser(Map retMap) {
        Map custInfo = new HashMap();
        List<Map> custList = (List<Map>) retMap.get("existedCustomer");
        String custId = "";
        for (Map cust : custList) {
            if ("0".equals(cust.get("blackListFlag"))) {
                custId = cust.get("custId").toString();
                break;
            }
        }
        if ("".equals(custId)) {
            throw new EcAopServerBizException("9999", "客户校验失败!无可用客户信息");
        }
        custInfo.put("custId", custId);
        custInfo.put("isNew", "1");
        return custInfo;
    }
}
