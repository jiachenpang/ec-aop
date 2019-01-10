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

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.OperateBaseCheckUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;

/**
 * 用于查询某转型渠道下某终端的可售数量
 */
@EcRocTag("qryTermStock")
public class QryTermStockProcessor extends BaseAopProcessor {

    EssBusinessDao essDao = new EssBusinessDao();
    OperateBaseCheckUtils operCheckDao = new OperateBaseCheckUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Object msg = body.get("msg");
        Map map = null;
        map = (Map) msg;
        operCheckDao.baseCheck(map);
        String channelType = (String) map.get("channelType");
        if (channelType.startsWith("20")) {
            throw new EcAopServerBizException("9999", "该渠道为社会渠道，暂不支持！");
        }
        chkTranTagByChannCode(map);
        Integer lockNum = selLockNumByMachCode(map);
        Integer availNum = selIdleNumByMachCode(map) - lockNum;
        if (availNum < 0) {
            throw new EcAopServerBizException("9999", "其他数据错误！");
        }
        Message out = new DefaultMessage();
        Map retMap = new HashMap();
        retMap.put("count", availNum);
        out.setBody(retMap);
        exchange.setOut(out);
    }

    /**
     * 用于判断渠道是否是转型渠道
     */
    private void chkTranTagByChannCode(Map msg) {
        if (1 != essDao.chkTranTagByChannCode(msg)) {
            throw new EcAopServerBizException("9999", "该渠道为非转型渠道，暂不支持！");
        }
    }

    /**
     * 查询转型渠道串码表中串码空闲数量
     */
    private int selIdleNumByMachCode(Map msg) {
        return essDao.selIdleNumByMachCode(msg);
    }

    /**
     * 查询转型渠道终端锁定表的锁定数量
     */
    private int selLockNumByMachCode(Map msg) {
        List lockNumList = essDao.selLockNumByMachCode(msg);
        if (IsEmptyUtils.isEmpty(lockNumList)) {
            return 0;
        }
        int lockNum = Integer.valueOf(lockNumList.get(0).toString());
        return lockNum;
    }

}
