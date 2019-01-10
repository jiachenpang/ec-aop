package com.ailk.ecaop.biz.numCenter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.dao.essbusiness.EssProvinceDao;
import com.alibaba.fastjson.JSON;

/**
 * 对接号卡中心--号码变更场景
 * 新号在号卡，老号在3GE
 * 
 * @auther Zhao ZhengChang
 * @create 2016_10_02_15:25
 */
@EcRocTag("numberCenterToChangeNumber")
public class NumberCenterToChangeNumberProcessor extends BaseAopProcessor {

    ChangeNumberStatusForNumberCenterProcessor cs = new ChangeNumberStatusForNumberCenterProcessor();
    EssProvinceDao pDao = new EssProvinceDao();
    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map occupyInfo = null;// 预占表信息
        Map idleAndBookInfo = null;// 号码表信息
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");

        for (Map res : resourcesInfo) {
            msg.put("serialNumber", res.get("oldResourcesCode"));
            msg.put("serialNumberNew", res.get("resourcesCode"));
            msg.put("sysCode", nc.changeSysCode(exchange));
            System.out.println(";';msg:" + msg + "结束");
            List occupyInfoList = pDao.selOccupyInfo(msg);// 查询预占表

            if (occupyInfoList != null && occupyInfoList.size() > 0) {
                occupyInfo = (Map) occupyInfoList.get(0);
            }
            System.out.println(";';:occupyInfo" + occupyInfo);
            if (occupyInfo == null) {
                List idleAndBookInfoList = pDao.selIDLEInfo(msg);// 查询号码表信息
                if (idleAndBookInfoList != null && idleAndBookInfoList.size() > 0) {
                    idleAndBookInfo = (Map) idleAndBookInfoList.get(0);
                }
                System.out.println(";';:idleAndBookInfo" + idleAndBookInfo);
                if (idleAndBookInfo == null) {
                    throw new EcAopServerBizException("9999", "原号码信息不存在！");
                }
                // 校验新老号码信息
                res.put("sysCode", nc.changeSysCode(exchange));
                res.put("operatorId", msg.get("operatorId") + "");
                checkInfo(idleAndBookInfo, res);
                System.out.println(";';校验：" + idleAndBookInfo + "结束" + "msg");

                msg.put("serialNumber", res.get("resourcesCode") + "");
                msg.put("proKey", res.get("proKey"));
                // 用新号码调号卡中心选占接口
                cs.callSelectionNum(exchange, msg, res);

                // 用新号码调号卡中心延时接口
                cs.callDelayOcpNumber(exchange, msg, res, (String) res.get("occupiedTime"));// 之前是这个：getOccupiedTime()

                // 调3GE释放老号码
                res.put("resourcesCode", res.get("oldResourcesCode") + "");
                exchange.getIn().setBody(body);
                releaseNum(exchange);
            }
            else {
                // 校验新老号码信息
                occupyInfo.put("UPDATE_STAFF", occupyInfo.get("OCCUPY_STAFF_ID") + "");
                res.put("sysCode", nc.changeSysCode(exchange));
                res.put("operatorId", msg.get("operatorId") + "");
                checkInfo(occupyInfo, res);

                // 判断老号码是否已释放
                String oldDate = (String) occupyInfo.get("RELEASE_TIME");
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = dateFormat.parse(oldDate);
                oldDate = dateFormat.format(date);
                System.out.println(";';oldDate：" + oldDate);
                long releaseTime = Long.valueOf(GetDateUtils.transDate(oldDate, 14));
                long nowTime = Long.valueOf(GetDateUtils.transDate(GetDateUtils.getSysdateFormat(), 14));
                System.out.println(";';时间：" + occupyInfo.get("RELEASE_TIME") + "" + ";;;" + releaseTime + ";;;"
                        + nowTime + ";;;" + (releaseTime > nowTime));
                if (releaseTime > nowTime) {// 越近的时间越大
                    // 用新号码调号卡中心选占接口
                    cs.callSelectionNum(exchange, msg, res);
                    // 调释放接口
                    res.put("resourcesCode", res.get("oldResourcesCode") + "");
                    exchange.getIn().setBody(body);
                    releaseNum(exchange);
                }
            }
        }
        // 处理返回
        dealReturn(exchange, msg);
    }

    /**
     * 新老号码信息校验
     * 
     * @param oldInfo
     * @param newInfo
     * @throws Exception
     */
    public void checkInfo(Map oldInfo, Map newInfo) throws Exception {
        // 先判断工号（必须相等），在判断关键字，证件号，system是否为空，若不为空则必须相等才行，若为空接着往下走
        // 工号校验
        if (oldInfo.get("UPDATE_STAFF") != null
                && !(newInfo.get("operatorId") + "").equals((oldInfo.get("UPDATE_STAFF") + ""))) {
            throw new EcAopServerBizException("9999", "员工信息不一致，不允许换号！");
        }
        // 预占关键字校验
        if (oldInfo.get("PROC_KEY") != null && !(newInfo.get("proKey") + "").equals((oldInfo.get("PROC_KEY") + ""))) {
            throw new EcAopServerBizException("9999", "预占关键字不一致，不允许换号！");
        }
        // 证件号校验
        if (oldInfo.get("CKVALUE2") != null && !((newInfo.get("certNum") + "")).equals((oldInfo.get("CKVALUE2") + ""))) {
            throw new EcAopServerBizException("9999", "证件号不一致，不允许换号！");
        }
        // SYS_CODE校验
        if (oldInfo.get("SYS_CODE") != null && !((newInfo.get("sysCode") + "")).equals((oldInfo.get("SYS_CODE") + ""))) {
            throw new EcAopServerBizException("9999", "操作系统不一致，不允许换号！");
        }
    }

    /**
     * 调3GE号码释放接口
     * 
     * @param exchange
     * @throws Exception
     */
    public void releaseNum(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        ((Map) (((List) (msg.get("resourcesInfo"))).get(0))).put("occupiedFlag", "4");
        body.put("msg", msg);
        System.out.println(";';释放：" + msg);
        exchange.getIn().setBody(body);
        lan.preData("ecaop.gdjk.numcheck.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4ONS("ecaop.gdjk.numcheck.template", exchange);

        // 判断是否释放成功
        Map retMap = exchange.getOut().getBody(Map.class);
        System.out.println("释放后的Map：" + retMap);
        Map resourcesRsp = (Map) ((List) retMap.get("resourcesRsp")).get(0);
        String rscStateCode = resourcesRsp.get("rscStateCode") + "";
        String rscStateDesc = (resourcesRsp.get("rscStateDesc") + "").length() > 0 ? (resourcesRsp.get("rscStateDesc") + "")
                : "号码释放异常！";
        if (!"0000".equals(rscStateCode)) {
            throw new EcAopServerBizException(rscStateCode, rscStateDesc);
        }
        exchange.getIn().setBody(body);
    }

    /**
     * 获取延时时间
     * 
     * @param time
     * @return
     * @throws ParseException
     */
    public String getOccupiedTime() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, +7);// 默认延时7天
        date = calendar.getTime();
        String occupiedTime = dateFormat.format(date);
        return occupiedTime;
    }

    /**
     * 处理返回
     * 
     * @param exchange
     * @param msg
     */

    public void dealReturn(Exchange exchange, Map msg) {
        Map out = new HashMap();
        Map temp = new HashMap();
        temp.put("resourcesType", "02");
        temp.put("resourcesCode", msg.get("serialNumberNew"));
        temp.put("rscStateCode", "0000");
        temp.put("rscStateDesc", "号码变更成功！");
        temp.put("numId", msg.get("serialNumberNew"));
        List<Map> resourcesRsp = new ArrayList();
        resourcesRsp.add(temp);
        out.put("resourcesRsp", resourcesRsp);
        exchange.getOut().setBody(out);
    }
}
