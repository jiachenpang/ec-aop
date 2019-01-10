package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 号码查询简单版号卡中心分支
 * 
 * @author
 */
@EcRocTag("QueryNumberCenter")
public class QueryNumberCenterProcessors extends BaseAopProcessor {

    private String appCode;

    @Override
    public void process(Exchange exchange) throws Exception {
        // 判断是否是全量割接
        String ifAllNumCenter = (String) exchange.getProperty("ifAllNumCenter");
        Map body = (Map) exchange.getIn().getBody();
        Object msgObjectJson = body.get("msg");
        Object msgObject = body.get("msg");
        Map msg = null;
        Map msg4Json = null;
        if (msgObject instanceof String) {
            msg = JSONObject.parseObject(msgObject.toString());
            msg4Json = JSONObject.parseObject(msgObjectJson.toString());
        }
        else {
            msg = (Map) msgObject;
            msg4Json = (Map) msgObject;
        }
        appCode = exchange.getAppCode();
        Map UNI_BSS_BODY = new HashMap();
        Map QRY_UNSOLD_NUM_REQ = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        QRY_UNSOLD_NUM_REQ.put("STAFF_ID", msg.get("operatorId"));
        QRY_UNSOLD_NUM_REQ.put("DISTRICT_CODE", msg.get("district"));
        QRY_UNSOLD_NUM_REQ.put("PROVINCE_CODE", msg.get("province"));
        QRY_UNSOLD_NUM_REQ.put("CITY_CODE", msg.get("city"));
        String BUSI_TYPE = (msg.get("busType")) == null ? "01" : "0" + msg.get("busType");
        QRY_UNSOLD_NUM_REQ.put("BUSI_TYPE", BUSI_TYPE);
        QRY_UNSOLD_NUM_REQ.put("SYS_CODE", sysCode);// 操作系统编码
        QRY_UNSOLD_NUM_REQ.put("CHANNEL_ID", msg.get("channelId"));
        List<Map> queryParasList = ((List<Map>) msg.get("queryParas"));
        if (null != queryParasList && queryParasList.size() > 0) {
            for (Map para : queryParasList) {
                String queryType = (String) para.get("queryType");
                String queryPara = String.valueOf(para.get("queryPara"));

                // 随机选号-01： 号卡接口可空字段均为空即可。
                // 02：号段选号
                if ("02".equals(queryType)) {
                    QRY_UNSOLD_NUM_REQ.put("NUM_HEAD", queryPara);
                }
                // 号码关键字选号-03：
                if ("03".equals(queryType)) {
                    QRY_UNSOLD_NUM_REQ.put("TAIL_TYPE", "01");//
                    QRY_UNSOLD_NUM_REQ.put("SERIAL_NUMBER", queryPara);
                }
                // 04：靓号等级
                if ("04".equals(queryType)) {

                    QRY_UNSOLD_NUM_REQ.put("GOOD_LEVEL", dealNumBack(queryPara));// 怎么判断关键字的位置

                }
                // 预付费产品编码选号-05：

                // 查询选号范围选号-06：
                if ("06".equals(queryType)) {
                    // 按选号返回选号，选号参数为0时调用号卡中心工号与库池对应关系查询接口(当前只处理为0的情况)
                    if ("0".equals(queryPara)) {
                        qryPoolStockInfo(exchange, QRY_UNSOLD_NUM_REQ);
                    }
                }
                // 尾号选号：
                if ("07".equals(queryType)) {
                    QRY_UNSOLD_NUM_REQ.put("TAIL_TYPE", "02");// 尾号匹配
                    QRY_UNSOLD_NUM_REQ.put("SERIAL_NUMBER", queryPara);
                }
                if ("08".equals(queryType)) {
                    QRY_UNSOLD_NUM_REQ.put("GOOD_TYPE", queryPara);
                }
                if ("09".equals(queryType)) {
                    QRY_UNSOLD_NUM_REQ.put("ADVANCE_PAY_TOP", queryPara);
                }
                if ("10".equals(queryType)) {
                    QRY_UNSOLD_NUM_REQ.put("ADVANCE_PAY_LOWER", queryPara);
                }
            }
        }
        Map req = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("QRY_UNSOLD_NUM_REQ", QRY_UNSOLD_NUM_REQ));
        Exchange qryExchange = ExchangeUtils.ofCopy(req, exchange);// 报
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.osn.services.QueryNumberCenter");

