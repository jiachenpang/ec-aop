package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.CallProcessorUtils;
import com.ailk.ecaop.dao.product.lan.LanProductInfoQuery;

@EcRocTag("cbssAddressCheck")
public class CbssAddressCheckProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanProductInfoQuery prdDao = new LanProductInfoQuery();

    @Override
    public void process(Exchange exchange) throws Exception {
        CallProcessorUtils call = CallProcessorUtils.newCallIFaceUtils(exchange);
        call.preData("ecaop.trades.sack.cbres.paramtersmapping");
        call.wsCall("ecaop.comm.conf.url.cbss.services.resAnticipation4GSer");
        call.xml2Json("ecaop.trades.sack.cbres.template");

        Map outMap = exchange.getOut().getBody(Map.class);
        if (outMap.get("code") != null && !"0000".equals(outMap.get("code"))) {
            return;
        }
        List<Map> brand = (List) outMap.get("brandList");
        if (brand == null || brand.size() == 0) {
            throw new EcAopServerBizException("0004", "CBSS返回接入方式列表为空");
        }
        // 接入方式列表
        List<Map> accessList = (List) brand.get(0).get("accessList");

        if (accessList == null || accessList.size() == 0) {
            throw new EcAopServerBizException("0004", "CBSS返回接入方式列表为空");
        }

        // 商城需要的信息
        List<Map> installInfoList = new ArrayList<Map>();
        List accList = new ArrayList();
        for (int i = 0; i < accessList.size(); i++) {
            // 局向列表
            List<Map> exehList = (List) accessList.get(i).get("exchList");

            if (exehList == null || exehList.size() == 0) {
                throw new EcAopServerBizException("0003", "省份返回局向信息列表为空");
            }

            if (accessList.size() != exehList.size()) {
                throw new EcAopServerBizException("0002", "省份返回接入方式列表和局向信息列表不匹配");
            }

            Map installInfo = new HashMap();
            installInfo.put("maxRate", String.valueOf(accessList.get(i).get("maxRate")).replace("M", ""));
            installInfo.put("accessType", accessList.get(i).get("accessType"));
            installInfo.put("exchCode", exehList.get(i).get("exchCode"));
            installInfoList.add(installInfo);
            accList.add(accessList.get(i).get("accessType"));
        }

        // 获取商城最初的请求数据
        Map retMap = new HashMap();
        Map inMap = (Map) exchange.getProperty("sackParamIn", Map.class).get("msg");
        List proList = new ArrayList();
        if (0 == ((List) (inMap.get("productCodes"))).size()) {
            retMap.put("installInfo", installInfoList);
            exchange.getOut().setBody(retMap);
            return;
        }
        proList.addAll((List) (inMap.get("productCodes")));
        inMap.put("pros", proList);
        // 把接入方式传入入参中
        inMap.put("accList", accList);
        List ableList = prdCheck(inMap, installInfoList);
        retMap.put("installInfo", ableList);
        exchange.getOut().setBody(retMap);

    }

    private List prdCheck(Map inMap, List<Map> installInfo) {

        List<Map> retList = new ArrayList<Map>();

        List<Map> prdList = prdDao.queryAllPrdAccModCb(inMap);

        if (prdList == null || prdList.size() == 0) {
            throw new EcAopServerBizException("0005", "根据商品配置没有查询到: " + inMap.get("productCode"));
        }
        for (Map m : prdList) {
            int prdMaxRate = Integer.parseInt((String) m.get("BAND_WIDTH"));
            // 遍历省份返回的接入方式，如果能在数据库查询到的结果集取到就把数据库中查询到的商品id放到返回报文中
            for (int i = 0; i < installInfo.size(); i++) {
                Map temp = new HashMap();
                temp.putAll(installInfo.get(i));
                int prvMaxRate = Integer.parseInt((String) temp.get("maxRate"));
                // 省份速率小于产品速率
                if (prvMaxRate < prdMaxRate) {
                    continue;
                }
                temp.put("productCode", m.get("COMMODITY_ID"));
                retList.add(temp);
            }
        }

        if (retList.size() == 0) {
            throw new EcAopServerBizException("0006", "商品配置与省份返回不匹配");
        }
        return retList;
    }

    @Override
    public void applyParams(String[] params) {

    }

}
