package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * @author May
 * @version 1.0.1
 * @since 2013-05-09 19:54
 *        宽带产品信息的处理
 *        STEP 1:将业务和资费分开处理，原因：两部分节点名不一致
 *        STEP 2:处理业务信息，将service标识相同的业务属性收集在一起
 *        STEP 3:处理业务信息，将discnt标识相同的业务属性收集在一起
 *        STEP 4:将服务属性放入package
 *        STEP 5:将资费属性放入package
 *        STEP 6:将产品包信息放入产品列表
 */
public class DealProductInfo {

    private final String endDate = "20491231235959";

    /**
     * 处理产品信息
     * 
     * @param input
     * @return
     */
    public ArrayList<Map> dealProduct(Map inputMap) {
        Esql dao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/prd/LanProductInfoQuery.esql");
        ArrayList<Map> result = dao.id("qryProcuctAttr").params(inputMap).execute();
        String realStartDate = (String) inputMap.get("startDate");
        String startDate = null == realStartDate || "".equals(realStartDate) ? GetDateUtils.getDate() : realStartDate;
        ArrayList<Map> serviceItem = new ArrayList<Map>();
        ArrayList<Map> discntItem = new ArrayList<Map>();
        LanUtils lan = new LanUtils();

        // STEP 1:将业务和资费分成两块，因节点不同，对其分开处理
        for (Map res : result) {
            if ("B".equals(res.get("elementType"))) {
                serviceItem.add(res);
            }
            else if ("C".equals(res.get("elementType")))
            {
                discntItem.add(res);
            }
        }
        ArrayList<Map> serviceList = new ArrayList<Map>();

        // STEP 2:处理业务信息，将service标识相同的业务属性收集在一起
        for (Map serv : serviceItem) {
            if (0 == serviceList.size()) {
                Map service = new HashMap();
                service.put("serviceId", serv.get("elementId"));
                service.put("productMode", serv.get("productMode"));
                service.put("mktCampId", serv.get("mktCampId"));
                service.put("productId", serv.get("productId"));
                service.put("packageId", serv.get("packageId"));
                service.put("startEnable", "0");
                service.put("svcType", "2");
                service.put("startDate", startDate);
                service.put("endDate", endDate);
                if (null != serv.get("attrCode")) {
                    Map tempMap = lan.createAttrInfo(serv.get("attrCode").toString(), serv.get("attrValue").toString());
                    ArrayList<Map> tempList = new ArrayList<Map>();
                    tempList.add(tempMap);
                    service.put("serviceItem", tempList);
                }
                serviceList.add(service);
            }
            else {
                boolean isExist = false;
                for (Map servList : serviceList) {
                    if (servList.get("serviceId").equals(serv.get("elementId"))
                            && servList.get("productId").equals(serv.get("productId"))
                            && servList.get("packageId").equals(serv.get("packageId"))) {
                        isExist = true;
                        ArrayList<Map> tempList = new ArrayList<Map>();
                        if (null != servList.get("serviceItem")) {
                            tempList = (ArrayList<Map>) servList.get("serviceItem");
                        }
                        if (null != serv.get("attrCode")) {
                            Map tempMap = lan.createAttrInfo(serv.get("attrCode").toString(), serv.get("attrValue")
                                    .toString());
                            tempList.add(tempMap);
                        }
                        servList.put("serviceItem", tempList);
                    }
                }
                if (isExist == false) {
                    Map service = new HashMap();
                    service.put("serviceId", serv.get("elementId"));
                    service.put("productId", serv.get("productId"));
                    service.put("productMode", serv.get("productMode"));
                    service.put("mktCampId", serv.get("mktCampId"));
                    service.put("packageId", serv.get("packageId"));
                    service.put("svcType", "2");
                    service.put("startEnable", "0");
                    service.put("startDate", startDate);
                    service.put("endDate", endDate);
                    if (null != serv.get("attrCode")) {
                        ArrayList<Map> tempList = new ArrayList<Map>();
                        Map tempMap = lan.createAttrInfo(serv.get("attrCode").toString(), serv.get("attrValue")
                                .toString());
                        tempList.add(tempMap);
                        service.put("serviceItem", tempList);
                    }
                    serviceList.add(service);
                }
            }
        }

        // STEP 3:处理业务信息，将discnt标识相同的业务属性收集在一起
        ArrayList<Map> discntList = new ArrayList<Map>();
        for (Map disc : discntItem) {
            if (0 == discntList.size()) {
                Map discnt = new HashMap();
                discnt.put("discntCode", disc.get("elementId"));
                discnt.put("productId", disc.get("productId"));
                discnt.put("productMode", disc.get("productMode"));
                discnt.put("mktCampId", disc.get("mktCampId"));
                discnt.put("startEnable", "0");
                discnt.put("packageId", disc.get("packageId"));
                discnt.put("startDate", startDate);
                discnt.put("endDate", endDate);
                if (null != disc.get("attrCode")) {
                    Map tempMap = lan.createAttrInfo(disc.get("attrCode").toString(), disc.get("attrValue").toString());
                    ArrayList<Map> tempList = new ArrayList<Map>();
                    tempList.add(tempMap);
                    discnt.put("custDiscntItem", tempList);
                }
                discntList.add(discnt);
            }
            else {
                boolean isExist = false;
                for (Map discList : discntList) {
                    if (discList.get("discntCode").equals(disc.get("elementId"))
                            && discList.get("productId").equals(disc.get("productId"))
                            && discList.get("packageId").equals(disc.get("packageId"))) {
                        isExist = true;
                        ArrayList<Map> tempList = new ArrayList<Map>();
                        if (null != discList.get("custDiscntItem")) {
                            tempList = (ArrayList<Map>) discList.get("custDiscntItem");
                        }
                        if (null != disc.get("attrCode")) {
                            Map tempMap = lan.createAttrInfo(disc.get("attrCode").toString(), disc.get("attrValue")
                                    .toString());
                            tempList.add(tempMap);
                        }
                        discList.put("custDiscntItem", tempList);
                    }
                }
                if (isExist == false) {
                    Map discnt = new HashMap();
                    discnt.put("discntCode", disc.get("elementId"));
                    discnt.put("productId", disc.get("productId"));
                    discnt.put("productMode", disc.get("productMode"));
                    discnt.put("mktCampId", disc.get("mktCampId"));
                    discnt.put("packageId", disc.get("packageId"));
                    discnt.put("startEnable", "0");
                    discnt.put("startDate", startDate);
                    discnt.put("endDate", endDate);
                    if (null != disc.get("attrCode")) {
                        ArrayList<Map> tempList = new ArrayList<Map>();
                        Map tempMap = lan.createAttrInfo(disc.get("attrCode").toString(), disc.get("attrValue")
                                .toString());
                        tempList.add(tempMap);
                        discnt.put("custDiscntItem", tempList);
                    }
                    discntList.add(discnt);
                }
            }
        }

        // STEP 4:将服务属性放入package
        ArrayList<Map> packageList = new ArrayList<Map>();
        for (Map serv : serviceList) {
            if (0 == packageList.size()) {
                Map service = new HashMap();
                service.put("productId", serv.get("productId"));
                service.put("packageId", serv.get("packageId"));
                service.put("mktCampId", serv.get("mktCampId"));
                service.put("productMode", serv.get("productMode"));
                service.put("defaultTag", "0");
                service.put("startEnable", "0");
                service.put("startDate", startDate);
                service.put("endDate", endDate);
                Map tempMap = new HashMap();
                tempMap = serv;
                tempMap.remove("packageId");
                tempMap.remove("productId");
                ArrayList<Map> tempList = new ArrayList<Map>();
                tempList.add(tempMap);
                service.put("service", tempList);
                packageList.add(service);
            }
            else {
                boolean isExist = false;
                for (Map servList : packageList) {
                    if (servList.get("productId").equals(serv.get("productId"))
                            && servList.get("packageId").equals(serv.get("packageId"))) {
                        isExist = true;
                        ArrayList<Map> tempList = new ArrayList<Map>();
                        if (null != servList.get("service")) {
                            tempList = (ArrayList<Map>) servList.get("service");
                        }
                        Map tempMap = new HashMap();
                        tempMap = serv;
                        tempMap.remove("packageId");
                        tempMap.remove("productId");
                        tempList.add(tempMap);
                        servList.put("service", tempList);
                    }
                }
                if (isExist == false) {
                    Map service = new HashMap();
                    service.put("productId", serv.get("productId"));
                    service.put("packageId", serv.get("packageId"));
                    service.put("mktCampId", serv.get("mktCampId"));
                    service.put("productMode", serv.get("productMode"));
                    service.put("startEnable", "0");
                    service.put("defaultTag", "0");
                    service.put("startDate", startDate);
                    service.put("endDate", endDate);
                    Map tempMap = new HashMap();
                    tempMap = serv;
                    serv.remove("packageId");
                    serv.remove("productId");
                    ArrayList<Map> tempList = new ArrayList<Map>();
                    tempList.add(tempMap);
                    service.put("service", tempList);
                    packageList.add(service);
                }
            }
        }

        // STEP 5：将资费属性放入产品包
        for (Map serv : discntList) {
            if (0 == packageList.size()) {
                Map service = new HashMap();
                service.put("productId", serv.get("productId"));
                service.put("packageId", serv.get("packageId"));
                service.put("mktCampId", serv.get("mktCampId"));
                service.put("productMode", serv.get("productMode"));
                service.put("defaultTag", "0");
                service.put("startEnable", "0");
                service.put("startDate", startDate);
                service.put("endDate", endDate);
                Map tempMap = new HashMap();
                tempMap = serv;
                tempMap.remove("packageId");
                tempMap.remove("productId");
                ArrayList<Map> tempList = new ArrayList<Map>();
                tempList.add(tempMap);
                service.put("custDiscnt", tempList);
                packageList.add(service);
            }
            else {
                boolean isExist = false;
                for (Map servList : packageList) {
                    if (servList.get("productId").equals(serv.get("productId"))
                            && servList.get("packageId").equals(serv.get("packageId"))) {
                        isExist = true;
                        ArrayList<Map> tempList = new ArrayList<Map>();
                        if (null != servList.get("custDiscnt")) {
                            tempList = (ArrayList<Map>) servList.get("custDiscnt");
                        }
                        Map tempMap = new HashMap();
                        tempMap = serv;
                        tempMap.remove("packageId");
                        tempMap.remove("productId");
                        tempList.add(tempMap);
                        servList.put("custDiscnt", tempList);
                    }
                }
                if (isExist == false) {
                    Map service = new HashMap();
                    service.put("productId", serv.get("productId"));
                    service.put("packageId", serv.get("packageId"));
                    service.put("mktCampId", serv.get("mktCampId"));
                    service.put("productMode", serv.get("productMode"));
                    service.put("startEnable", "0");
                    service.put("defaultTag", "0");
                    service.put("startDate", startDate);
                    service.put("endDate", endDate);
                    Map tempMap = new HashMap();
                    tempMap = serv;
                    serv.remove("packageId");
                    serv.remove("productId");
                    ArrayList<Map> tempList = new ArrayList<Map>();
                    tempList.add(tempMap);
                    service.put("custDiscnt", tempList);
                    packageList.add(service);
                }
            }
        }

        // STEP 6：将产品包信息放入产品列表
        boolean noSerDis = false;
        if (0 == packageList.size()) {
            packageList = result;
            for (Map pack : packageList) {
                if (null == pack.get("packageId")) {
                    continue;
                }
                pack.put("startEnable", "0");
                pack.put("defaultTag", "1");
                pack.put("startDate", startDate);
                pack.put("endDate", endDate);
            }
            noSerDis = true;
        }
        ArrayList<Map> productList = new ArrayList<Map>();
        for (Map pack : packageList) {
            if (0 == productList.size()) {
                Map service = new HashMap();
                service.put("productId", pack.get("productId"));
                service.put("startEnable", "0");
                service.put("defaultTag", "0");
                service.put("mktCampId", pack.get("mktCampId"));
                service.put("productMode", pack.get("productMode"));
                service.put("startDate", startDate);
                service.put("endDate", endDate);
                Map tempMap = new HashMap();
                tempMap = pack;
                tempMap.remove("productId");
                ArrayList<Map> tempList = new ArrayList<Map>();
                tempList.add(tempMap);
                service.put("productPackage", tempList);
                productList.add(service);
            }
            else {
                boolean isExist = false;
                for (Map servList : productList) {
                    if (servList.get("productId").equals(pack.get("productId"))) {
                        isExist = true;
                        ArrayList<Map> tempList = new ArrayList<Map>();
                        if (null != servList.get("productPackage")) {
                            tempList = (ArrayList<Map>) servList.get("productPackage");
                        }
                        Map tempMap = new HashMap();
                        tempMap = pack;
                        tempMap.remove("productId");
                        tempList.add(tempMap);
                        servList.put("productPackage", tempList);
                    }
                }
                if (isExist == false) {
                    Map service = new HashMap();
                    service.put("productId", pack.get("productId"));
                    service.put("packageId", pack.get("packageId"));
                    service.put("mktCampId", pack.get("mktCampId"));
                    service.put("productMode", pack.get("productMode"));
                    service.put("startEnable", "0");
                    service.put("defaultTag", "0");
                    service.put("startDate", startDate);
                    service.put("endDate", endDate);
                    Map tempMap = new HashMap();
                    tempMap = pack;
                    pack.remove("productId");
                    ArrayList<Map> tempList = new ArrayList<Map>();
                    tempList.add(tempMap);
                    service.put("productPackage", tempList);
                    productList.add(service);
                }
            }
        }
        if (noSerDis == true) {
            for (Map prod : productList) {
                ArrayList<Map> pack = (ArrayList<Map>) prod.get("productPackage");
                ArrayList<Map> productPackage = new ArrayList<Map>();
                for (Map p : pack) {
                    if (null != p.get("packageId") && !"".equals(p.get("packageId"))) {
                        productPackage.add(p);
                    }
                }
                prod.remove("productPackage");
                if (0 < productPackage.size()) {
                    prod.put("productPackage", productPackage);
                }
                else {
                    prod.put("defaultTag", "1");
                }
            }
        }
        return productList;
    }
}
