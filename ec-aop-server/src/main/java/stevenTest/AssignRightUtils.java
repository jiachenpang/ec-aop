package stevenTest;

import java.util.ArrayList;
import java.util.List;

import org.n3r.config.Config;
import org.n3r.ecaop.core.appchecker.EcAopAppAllow;

public class AssignRightUtils {

    static int MAX_LINE_COUNT = 120;

    public static void main(String[] args) {
        String[] methods = new String[] { "ecaop.trades.query.comm.paylist.qry", "ecaop.trades.query.comm.payres.qry",
                "ecaop.trades.serv.payment.fee.sub", "ecaop.trades.serv.payment.fixfee.sub",
                "ecaop.trades.serv.curt.pay.cannel", "ecaop.trades.query.comm.traffic.qry",
                "ecaop.trades.query.comm.flow.qry", "ecaop.trades.query.comm.orderpro.qry",
                "ecaop.trades.sell.mob.comm.traffic.order", "ecaop.trades.sell.mob.comm.traffic.sub",
                "ecaop.trades.query.ordi.service.qry" };
        assign(methods);
    }

    public static void assign(String[] methods) {
        String appkey = "gzpre.sub";
        String appcode = Config.getStr("ecaop.core.app.map." + appkey);
        String methodCode;
        EcAopAppAllow appAllow = Config.getBean("ecaop.core.app." + appcode + ".allow", EcAopAppAllow.class);
        if (null == appAllow) {
            System.err.println("当前接入系统:" + appkey + "未定义或未进行任何的能力分配");
            System.exit(0);
        }
        List<String> dealRet = new ArrayList<String>();
        String allowMethods = "";
        for (String method : methods) {
            methodCode = Config.getStr("ecaop.core.method.map." + method);
            if (null == methodCode) {
                dealRet.add("Method:[" + method + "]不存在或未定义,请进行检查.");
                continue;
            }
            if (allowMethods.contains(methodCode)) {
                dealRet.add("Method:[" + method + "]已存在权限,未进行重复分配.");
            }
            else {
                dealRet.add("Method:[" + method + "]不存在权限,已经分配.短名为:" + methodCode + ".");
                allowMethods = allowMethods + "," + methodCode;
            }
        }
        System.out.println("==================Method权限校验结果如下=========================");
        for (String s : dealRet) {
            System.out.println(s);
        }
        System.out.println("经过赋权之后" + appkey + "拥有的权限如下:");
        int length = allowMethods.length();
        int index = 0;
        if (length > MAX_LINE_COUNT - 50) {
            index = allowMethods.substring(0, MAX_LINE_COUNT - 50).lastIndexOf(",");
            System.out.println(allowMethods.substring(0, index + 1) + "\\");
            allowMethods = allowMethods.substring(index + 1, length);
            length -= index + 1;
        }
        while (length > MAX_LINE_COUNT) {
            index = allowMethods.substring(0, MAX_LINE_COUNT).lastIndexOf(",");
            System.out.println(allowMethods.substring(0, index + 1) + "\\");
            allowMethods = allowMethods.substring(index + 1, length);
            length -= index + 1;
        }
        System.out.println(allowMethods);
    }
}
