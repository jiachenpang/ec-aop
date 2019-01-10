package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DealResOccupyAndRlsUtils {

    public Map copyMap(Map inMap) {
        Map temp = new HashMap();
        Set key = inMap.keySet();
        Iterator iterator = key.iterator();
        while (iterator.hasNext()) {
            Object k = iterator.next();
            temp.put(k, inMap.get(k));
        }
        return temp;
    }

    /**
     * 准备号码数据
     * 
     * @param inMap
     * @param occupy
     */
    public void preNumberInfo(Map inMap, boolean occupy) {
        ArrayList<Map> resourcesInfo = new ArrayList<Map>();
        List<Map> numId = (List<Map>) inMap.get("numId");
        List<Map> userInfo = (List<Map>) inMap.get("userInfo");
        for (int i = 0; i < numId.size(); i++) {
            Map res = new HashMap();
            res.put("proKey", numId.get(i).get("proKey"));
            res.put("resourcesCode", numId.get(i).get("serialNumber"));
            res.put("packageTag", userInfo.get(i).get("packageTag"));
            res.put("occupiedTime", GetDateUtils.getDate(30));
            res.put("occupiedFlag", true == occupy ? "1" : "4");
            res.put("resourcesType", decodeNumberType(userInfo.get(i).get("serType").toString()));
            resourcesInfo.add(res);
        }
        if (0 == resourcesInfo.size()) {
            return;
        }
        inMap.put("resourcesInfo", resourcesInfo);
    }

    /**
     * 准备终端预占基本参数
     * 
     * @param map
     * @return
     * @throws Exception
     */
    public void preTerminalInfo(Map map, boolean occupy) throws Exception {
        String methodCode = map.get("methodCode").toString();
        if ("opsb".equals(methodCode)) {
            map.put("resourcesInfo", preParams4Opsb(map, occupy));
        }
        else if ("qrtc|qrtac".contains(methodCode)) {
            map.put("resourcesInfo", preParams4Qrtc(map, occupy));
        }
        else {
            map.put("resourcesInfo", preParams4Opnc(map, occupy));
        }
    }

    /**
     * 针对开户处理提交接口，准备资源预占信息
     * 
     * @param inMap
     * @return
     * @throws Exception
     */
    private List<Map> preParams4Opsb(Map inMap, boolean occupy) throws Exception {
        List<Map> paraList = (List<Map>) inMap.get("para");
        List<Map> paraList2 = new ArrayList<Map>();
        Map resourcesMap = new HashMap();
        List<Map> resourcesInfo = new ArrayList<Map>();
        for (Map para : paraList) {
            if ("resourcesCode".equals(para.get("paraId"))) {// RES_CODE
                resourcesMap.put("resourcesCode", para.get("paraValue"));
                resourcesMap.put("resCodeType", "01");
                resourcesMap.put("occupiedFlag", true == occupy ? "1" : "3");
                Object allocationFlag = resourcesMap.get("allocationFlag");
                if (null == allocationFlag || "".equals(allocationFlag)) {
                    resourcesMap.put("allocationFlag", "0");
                }
                resourcesMap.put("occupiedTime", GetDateUtils.getDate(30));
            }
            else if ("resourcesType".equals(para.get("paraId"))) {// RES_TYPE
                resourcesMap.put("resourcesType", decodeTerminalType(para.get("paraValue").toString()));
            }
            else {
                paraList2.add(para);
            }
        }
        if (null == resourcesMap.get("resourcesType") || "".equals(resourcesMap.get("resourcesType"))) {
            resourcesMap.put("resourcesType", "01");
        }
        inMap.put("para", paraList2);
        resourcesInfo.add(resourcesMap);
        return resourcesInfo;
    }

    /**
     * 针对开户处理申请接口准备资源预占信息
     * 
     * @param map
     * @return
     * @throws Exception
     */
    private List<Map> preParams4Opnc(Map map, boolean occupy) throws Exception {
        List<Map> resourcesInfo = new ArrayList<Map>();
        List<Map> userInfo = (List<Map>) map.get("userInfo");
        for (Map userMap : userInfo) {
            List<Map> activityInfo = (List<Map>) userMap.get("activityInfo");
            for (Map activityMap : activityInfo) {
                Object resourcesCode = activityMap.get("resourcesCode");
                if (null == resourcesCode || "".equals(resourcesCode)) {
                    continue;
                }
                String resourcesType = (String) activityMap.get("resourcesType");
                Map resourcesInfoTemp = new HashMap();
                DealResOccupyAndRlsUtils rr = new DealResOccupyAndRlsUtils();
                resourcesInfoTemp.put("resourcesType", rr.decodeTerminalType(resourcesType));
                resourcesInfoTemp.put("resourcesCode", resourcesCode);
                resourcesInfoTemp.put("occupiedFlag", true == occupy ? "1" : "3");
                Object allocationFlag = resourcesInfoTemp.get("allocationFlag");
                if (null == allocationFlag || "".equals(allocationFlag)) {
                    resourcesInfoTemp.put("allocationFlag", "0");
                }
                resourcesInfoTemp.put("occupiedTime", GetDateUtils.getDate(30));
                resourcesInfo.add(resourcesInfoTemp);
            }
        }
        return resourcesInfo;
    }

    /**
     * 针对终端状态变更接口准备资源预占信息
     * 
     * @param map
     * @param occupy
     * @return
     */
    private List<Map> preParams4Qrtc(Map map, boolean occupy) {
        List<Map> resourcesInfo = (List<Map>) map.get("resourcesInfo");
        for (Map resMap : resourcesInfo) {
            // String resourcesType = (String) resMap.get("resourcesType");
            // DealResOccupyAndRlsUtils rr = new DealResOccupyAndRlsUtils();
            // resMap.put("resourcesType", rr.decodeTerminalType(resourcesType));
            Object allocationFlag = resMap.get("allocationFlag");
            if (null == allocationFlag || "".equals(allocationFlag)) {
                resMap.put("allocationFlag", "0");
            }
        }
        return resourcesInfo;
    }

    /**
     * 资源存在返回ture，反之返回false
     * opap为商城和MINI厅开户所用，终端资源上游系统进行自行预占
     * 
     * @param map
     * @return
     */
    public boolean isExistTerminal(Map inMap) {
        String methodCode = inMap.get("methodCode").toString();
        if ("opap".equals(methodCode)) {
            return false;
        }
        if ("opsb".equals(methodCode)) {
            return isExistResource4OpenSub(inMap);
        }
        if ("qrtc|qrtac".contains(methodCode)) {
            return true;
        }
        return isExistReource4OpenApply(inMap);
    }

    /**
     * 校验开户处理申请接口是否传递资源串码
     * 
     * @param inMap
     * @return
     */
    private boolean isExistReource4OpenApply(Map inMap) {
        boolean flag = false;
        if (null == inMap.get("userInfo")) {
            return false;
        }
        List<Map> userInfo = (List<Map>) inMap.get("userInfo");
        for (Map userInfoMap : userInfo) {
            List<Map> activityInfo = (List<Map>) userInfoMap.get("activityInfo");
            if (null == activityInfo || 0 == activityInfo.size()) {
                return false;
            }
            for (Map activityMap : activityInfo) {
                if (null != activityMap.get("resourcesCode") && !"".equals(activityMap.get("resourcesCode"))
                        && null != activityMap.get("resourcesType") && !"".equals(activityMap.get("resourcesType"))) {
                    flag = true;
                    return flag;
                }
            }
        }
        return flag;
    }

    /**
     * 校验开户处理提交接口是否传递了资源串码
     * 
     * @param inMap
     * @return
     */
    private boolean isExistResource4OpenSub(Map inMap) {
        if (null == inMap.get("para")) {
            return false;
        }
        List<Map> paraList = (List<Map>) inMap.get("para");
        if (null == paraList || 0 == paraList.size()) {
            return false;
        }
        for (Map para : paraList) {
            if ("resourcesCode".equals(para.get("paraId")) && !"".equals(para.get("paraValue"))) {// RES_CODE
                return true;
            }
        }
        return false;
    }

    /**
     * 资源类型转码
     * 
     * @param value
     * @return
     */
    public String decodeTerminalType(String value) {
        if ("03".equals(value)) {
            return "01";
        }
        if ("04".equals(value)) {
            return "02";
        }
        return "03";
    }

    /**
     * 付费类型转码
     * 
     * @param type
     * @return
     */
    public String decodeNumberType(String type) {
        if ("1".equals(type)) {
            return "01";
        }
        return "02";
    }
}
