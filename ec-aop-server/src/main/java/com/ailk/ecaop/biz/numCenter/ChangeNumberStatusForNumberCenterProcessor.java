package com.ailk.ecaop.biz.numCenter;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EcRocTag("changeNumberStatusForNumberCenter")
public class ChangeNumberStatusForNumberCenterProcessor extends BaseAopProcessor implements ParamsAppliable {

    NumCenterUtils nc = new NumCenterUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trades.core.simpleCheckUserInfo.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        for (Map res : resourcesInfo) {
            // 号码状态标识：0 不预占；1 预占；2 预订（未付费）；3 预订（付费）；4 释放资源
            String occupiedFlag = (String) res.get("occupiedFlag");
            // 关键字修改0:不修改；1：修改snChangeTag
            String keyChangeTag = (String) res.get("keyChangeTag");
            // snChangeTag 0：号码不变更；1：号码变更
            String snChangeTag = (String) res.get("snChangeTag");
            msg.put("serialNumber", res.get("resourcesCode"));
            msg.put("proKey", res.get("proKey"));
            msg.put("sysCode", nc.changeSysCode(exchange));

            // 查询接口
            if ("0".equals(occupiedFlag)) {
                callQryNumInfo(exchange, msg);
                dealQryNumInfoReturn(exchange);

                Map updateCertInfo = (Map) res.get("updateCertInfo");
                if (null != updateCertInfo && updateCertInfo.size() > 0) {
                    Exchange preTemp = ExchangeUtils.ofCopy(exchange, msg);
                    msg.put("oldCertType",CertTypeChangeUtils.certTypeMall2Fbs((String) updateCertInfo.get("oldCertType")));
                    msg.put("oldCertNum", updateCertInfo.get("oldCertNum"));
                    msg.put("newCertType",
                            CertTypeChangeUtils.certTypeMall2Fbs((String) updateCertInfo.get("newCertType")));
                    msg.put("newCertNum", updateCertInfo.get("newCertNum"));
                    callUpdateCert(preTemp, msg);
                }

                // 释放接口
            }
            else if ("4".equals(occupiedFlag)) {
                callRelSelectionNum(exchange, msg, (String) res.get("resourcesCode"), (String) res.get("proKey"));
                dealRelSelectionNumReturn(exchange);
            }
            // 选占业务不支持关键字变更
            else if ("1|2|3".contains(occupiedFlag) && "1".equals(keyChangeTag)) {
                // 变更选占关键字
                throw new EcAopServerBizException("9999", "号卡中心不支持关键字变更场景。");
                // 选占业务1,2,3
            }
            else if ("1|2|3".contains(occupiedFlag) && "0".equals(keyChangeTag)) {
                // 选占逻辑 snChangeTag 0：号码不变更；1：号码变更
                if ("0".equals(snChangeTag)) {
                    /**
                     * 选占-号码不变更，关键字不修改选占接口
                     */
                    // 1 预占；选占时还需去BSS及CB查询是否已被使用
                    if ("1".equals(occupiedFlag)) {
                        callSelectionNum(exchange, msg, res);
                        dealSelectionNumReturn(exchange);

                        String certNum = (String) res.get("certNum");
                        String certType = (String) res.get("certType");
                        // 更新证件信息
                        if (StringUtils.isNotEmpty(certNum) && StringUtils.isNotEmpty(certType)) {
                            Exchange temp = ExchangeUtils.ofCopy(exchange, msg);
                            msg.put("oldCertType", "");
                            msg.put("oldCertNum", "");
                            msg.put("newCertType", CertTypeChangeUtils.certTypeMall2Fbs(certType));
                            msg.put("newCertNum", certNum);
                            callUpdateCert(temp, msg);
                        }

                        // 2 预订（未付费）；3 预订（付费）先查一遍号码状态，如果空闲，则先选占后延时；如已选占，则只延时
                    }
                    else {
                        String preOrderTag = (String) res.get("preOrderTag");//preOrderTag 0不校验;1身份信息校验
                        String certNum = (String) res.get("certNum");// 证件号码OccupiedFlag:2、3的时候必填
                        if ("1".equals(preOrderTag) && (certNum == null || "".equals(certNum))) {
                            throw new EcAopServerBizException("9999", "身份信息校验preOrderTag为1时，证件号码必传！");
                        }
                        Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
                        callQryNumInfo(tempExchange, msg);
                        // 调号码状态查询接口，校验是否已被选占
                        boolean isSelected = checkQryNumIsSelected(tempExchange);

                        if (!isSelected) {
                            Exchange temp = ExchangeUtils.ofCopy(exchange, msg);
                            callSelectionNum(temp, msg, res);
                        }
                        callDelayOcpNumber(exchange, msg, res, (String) res.get("occupiedTime"));
                        dealDelayOcpNumberReturn(exchange, msg);

                        String certType = (String) res.get("certType");
                        // 商城延时选占之后更新证件信息
                        if (StringUtils.isNotEmpty(certNum) && StringUtils.isNotEmpty(certType)) {
                            Exchange temp = ExchangeUtils.ofCopy(exchange, msg);
                            msg.put("oldCertType", "");
                            msg.put("oldCertNum", "");
                            msg.put("newCertType", CertTypeChangeUtils.certTypeMall2Fbs(certType));
                            msg.put("newCertNum", certNum);
                            callUpdateCert(temp, msg);
                        }

                    }
                    /**
                     * 选占-号码变更，关键字不修改选占接口
                     */
                }
                else
                {
                    throw new EcAopServerBizException("9999", "暂不支持涉及号卡中心的号码变更，敬请期待!");
                }
            }
            else
            {
                throw new EcAopServerBizException("9999", "未知业务，请确认传参是否正确！");
            }
        }
    }

    /**
     * 调号卡中心号码状态查询接口
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void callQryNumInfo(Exchange exchange, Map msg) throws Exception {
        Map REQ = createREQ(msg);
        REQ.put("SEARCH_TYPE", "2");// 查询类型： 1：条件查询 2、散号查询
        List<Map> SELNUM_LIST = new ArrayList();
        SELNUM_LIST.add(MapUtils.asMap("SERIAL_NUMBER", msg.get("serialNumber")));
        REQ.put("SELNUM_LIST", SELNUM_LIST);
        Map req = createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("QRY_NUM_INFO_REQ", REQ));
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.qryNumInfo");
        dealNumberCenterReturn(exchange, "QRY_NUM_INFO_RSP", "号码状态查询");
    }

    /**
     * 处理号卡中心号码状态查询接口返回信息
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dealQryNumInfoReturn(Exchange exchange) throws Exception {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map UNI_BSS_BODY = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) UNI_BSS_BODY.get("QRY_NUM_INFO_RSP");
        if (resultMap == null || resultMap.size() < 1) {
            throw new EcAopServerBizException("9999", "号卡中心号码状态查询接口返回结果为空");
        }
        Map newOut = new HashMap();
        List<Map> newResourcesRsp = null;
        if ("0000".equals(resultMap.get("RESP_CODE") + "")) {
            List<Map> resourcesRsp = (List<Map>) resultMap.get("RESOURCES_INFO");
            newResourcesRsp = new ArrayList<Map>();
            if (null != resourcesRsp && resourcesRsp.size() > 0) {
                for (Map resourceInfo : resourcesRsp) {
                    Map temp = new HashMap();
                    String rscStateCode = (String) resourceInfo.get("NUM_STATUS");
                    String numPreFee = (String) resourceInfo.get("ADVANCE_PAY");
                    String numLevel = (String) resourceInfo.get("GOOD_LEVEL");
                    temp.put("resourcesType", "02");
                    temp.put("resourcesCode", resourceInfo.get("SERIAL_NUMBER"));
                    temp.put("numId", resourceInfo.get("SERIAL_NUMBER"));
                    /*
                     * 01 空闲 02 预留 03 上架 04 选占
                     * 05 待预配 06 预配 07 预配套包 08 预占
                     * 09 占用未激活 10 占用 11 冷冻 12 携入
                     * 13 携出 14 待审批
                     * 0000 资源可用 0001 资源已被占 0002 无此资源信息 0003 资源不可售
                     * 0004 资源状态是非可用0005 资源归属渠道错误0006 资源空闲，不能释放
                     * 0007 资源已售，不能释放9999 其它失败原因
                     */
                    if ("03".equals(rscStateCode) || "3".equals(rscStateCode)) {
                        temp.put("rscStateCode", "0000");
                        temp.put("rscStateDesc", "资源可用");
                    }
                    else if ("04".equals(rscStateCode) || "4".equals(rscStateCode)) {
                        temp.put("rscStateCode", "0001");
                        temp.put("rscStateDesc", "号卡中心已选占");
                    }
                    else if ("10".equals(rscStateCode)) {
                        temp.put("rscStateCode", "0001");
                        temp.put("rscStateDesc", "号卡中心已实占");
                    }
                    else if ("08".equals(rscStateCode) || "8".equals(rscStateCode)) {
                        temp.put("rscStateCode", "0001");
                        temp.put("rscStateDesc", "号卡中心已预占");
                    }
                    else if ("02".equals(rscStateCode) || "2".equals(rscStateCode)) {
                        temp.put("rscStateCode", "0003");
                        temp.put("rscStateDesc", "资源不可售");
                    }
                    else if ("01,05,06,07,09,11,12,13,14".contains(rscStateCode)) {
                        temp.put("rscStateCode", "0004");
                        temp.put("rscStateDesc", "资源状态是非可用");
                    }
                    else {
                        temp.put("rscStateCode", "9999");
                        temp.put("rscStateDesc", "其他失败原因");
                    }
                    if (StringUtils.isNotEmpty(numPreFee)) {
                        temp.put("numPreFee", numPreFee);
                    }
                    if (StringUtils.isNotEmpty(numLevel)) {
                        temp.put("numLevel", numLevel);
                    }
                    if (StringUtils.isNotEmpty(rscStateCode)) {
                        temp.put("numState", rscStateCode);
                    }
                    newResourcesRsp.add(temp);
                }
            }
            else {
                throw new EcAopServerBizException("9999", "号卡中心未返回号码状态信息");
            }
        }
        else {
            throw new EcAopServerBizException(resultMap.get("RESP_CODE") + "", "号卡中心号码状态查询接口返回:"
                    + resultMap.get("RESP_DESC"));
        }
        newOut.put("resourcesRsp", newResourcesRsp);
        exchange.getOut().setBody(newOut);
    }

	/**
	 * 处理号卡中心选占接口返回信息
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void dealSelectionNumReturn(Exchange exchange) throws Exception {
		Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
		Map UNI_BSS_BODY = (Map) outMap.get("UNI_BSS_BODY");
		Map resultMap = (Map) UNI_BSS_BODY.get("SELECTED_NUM_RSP");
		if (resultMap == null || resultMap.size() < 1) {
			throw new EcAopServerBizException("9999", "号卡中心号码状态查询接口返回结果为空");
		}
		Map newOut = null;
		List<Map> newResourcesRsp = null;
		if ("0000".equals(resultMap.get("RESP_CODE") + "")) {
			newOut = new HashMap();
			newResourcesRsp = new ArrayList<Map>();
			Map temp = new HashMap();
			temp.put("resourcesType", "02");
			temp.put("resourcesCode", resultMap.get("SERIAL_NUMBER"));
			temp.put("rscStateCode", "0000");
            temp.put("rscStateDesc", "号卡选占成功");
			temp.put("numId", resultMap.get("SERIAL_NUMBER"));
			String numPreFee = (String) resultMap.get("ADVANCE_PAY");// 单位都是分
			if (StringUtils.isNotEmpty(numPreFee)) {
				temp.put("numPreFee", numPreFee);
			}
			String numLevel = (String) resultMap.get("GOOD_LEVEL");
			if (StringUtils.isNotEmpty(numLevel)) {
				temp.put("numLevel", numLevel);
			}
			String simId = (String) resultMap.get("ICCID");
			if (StringUtils.isNotEmpty(simId)) {
				temp.put("numLevel", simId);
			}
			newResourcesRsp.add(temp);
			newOut.put("resourcesRsp", newResourcesRsp);
		} else {
			throw new EcAopServerBizException(resultMap.get("RESP_CODE") + "",
					"号卡中心选占接口返回:" + resultMap.get("RESP_DESC"));
		}
		exchange.getOut().setBody(newOut);
	}

    /**
     * 判断查询号码状态结果，返回是否已被选占;
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes" })
    public boolean checkQryNumIsSelected(Exchange exchange) throws Exception {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map UNI_BSS_BODY = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) UNI_BSS_BODY.get("QRY_NUM_INFO_RSP");

        if (resultMap == null || resultMap.size() < 1) {
            throw new EcAopServerBizException("9999", "号卡中心号码状态查询接口返回结果为空");
        }
        List<Map> resInfo = ((List<Map>) resultMap.get("RESOURCES_INFO"));
        if ("0000".equals(resultMap.get("RESP_CODE") + "")) {
            for (Map res : resInfo) {
                String rscStateCode = (String) (res.get("NUM_STATUS"));
                System.out.println("124343" + rscStateCode);
                if ("4".equals(rscStateCode) || "04".equals(rscStateCode)) {
                    return true;
                }
            }
        }
        else {
            throw new EcAopServerBizException(resultMap.get("RESP_CODE") + "", "号卡中心号码状态查询接口返回:"
                    + resultMap.get("RESP_DESC"));
        }
        return false;
    }

    /**
     * 处理号卡中心选占号码延时接口返回信息
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dealDelayOcpNumberReturn(Exchange exchange, Map msg) throws Exception {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map UNI_BSS_BODY = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) UNI_BSS_BODY.get("DELAY_OCP_NUMBER_RSP");
        if (resultMap == null || resultMap.size() < 1) {
            throw new EcAopServerBizException("9999", "号卡中心选占号码延时接口返回结果为空");
        }
        Map newOut = null;
        List<Map> newResourcesRsp = null;
        if ("0000".equals(resultMap.get("RESP_CODE") + "")) {
            newOut = new HashMap();
            newResourcesRsp = new ArrayList<Map>();
            Map temp = new HashMap();
            temp.put("resourcesType", "02");
            temp.put("resourcesCode", msg.get("serialNumber"));
            temp.put("rscStateCode", "0000");
            temp.put("rscStateDesc", "号卡选占成功");
            temp.put("numId", msg.get("serialNumber"));
            newResourcesRsp.add(temp);
            newOut.put("resourcesRsp", newResourcesRsp);
        }
        else {
            throw new EcAopServerBizException(resultMap.get("RESP_CODE") + "", "号卡中心选占号码延时接口返回:"
                    + resultMap.get("RESP_DESC"));
        }
        exchange.getOut().setBody(newOut);
    }

    /**
     * 处理号卡中心选占号码手工释放接口返回信息
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dealRelSelectionNumReturn(Exchange exchange) throws Exception {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map UNI_BSS_BODY = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) UNI_BSS_BODY.get("REL_SELECTION_NUM_RSP");
        if (resultMap == null || resultMap.size() < 1) {
            throw new EcAopServerBizException("9999", "号卡中心选占号码手工释放接口返回结果为空");
        }
        Map newOut = null;
        List<Map> newResourcesRsp = null;
        if ("0000".equals(resultMap.get("RESP_CODE") + "")) {
            if (resultMap.get("SELNUM_LIST") != null) {
                Map numList = ((List<Map>) resultMap.get("SELNUM_LIST")).get(0);
                if ("0000".equals(numList.get("RESP_CODE") + "")) {
                    newOut = new HashMap();
                    newResourcesRsp = new ArrayList<Map>();
                    Map temp = new HashMap();
                    temp.put("resourcesType", "02");
                    temp.put("resourcesCode", numList.get("SERIAL_NUMBER"));
                    temp.put("rscStateCode", "0000");
                    temp.put("rscStateDesc", "号码已释放");
                    temp.put("numId", numList.get("SERIAL_NUMBER"));
                    newResourcesRsp.add(temp);
                    newOut.put("resourcesRsp", newResourcesRsp);
                }
                else {
                    throw new EcAopServerBizException("9999", "号卡中心选占号码手工释放接口返回:" + numList.get("RESP_DESC"));
                }
            }
        }
        else {
            throw new EcAopServerBizException(resultMap.get("RESP_CODE") + "", "号卡中心选占号码手工释放接口返回:"
                    + resultMap.get("RESP_DESC"));
        }
        exchange.getOut().setBody(newOut);
    }

    /**
     * 准备REQ中常用参数
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map createREQ(Map msg) {
        Map REQ = new HashMap();
        REQ.put("STAFF_ID", msg.get("operatorId"));
        REQ.put("PROVINCE_CODE", msg.get("province"));
        REQ.put("CITY_CODE", msg.get("city"));
        REQ.put("CHANNEL_ID", msg.get("channelId"));
        REQ.put("SYS_CODE", msg.get("sysCode"));
        String district = (String) msg.get("district");
        String channelType = (String) msg.get("channelType");
        if (null != district && !"".equals(district))
            REQ.put("DISTRICT_CODE", msg.get("district"));
        if (null != channelType && !"".equals(channelType))
            REQ.put("CHANNEL_TYPE", msg.get("channelType"));
        return REQ;
    }

    /**
     * 准备UNI_BSS_HEAD及UNI_BSS_ATTACHED
     * 
     * @throws Exception
     * @return Map 含UNI_BSS_HEAD
     */
    @SuppressWarnings("rawtypes")
    public Map createHeadAndAttached(String appKey) throws Exception {
        return NumFaceHeadHelper.creatHead(appKey);
    }

    /**
     * 初步处理号卡中心返回报文
     * 
     * @param exchange
     * @param rspKey XXX_XXX_RSP
     * @param kind 接口类型（中文名）
     */
    @SuppressWarnings("rawtypes")
    public void dealNumberCenterReturn(Exchange exchange, String rspKey, String kind) {
        String rsp = exchange.getOut().getBody().toString();
        System.out.println("号卡中心返回toString：" + rsp);
        Map out = (Map) JSON.parse(rsp);
        Map UNI_BSS_HEAD = (Map) out.get("UNI_BSS_HEAD");
        if (null != UNI_BSS_HEAD) {
            String code = (String) UNI_BSS_HEAD.get("RESP_CODE");
            if (!"0000".equals(code) && !"00000".equals(code)) {
                throw new EcAopServerBizException("9999", UNI_BSS_HEAD.get("RESP_DESC") + "");
            }
        }
        else {
            throw new EcAopServerBizException("9999", "调号卡中心" + kind + "接口返回异常!");
        }
        Map UNI_BSS_BODY = (Map) out.get("UNI_BSS_BODY");
        if (null != UNI_BSS_BODY) {
            Map rspMap = (Map) UNI_BSS_BODY.get(rspKey);
            if (null != rspMap) {
                String code = (String) rspMap.get("RESP_CODE");
                if (!"0000".equals(code)) {
                    throw new EcAopServerBizException("9999", "号卡中心" + kind + "接口返回：" + rspMap.get("RESP_DESC"));
                }
            }
        }
        else {
            throw new EcAopServerBizException("9999", "调号卡中心" + kind + "接口返回异常!");
        }
    }

    /**
     * 插入调号卡中心状态变更记录
     * 出错不抛异常
     * 
     * @param msg 含province,sysCode,operatorId,serialNumber信息的Map
     * @param req 含UNI_BSS_HEAD信息的Map
     * @param numState 号码状态 与号卡中心一致（待确认）
     * @param remark 备注说明
     */
    @SuppressWarnings({ "rawtypes", "unchecked", "static-access" })
    public void insertCallNumberCenterRecord(Map msg, Map req, String numState, String remark) {
        Map map = new HashMap();
        map.put("province", msg.get("province"));
        map.put("sysCode", msg.get("sysCode"));
        map.put("operatorId", msg.get("operatorId"));
        map.put("serialNumber", msg.get("serialNumber"));
        map.put("ordersId", ((Map) req.get("UNI_BSS_HEAD")).get("TRANS_ID"));
        map.put("numState", numState);
        map.put("remark", remark);
        try {
            nc.InsertTradeInfo(map);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
    /**
     * 调号卡中心选占接口,选占时还需去BSS及CB查询是否已被使用 keyChangeTag不修改时走这里
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void callSelectionNum(Exchange exchange, Map msg, Map res) throws Exception {
        //校验BSS及CB号码状态
        //Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
        //checkNumberFromBssAndCB(tempExchange, msg);
        Map REQ = createREQ(msg);
        REQ.put("SERIAL_NUMBER", msg.get("serialNumber"));
        REQ.put("PRO_KEY", msg.get("proKey"));
        String certType = (String) res.get("certType");// 证件类型
        String certNum = (String) res.get("certNum");// 证件号码OccupiedFlag:2、3的时候必填
        String custName = (String) res.get("custName");// 客户名称
        String contactNum = (String) res.get("contactNum");// 联系电话[BSS一定要]
        if (StringUtils.isNotEmpty(certType))
            REQ.put("CERT_TYPE_CODE", CertTypeChangeUtils.certTypeMall2Fbs(certType));
        if (StringUtils.isNotEmpty(certNum))
            REQ.put("CERT_CODE", certNum);
        if (StringUtils.isNotEmpty(custName))
            REQ.put("CUST_NAME", custName);
        if (StringUtils.isNotEmpty(contactNum))
            REQ.put("CONTACT_PHONE", contactNum);
        String occupiedTime = (String) res.get("occupiedTime");
        // delayOccupiedFlag 0：不延长预定时间；1：延长预定时间；目前仅用于北六延长号码未付费预定时间
        // occupiedTime yyyymmddhh24miss。OccupiedFlag:1，2，3的时候必填
        // 仅选占时需对时间作处理
        if (StringUtils.isNotEmpty(occupiedTime) && "1".equals(res.get("occupiedFlag"))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Date inDate = null;
            try {
                inDate = sdf.parse(occupiedTime);
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", "选占时间格式不符合要求!" + e.getMessage());
            }
            long sum = inDate.getTime() - (new Date()).getTime();
            int minute = (int) (sum / (1000 * 60));
            if (minute < 1) {
                throw new EcAopServerBizException("9999", "选占时间时必须在当前时间1分钟之后!");
            }
            REQ.put("SELECTION_TIME", minute + "");
        }
        else {
            REQ.put("SELECTION_TIME", "30");// 默认选占30分
        }
        Map req = createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("SELECTED_NUM_REQ", REQ));
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.selectedNum");
        dealNumberCenterReturn(exchange, "SELECTED_NUM_RSP", "选占");
        insertCallNumberCenterRecord(msg, req, "选占", "调号卡中心选占接口");
    }

    /**
     * 调号卡中心选占号码延时接口
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void callDelayOcpNumber(Exchange exchange, Map msg, Map res, String occupiedTime) throws Exception {
        Map REQ = createREQ(msg);
        REQ.put("SERIAL_NUMBER", msg.get("serialNumber"));
        REQ.put("PRO_KEY", msg.get("proKey"));
        if (null != res && res.size() > 0) {
            String certType = (String) res.get("certType");// 证件类型
            String certNum = (String) res.get("certNum");// 证件号码OccupiedFlag:2、3的时候必填
            String custName = (String) res.get("custName");// 客户名称
            String contactNum = (String) res.get("contactNum");// 联系电话[BSS一定要]
            if (StringUtils.isNotEmpty(certType))
                REQ.put("CERT_TYPE_CODE", CertTypeChangeUtils.certTypeMall2Fbs(certType));
            if (StringUtils.isNotEmpty(certNum))
                REQ.put("CERT_CODE", certNum);
            if (StringUtils.isNotEmpty(custName))
                REQ.put("CUST_NAME", custName);
            if (StringUtils.isNotEmpty(contactNum))
                REQ.put("CONTACT_PHONE", contactNum);
        }
        // delayOccupiedFlag 0：不延长预定时间；1：延长预定时间；目前仅用于北六延长号码未付费预定时间
        // occupiedTime yyyymmddhh24miss。OccupiedFlag:1，2，3的时候必填
        if (StringUtils.isEmpty(occupiedTime)) {
            throw new EcAopServerBizException("9999", "预定时间occupiedTime不能为空!");
        }
        REQ.put("DELAY_NUM", occupiedTime);
        Map req = createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("DELAY_OCP_NUMBER_REQ", REQ));
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.delayOcpNumber");
        dealNumberCenterReturn(exchange, "DELAY_OCP_NUMBER_RSP", "选占号码延时");
        insertCallNumberCenterRecord(msg, req, "选占延时", "调号卡中心选占号码延时接口");
    }

    /**
     * 调号卡中心选占号码手工释放接口
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void callRelSelectionNum(Exchange exchange, Map msg, String serialNumber, String proKey) throws Exception {
        Map REQ = createREQ(msg);
        List list = new ArrayList<Map>();
        REQ.put("SYS_CODE", msg.get("sysCode"));
        Map selnum = MapUtils.asMap("SERIAL_NUMBER", serialNumber, "PRO_KEY", proKey);
        if ("masb".equals(exchange.getAppCode())) {
            List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
            for (Map res : resourcesInfo) {
                String certNum = (String) res.get("certNum");
                String certType = (String) res.get("certType");
                if (StringUtils.isNotEmpty(certType)) {
                    selnum.put("CERT_TYPE_CODE", CertTypeChangeUtils.certTypeMall2Fbs(certType));
                }
                if (StringUtils.isNotEmpty(certNum)) {
                    selnum.put("CERT_CODE", certNum);
                }
            }
        }
        list.add(selnum);
        REQ.put("SELNUM_LIST", list);
        Map req = createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("REL_SELECTION_NUM_REQ", REQ));
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.relSelectionNum");
        dealNumberCenterReturn(exchange, "REL_SELECTION_NUM_RSP", "选占号码手工释放");
        insertCallNumberCenterRecord(msg, req, "释放", "调号卡中心选占号码手工释放接口");
    }

    /**
     * 调号卡中心选占更改证件号码接口
     * 
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void callUpdateCert(Exchange exchange, Map msg) throws Exception {
        Map REQ = createREQ(msg);
        REQ.put("SERIAL_NUMBER", msg.get("serialNumber"));
        REQ.put("PRO_KEY", msg.get("proKey"));
        //REQ.put("DEPART_ID", msg.get("channelId"));//应号卡中心张明瑶同事要求此字段不传
        REQ.put("OLD_CERT_TYPE_CODE", msg.get("oldCertType"));
        REQ.put("OLD_CERT_CODE", msg.get("oldCertNum"));
        REQ.put("CERT_TYPE_CODE", msg.get("newCertType"));
        REQ.put("CERT_CODE", msg.get("newCertNum"));
        Map req = createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("UPDATE_CERT_REQ", REQ));
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.updateCert");
        String rsp = exchange.getOut().getBody().toString();
        Map out = (Map) JSON.parse(rsp);
        Map UNI_BSS_HEAD = (Map) out.get("UNI_BSS_HEAD");
        if (null != UNI_BSS_HEAD) {
            String code = (String) UNI_BSS_HEAD.get("RESP_CODE");
            if (!"0000".equals(code) && !"00000".equals(code)) {
                throw new EcAopServerBizException("8888", UNI_BSS_HEAD.get("RESP_DESC") + "");
            }
        }
        else {
            throw new EcAopServerBizException("8888", "调号卡中心选占更改证件号码接口返回异常!");
        }
        Map UNI_BSS_BODY = (Map) out.get("UNI_BSS_BODY");
        if (null != UNI_BSS_BODY) {
            Map rspMap = (Map) UNI_BSS_BODY.get("UPDATE_CERT_RSP");
            String code = (String) rspMap.get("RESP_CODE");
            if (!"0000".equals(code)) {
                throw new EcAopServerBizException("8888", "号卡中心选占更改证件号码接口返回：" + rspMap.get("RESP_DESC"));
            }
        }
        else {
            throw new EcAopServerBizException("8888", "调号卡中心选占更改证件号码接口返回异常!");
        }
        insertCallNumberCenterRecord(msg, req, "更改证件", "调号卡中心选占更改证件号码接口");
    }
}
