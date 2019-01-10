package com.ailk.ecaop.biz.balance;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.TransReqMsg;
import com.ailk.ecaop.common.utils.TransRspMsg;

/**
 * ClassName: BalanceQryProcessor
 * 
 * @Description: 余额查询
 * @author cuiminyi
 * @date 2016-7-29
 */
@EcRocTag("BalanceQur")
public class BalanceQryProcessor extends BaseAopProcessor implements
		ParamsAppliable {
	private String url;
	private String port;
	static char packageEnd = (char) 0x1a;
	static int no = 1;
	static char spFiled = (char) 0x09;

	@Override
	public void process(Exchange exchange) throws Exception {
		Map body = exchange.getIn().getBody(Map.class);
		Map mapMsg = (Map) body.get("msg");
		mapMsg.put("busiType", mapMsg.get("netType"));
		Socket socket = null;
		socket = new Socket(url, Integer.parseInt(port));
		InputStream in = new BufferedInputStream(socket.getInputStream());
		OutputStream out = new BufferedOutputStream(socket.getOutputStream());
		String headMsg = TransReqMsg.getReqMsg(mapMsg);
		StringBuffer bodyMsg = new StringBuffer();
		bodyMsg.append(mapMsg.get("qryType"));
		String reqMsg = headMsg + bodyMsg.toString() + packageEnd;
		byte[] real = reqMsg.getBytes("UTF-8");
		out.write(real);
		out.flush();
		String rspMsgs = TransRspMsg.getRspMsg(in).substring(102);

		// 读取服务端返回数据剔除结束符
		if (rspMsgs != null) {
			Pattern p = Pattern.compile(String.valueOf(packageEnd));
			Matcher m = p.matcher(rspMsgs);
			rspMsgs = m.replaceAll("");
		}
		String splitString = String.valueOf(spFiled);
		String rspMsg[] = rspMsgs.split(splitString);
		Map msgMap = new HashMap();
		msgMap.put("realTimeFee", rspMsg[0]);
		msgMap.put("realTimeBalance", rspMsg[1]);
		msgMap.put("feeBalance", rspMsg[2]);
		msgMap.put("feeBalanceDesc", rspMsg[3]);
		Message outMsg = new DefaultMessage();
		outMsg.setBody(msgMap);
		exchange.setOut(outMsg);
	}

	@Override
	public void applyParams(String[] params) {
		url = Config.getStr(ParamsUtils.getStr(params, 0, ""));
		port = Config.getStr(ParamsUtils.getStr(params, 1, ""));
	}
}
