package com.ailk.ecaop.biz.res;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;

/**
 * 判断是否为调用资源中心的方法,暂3G换机接口使用
 * @author wangmc
 * @date 2018-06-22
 */
@EcRocTag("resCenterSwitch")
public class GetOldResOrderInfoProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");

        if (!EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province").contains(
                String.valueOf(msg.get("province")))) {
            return;
        }
        // 对接资源中心,从开户预提交之后记录的订单中,查询原终端的销售订单和销售号码
        msg.put("resCenterSwitch", "1");
        msg.put("numberId", msg.get("number"));
        Map sqlMap = MapUtils.asMap("resourcesCode", msg.get("oldResourcesCode"), "province", msg.get("province"),
                "number", msg.get("number"));
        List<Map> saleOrderInfoList = new EssBusinessDao().qryOrderInfoByTerminalId(sqlMap);
        if (!IsEmptyUtils.isEmpty(saleOrderInfoList)) {
            throw new EcAopServerBizException("9999", "未查询到原终端的销售记录");
        }
        // 原销售订单
        msg.put("oldResSaleOrderId", saleOrderInfoList.get(0).get("SUBSCIBE_ID"));
    }

}