        dealNumberCenterReturn(qryExchange, ifAllNumCenter, msg4Json, exchange);

    }

    /**
     * 工号与库池对应关系查询接口处理
     * 当前只处理从池中选号
     * 
     * @param exchange
     * @param QRY_UNSOLD_NUM_REQ
     * @throws Exception
     */
    private void qryPoolStockInfo(Exchange exchange, Map QRY_UNSOLD_NUM_REQ) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSONObject.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        // 准备参数调用号卡中心
        Map QRY_POOL_STOCKINFO_REQ = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        QRY_POOL_STOCKINFO_REQ.put("STAFF_ID", msg.get("operatorId"));
        QRY_POOL_STOCKINFO_REQ.put("PROVINCE_CODE", msg.get("province"));
        QRY_POOL_STOCKINFO_REQ.put("CITY_CODE", msg.get("city"));
        QRY_POOL_STOCKINFO_REQ.put("SYS_CODE", sysCode);// 操作系统编码
        QRY_POOL_STOCKINFO_REQ.put("QUERY_TYPE", "00");
        QRY_POOL_STOCKINFO_REQ.put("CHANNEL_ID", msg.get("channelId"));
        QRY_POOL_STOCKINFO_REQ.put("CHANNEL_TYPE", msg.get("channelType"));
        Map req = NumFaceHeadHelper.creatHead(exchange.getAppkey());// 需确认
        req.put("UNI_BSS_BODY", MapUtils.asMap("QRY_POOL_STOCKINFO_REQ", QRY_POOL_STOCKINFO_REQ));
        Exchange qryPoolExchange = ExchangeUtils.ofCopy(req, exchange);// 报文
        CallEngine.numCenterCall(qryPoolExchange, "ecaop.comm.conf.url.numbercenter.qryPoolStockInfo");
        // 处理返回
        String out = qryPoolExchange.getOut().getBody().toString();
        Map rsp = (Map) JSON.parse(out);
        Map RSP_UNI_BSS_BODY = (Map) rsp.get("UNI_BSS_BODY");
        Map RSP_UNI_BSS_HEAD = (Map) rsp.get("UNI_BSS_HEAD");
        if (null != RSP_UNI_BSS_BODY) {
            Map QRY_POOL_STOCKINFO_RSP = (Map) RSP_UNI_BSS_BODY.get("QRY_POOL_STOCKINFO_RSP");
            if (null == QRY_POOL_STOCKINFO_RSP) {
                throw new EcAopServerBizException("9999", (String) RSP_UNI_BSS_HEAD.get("RESP_DESC"));
            }
            String rspCode = (String) QRY_POOL_STOCKINFO_RSP.get("RESP_CODE");
            if (!"0000".equals(rspCode)) {
                throw new EcAopServerBizException(rspCode, "号卡中心返回：" + QRY_POOL_STOCKINFO_RSP.get("RESP_DESC"));
            }
            // 获取池信息
            List<Map> POOL_INFO = (List<Map>) QRY_POOL_STOCKINFO_RSP.get("POOL_INFO");
            String POOL_CODE = "";
            String POOL_TYPE = "";
            if (null != POOL_INFO) {
                for (Map poolInfo : POOL_INFO) {
                    POOL_TYPE = (String) poolInfo.get("POOL_TYPE");
                    if ("1".equals(POOL_TYPE)) {
                        POOL_CODE = (String) poolInfo.get("POOL_CODE");
                    }
                }
            }
            // 池ID放入选号接口参数列表
            QRY_UNSOLD_NUM_REQ.put("POOL_CODE", POOL_CODE);
        }
        else {
            throw new EcAopServerBizException("9999", "调号卡中心接口返回异常!");
        }
    }

    /*
     * 处理号卡中心返回数据
     */
    @SuppressWarnings("rawtypes")
    public void dealNumberCenterReturn(Exchange exchange, String ifAllNumCenter, Map map, Exchange exchangeOld) {
        String rsp = exchange.getOut().getBody().toString();
        Map out = (Map) JSON.parse(rsp);
        Map UNI_BSS_BODY1 = (Map) out.get("UNI_BSS_BODY");
        if (null != UNI_BSS_BODY1) {
            Map QRY_UNSOLD_NUM_RSP = (Map) UNI_BSS_BODY1.get("QRY_UNSOLD_NUM_RSP");
            if (null == QRY_UNSOLD_NUM_RSP) {
                Map UNI_BSS_HEAD = (Map) UNI_BSS_BODY1.get("UNI_BSS_HEAD");
                throw new EcAopServerBizException("9999", (String) UNI_BSS_HEAD.get("RESP_DESC"));
            }
            String code = (String) QRY_UNSOLD_NUM_RSP.get("RESP_CODE");
            if (!"0000".equals(code)) {
                throw new EcAopServerBizException(code, "号卡中心返回：" + QRY_UNSOLD_NUM_RSP.get("RESP_DESC"));
            }
            else {
                List<Map> RESOURCES_INFO = null;
                RESOURCES_INFO = (List<Map>) (QRY_UNSOLD_NUM_RSP.get("RESOURCES_INFO"));
                if ((RESOURCES_INFO.size() < 200) && (!"OK".equals(ifAllNumCenter))) {// 号卡中心为非全量割并且返回号码数量少于200时,重新发起请求
                    List<Map> para = new ArrayList<Map>();
                    Map para1 = new HashMap();
                    para1.put("paraId", "callAgain");
                    para1.put("paraValue", "Yes");
                    para.add(para1);
                    map.put("para", para);
                    String str4Json = Map2Json(map);
                    String a = JSON.toJSONString(map);
                    String backData = HttpClientNumCenter.testConnType(exchangeOld, (JSON.toJSONString(map)));
                    Map numInfo = (Map) JSON.parse(backData);
                    List<Map> numInfoList = (List<Map>) numInfo.get("numInfo");
                    if (numInfoList == null) {
                        List<Map> numInfoFail = dealNumBack(RESOURCES_INFO);
                        Map num = new HashMap();
                        num.put("numInfo", numInfoFail);
                        exchangeOld.getOut().setBody(num);
                    }
                    else {
                        List<Map> numInfoList1 = new ArrayList<Map>();
                        for (int i = 0; i < numInfoList.size(); i++) {
                            numInfoList1.add(numInfoList.get(i));
                            if (RESOURCES_INFO.size() + numInfoList1.size() >= 200) {
                                break;
                            }
                        }
                        List<Map> numberCenterResources = dealNumBack(RESOURCES_INFO);
                        numberCenterResources.addAll(numInfoList1);
                        Map num = new HashMap();
                        num.put("numInfo", numberCenterResources);
                        exchangeOld.getOut().setBody(num);
                    }
                }
                else {
                    List<Map> numInfo = dealNumBack(RESOURCES_INFO);
                    Map num = new HashMap();
                    num.put("numInfo", numInfo);
                    exchangeOld.getOut().setBody(num);
                }
            }
        }
        else {
            throw new EcAopServerBizException("9999", "调号卡中心接口返回异常!");
        }

    }

    /*
     * 号卡中心返回的数据转换格式
     */
    public List<Map> dealNumBack(List<Map> RESOURCES_INFO) {
        List<Map> numInfo = new ArrayList<Map>();
        for (Map res : RESOURCES_INFO) {
            Map send = new HashMap();
            send.put("numId", res.get("SERIAL_NUMBER"));
            send.put("numMemo", res.get(""));
            if ("hnpr".equals(appCode)) {
                send.put("advancePay", Integer.valueOf(res.get("ADVANCE_PAY").toString()) + "");
                send.put("lowCostPro", Integer.valueOf(res.get("LOW_COST").toString()) + "");
            }
            else {
                send.put("advancePay", Integer.valueOf(res.get("ADVANCE_PAY").toString()) * 10 + "");
                send.put("lowCostPro", Integer.valueOf(res.get("LOW_COST").toString()) * 10 + "");
            }

            send.put("classId", dealNumGo((String) res.get("GOOD_LEVEL")));
            send.put("timeDurPro", res.get("ONLINE_LENGTH"));
            numInfo.add(send);
        }
        return numInfo;
    }

    // 下发号卡资源转码
    public String dealNumBack(String code) {
        Map preDateMap = MapUtils.asMap("0", "1", "1", "2", "2", "3", "3", "4", "4", "5", "5", "6", "6", "99");
        String str = (String) preDateMap.get(code);
        return str;
    }

    // 下发上游等级转码
    public String dealNumGo(String code) {
        return code = ("99".equals(code)) ? "9" : code;
    }

    public String Map2Json(Map inMap) {
        String jsonTag = "\"";
        String a = inMap.toString().replace(", ", jsonTag + "," + jsonTag)
                .replace("=", jsonTag + ":" + jsonTag).replace("}", jsonTag + "}").replace("]" + jsonTag, "]")
                .replace(jsonTag + "[", "[").replace(jsonTag + "{", "{").replace("}" + jsonTag, "}")
                .replace(":" + jsonTag + ",", ":" + jsonTag + jsonTag + ",")
                .replace(":" + jsonTag + "]", ":" + jsonTag + jsonTag + "]")
                .replace(":" + jsonTag + "}", ":+" + jsonTag + jsonTag + "}").replace("{", "{" + jsonTag)
                .replace(jsonTag + jsonTag, jsonTag).replace("equals", "=").replace("comma", ",");
        return a;
    }
}
