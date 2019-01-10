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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("simpleCustCheck")
public class SimpleCustCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        body.put("methodCode", exchange.getMethodCode());
        Map msg = (Map) body.get("msg");
        if (null != msg.get("serialNumber") && !"".equals(msg.get("serialNumber"))) {
            if (!MagicNumber.MOB_SERVICE_CLASS.equals(msg.get("serviceClassCode"))) {
                throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "客户资料查询简单版不支持非移网号码查询");
            }
            msg.put("operateType", "8");
        }
        else {
            msg.put("operateType", "1");
            Object certType = msg.get("certType");
            if (null == certType || "".equals(certType)) {
                throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "证件类型为空,业务无法继续");
            }
            Object certNum = msg.get("certNum");
            if (null == certNum || "".equals(certNum)) {
                throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "证件号码为空,业务无法继续");
            }
            msg.put("certTypeCode", CertTypeChangeUtils.certTypeMall2Fbs(certType.toString()));
            msg.put("certCode", certNum);
        }
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.cuss.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.CustSer");
        lan.xml2Json("ecaop.trades.cuss.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) retMap.get("custInfo");
        exchange.getOut().setBody(dealCustInfo(custInfo));
    }

    private Map dealCustInfo(List<Map> custInfo) {
        if (null == custInfo || 0 == custInfo.size()) {
            return new HashMap();
        }
        ArrayList<Map> retList = new ArrayList<Map>();
        String[] copyStr = { "operTag", "custId", "custName", "custType", "custState", "certTypeCode", "certCode",
                "eparchyCode", "cityCode", "inDate", "partyIsbwl", "removeTag", "groupInfo", "grouId", "groupName" };
        for (Map cust : custInfo) {
            Map temp = new HashMap();
            MapUtils.arrayPut(temp, cust, copyStr);
            temp.put("certTypeCode", CertTypeChangeUtils.certTypeFbs2Mall(temp.get("certTypeCode").toString()));
            retList.add(temp);
        }
        Map customer = new HashMap();
        customer.put("custInfo", retList);
        return customer;
    }
}
