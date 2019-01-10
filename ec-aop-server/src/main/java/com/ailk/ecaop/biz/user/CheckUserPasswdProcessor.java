package com.ailk.ecaop.biz.user;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.TransReqMsg;
import com.ailk.ecaop.common.utils.TransRspMsg;


/**用户密码校验
 *
 * @author: crane-yuan[yuanxingnepu@gmail.com]
 * @date: 2017年03月02日 星期四 10时50分25秒
 */

@EcRocTag("CheckUserPasswdProcessor")
public class CheckUserPasswdProcessor extends BaseAopProcessor implements ParamsAppliable {

    static char packageEnd = (char) 0x1a;
    static int no = 1;
    static char spFiled = (char) 0x09;// tab键
    static String recordSpilt = "\r\n";// 回车键
    private String url;
    private int port;

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<?, ?> body = exchange.getIn().getBody(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> mapMsg = (Map<String, String>) body.get("msg");

        // 请求参数转换成符合H2协议头的参数格式
        mapMsg.put("number", mapMsg.get("number"));
        mapMsg.put("channelId", "Z000KF");

        Socket socket = null;

        try {
            socket = new Socket(url, port);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            mapMsg.put("busiType", "T");
            String servceName = String.format("%-12s", "CUSERPWD");
            mapMsg.put("servceName", servceName);
            String headMsg = TransReqMsg.getReqMsg(mapMsg);
            StringBuffer bodyMsg = new StringBuffer();
            bodyMsg.append(mapMsg.get("password")).append(spFiled);
            String reqMsg = headMsg + bodyMsg.toString() + packageEnd;
            byte[] real = reqMsg.getBytes("UTF-8");

            os.write(real);
            os.flush();

            String rspMsgs = TransRspMsg.getRspMsg(in).substring(102);
            // 返回给上游的信息.
            Message out = new DefaultMessage();
            exchange.setOut(out);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "下游返回信息为空!");
        }
        finally {
            try {
                if (socket != null)
                    socket.close();
            }
            catch (IOException e) {
                throw e;
            }
        }

    }

    @Override
    public void applyParams(String[] params) {
        url = Config.getStr(ParamsUtils.getStr(params, 0, ""));
        port = Integer.parseInt(Config.getStr(ParamsUtils.getStr(params, 1, "")));
    }
}
