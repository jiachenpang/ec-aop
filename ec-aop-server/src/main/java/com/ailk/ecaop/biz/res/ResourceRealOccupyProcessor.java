package com.ailk.ecaop.biz.res;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;

/**
 * 23G南25省开户,调用资源中心做终端实占
 * @author wangmc
 * @date 2018-05-31
 */
@EcRocTag("ResourceRealOccupyProcessor")
public class ResourceRealOccupyProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // 之前业务均成功,才需要调该接口
        if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        // 获取请求参数
        Map headers = exchange.getIn().getHeader("strParams", Map.class);
        Map msg = JSON.parseObject(String.valueOf(headers.get("msg")));
        // 查询开户单的数据,判断是否需要调用资源中心做终端实占,true-不调号卡中心,false-调号卡中心
        if (isNotResCenter(msg)) {
            return;
        }
        // 调用资源中心做终端实占
        realOccupy(exchange, msg);
    }

    /**
     * 查询开户单的数据,判断是否需要调用资源中心做终端实占,true-不调号卡中心,false-调号卡中心
     * @param orderData
     * @return
     */
    private boolean isNotResCenter(Map msg) {
        // 在3GE中心库查询预提交时记录的终端信息
        try {
            Map sqlMap = MapUtils.asMap("provOrderId", msg.get("provOrderId"), "province", msg.get("province"));
            List<Map> preSubMitDataList = new EssBusinessDao().qryResourceInfo4RollBack(sqlMap);
            if (IsEmptyUtils.isEmpty(preSubMitDataList)) {
                throw new EcAopServerBizException("9999", "查询不到已经开户提交的订单");
            }
            // 获取开户的终端串码
            if (!IsEmptyUtils.isEmpty(preSubMitDataList.get(0).get("TERMINAL_ID"))) {
                msg.put("orderResourcesCode", preSubMitDataList.get(0).get("TERMINAL_ID"));
            }
            else {
                return true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取开户订单数据异常:" + e.getMessage());
        }
        return false;
    }

    /**
     * 调用资源中心做终端实占
     * @param exchange
     * @param msg
     * @param orderData
     */
    private void realOccupy(Exchange exchange, Map msg) throws Exception {
        msg.put("resourcesCode", msg.get("orderResourcesCode"));
        msg.put("number", msg.get("numId"));
        // 默认为订业务送手机
        msg.put("activeType", "04");
        Exchange occupyExchange = ExchangeUtils.ofCopy(exchange, msg);
        // 调用资源中心做终端实占
        try {
            new ResourceStateChangeUtils().realOccupiedResourceInfo(occupyExchange);
        }
        catch (EcAopServerBizException e) {
            e.printStackTrace();
            throw new EcAopServerBizException(e.getCode(), "调用资源中心实占终端出错:" + e.getDetail());
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用资源中心实占终端出错:" + e.getMessage());
        }
    }

}
