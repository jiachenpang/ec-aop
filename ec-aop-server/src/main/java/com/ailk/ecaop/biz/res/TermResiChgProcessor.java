package com.ailk.ecaop.biz.res;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.OperateBaseCheckUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.ailk.ecaop.dao.essbusiness.EssProvinceDao;

/**
 * 根据机型锁定和解锁库存
 */
@EcRocTag("termResiChg")
public class TermResiChgProcessor extends BaseAopProcessor {

    EssBusinessDao essDao = new EssBusinessDao();
    EssProvinceDao proDao = new EssProvinceDao();
    OperateBaseCheckUtils operCheckDao = new OperateBaseCheckUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // Map body = (Map) exchange.getIn().getBody();
        // Map msg = (Map)body.get("msg");
        Map body = new HashMap((Map) exchange.getIn().getBody());
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Map staffMap = operCheckDao.baseCheck(msg);
        String staffId = (String) staffMap.get("STAFF_ID");
        msg.put("staffId", staffId);
        Object opeType = msg.get("opeType");
        String channelType = (String) msg.get("channelType");
        String logId = proDao.selSubscribeId(msg);
        msg.put("logId", logId);/* 记录日志流水 */
        if ("1".equals(opeType)) {
            msg.put("opeTypeToSap", "0");
        }
        if ("2".equals(opeType)) {
            msg.put("opeTypeToSap", "1");
        }
        String termChgToSap = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><STD_IN><Channel_Code>" +
                msg.get("channelId") + "</Channel_Code><Staff_ID>" + msg.get("operatorId") +
                "</Staff_ID><updateTime>" + GetDateUtils.getDate() +
                "</updateTime><propertyCode>" + msg.get("machineTypeCode") +
                "</propertyCode><opType>" + msg.get("opeTypeToSap") + "</opType><proKey>" +
                msg.get("proKey") + "</proKey><count>1</count><province>" +
                msg.get("province") + "</province><city>" + msg.get("city") + "</city></STD_IN>";

        if (channelType.startsWith("10")) { // 自由渠道
            chkTranTagByChannCode(msg);
            Integer lockNum = selLockNumByMachCode(msg);
            msg.put("lockNum", lockNum);
            if (lockNum < 0) {
                throw new EcAopServerBizException("9999", "其他数据错误！");
            }
            if ("1".equals(opeType)) {
                Integer availNum = selIdleNumByMachCode(msg) - lockNum;
                if (availNum == 0) {
                    throw new EcAopServerBizException("0001", "库存不足！");
                }
                if (availNum < 0) {
                    throw new EcAopServerBizException("9999", "其他数据错误！");
                }
                if (selLockOrderNum(msg) > 0) {
                    throw new EcAopServerBizException("9999", "该关键字与此机型已有锁定记录！");
                }
                essDao.addLockNumInTranLock(msg);
                essDao.addLockOrderInfo(msg);
                essDao.insertLockInfoLog(msg);
            }
            else if ("2".equals(opeType)) {
                if (selLockOrderNum(msg) <= 0) {
                    throw new EcAopServerBizException("0002", "没有锁定记录！");
                }
                essDao.decLockNumInTranLock(msg);
                essDao.updateLock2Release(msg);
                essDao.insertLockInfoLog(msg);
            }
            else {
                throw new EcAopServerBizException("9999", "无效的操作标识！");
            }
        }
        else {
            throw new EcAopServerBizException("9999", "该渠道为社会渠道，暂不支持！");
        }
        body.put("REQ_XML", termChgToSap);
        exchange.getIn().setBody(body);
        try {
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.services.TermChgToSap");
        }
        catch (Exception e) {

        }

        Message out = new DefaultMessage();
        out.setBody(new HashMap());
        exchange.setOut(out);
    }

    /**
     * 用于判断渠道是否为转型渠道
     */
    private void chkTranTagByChannCode(Map msg) {
        if (1 != essDao.chkTranTagByChannCode(msg)) {
            throw new EcAopServerBizException("9999", "该渠道为非转型渠道，暂不支持！");
        }
    }

    /**
     * 查询自提终端表中相关关键字的锁定数量
     */
    private int selLockOrderNum(Map msg) {
        return essDao.selLockOrderNum(msg);
    }

    /**
     * 查询转型渠道终端锁定表的锁定数量
     */
    private int selLockNumByMachCode(Map msg) {
        List lockNumList = essDao.selLockNumByMachCode(msg);
        if (IsEmptyUtils.isEmpty(lockNumList)) {
            essDao.addLockNumInfoInTranLock(msg);
            return 0;
            // throw new EcAopServerBizException("9999", "转型渠道终端锁定表无此机型编码及渠道的锁定信息！");
        }
        int lockNum = Integer.valueOf(lockNumList.get(0).toString());
        return lockNum;
    }

    /**
     * 查询转型渠道串码表中串码空闲数量
     */
    private int selIdleNumByMachCode(Map msg) {
        return essDao.selIdleNumByMachCode(msg);
    }

}
