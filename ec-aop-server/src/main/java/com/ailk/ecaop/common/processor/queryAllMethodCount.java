package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.esql.Esql;

@EcRocTag("queryAllMethodCount")
public class queryAllMethodCount extends BaseAopProcessor implements ParamsAppliable{

	@Override
	public void process(Exchange exchange) throws Exception {
		/**
		 * 1、拿到exchange中的appcode，bizkey，msg中的省份，标识aop的接口的methodcode
		 * 2、区后台查询出来过滤条件下的全量数据
		 */
		System.out.println("i am coming");
		try {
			Map body = (Map) exchange.getIn().getBody();
			Map msg = (Map) body.get("msg");
			String appCode = exchange.getAppCode();
			String bizkey = exchange.getBizkey();
			String province = (String) msg.get("province");
			// 标识aop的哪个接口
			String markStr = (String) msg.get("markStr");
			Map paramMap = new HashMap();
			paramMap.put("appcode", appCode);
			paramMap.put("province", province);
			System.out.println("apptx:" + body.get("apptx")+";paramMap:" + paramMap);
			//查询不同系统下不通省份的数据
			List resultList = queryMethodCount(paramMap);
			System.out.println("apptx:" + body.get("apptx")+";resultList:" + resultList);
			resultList.add("王宗凯");
			Map<String,List> returnMap = new HashMap();
			returnMap.put("resultList", resultList);
			//处理返回信息
			exchange.getOut().setBody(returnMap);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		
	}

	/**
	 * 处理查询出来的返回信息
	 * @param resultMap
	 */
	private Map  dealReturnParam(Map<String, List> resultMap) {
		
		
		return null;

	}

	private List queryMethodCount(Map paramMap) {
		List resultList = new Esql("ecaop_connect_Oracle")
				.useSqlFile("/com/ailk/ecaop/sql/aop/queryMethodCount.esql").id("queryMethodCount").params(paramMap)
				.execute();
		return resultList;
	}

	@Override
	public void applyParams(String[] params) {
		
	}

}
