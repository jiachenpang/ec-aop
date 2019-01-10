package com.ailk.ecaop.biz.numCenter;
/**
 * Created by Liu JiaDi on 2016/8/24.
 */

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.esql.Esql;

import java.util.List;
import java.util.Map;

/**
 * CBSS简单用户查询接口
 *
 * @auther Liu JiaDi
 * @create 2016_08_24_15:25
 * msg里面必须有serialNumber
 * code=1024时表示无资料
 */
@EcRocTag("simpleCheckUserInfoProCessor")
public class CbssSimpleCheckUserInfoProCessor extends BaseAopProcessor {
    LanUtils lan = new LanUtils();
    //static Esql dao = DaoEngine.get3GeDao("/com/ailk/ecaop/sql/aop/common.esql");

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String province = (String) msg.get("province");

        if (isCallSimpleCheckUser(msg)) {
            msg.put("province", "99");
            lan.preData("ecaop.trades.core.simpleCheckUserInfo.ParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.UsrSer");
            lan.xml2JsonNoError("ecaop.trades.core.simpleCheckUserInfo.template", exchange);
            Map out = exchange.getOut().getBody(Map.class);
            // 0000 存在客户资料 不能选用此号码
            if ("0000".equals(out.get("code"))) {

                if (out.get("userInfo") == null) {
                    throw new EcAopServerBizException("9999", "用户简单查询接口没有返回客户信息");
                }
                Map userInfo = (Map) out.get("userInfo"); // 已存在的用户信息
                String openDate = (String) userInfo.get("openDate"); // 入网时间
                String errMsg = "您选择的号码" + msg.get("serialNumber") + "已经于" + openDate.substring(0, 4)
                        + "年" + openDate.substring(4, 6) + "月" + openDate.substring(6, 8) + "日被占用，请重新选择!";
                throw new EcAopServerBizException("9999", errMsg);
            } else if ("8888".equals(out.get("code"))) {
                throw new EcAopServerBizException("9999", "用户简单查询接口调用失败，省份返回编码8888：" + out.get("detail"));
            }
        }
        //这是个bug不清楚为什么这个省份地址会覆盖
        msg.put("province", province);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    private boolean isCallSimpleCheckUser(Map msg) {
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        if (IsEmptyUtils.isEmpty(resourcesInfo)) {
            return false;
        }
        List<String> result = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/aop/common.esql").id("qryCbSimpleCheckUserInfoTag").params(msg).execute();
        if (IsEmptyUtils.isEmpty(result)) {
            return false;
        }
        return "1".equals(result.get(0)) ? true : false;
    }
}
