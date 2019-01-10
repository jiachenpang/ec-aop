package com.ailk.ecaop.common;

public interface Itarget {

    final String REQ_SUFFIX = "_Req.xml";

    final String SYSTEM_TAG_3GE = "3GE";
    final String SYSTEM_TAG_FBS = "FBS";
    final String SYSTEM_TAG_FBS2 = "FBS2";
    final String SYSTEM_TAG_ONS = "ONS";
    final String SYSTEM_TAG_ESS = "ESS";
    final String SYSTEM_TAG_FPAY = "FPAY";

    final String REQ_JSON = "{OrigDomain:\"UESS\",HomeDomain:\"UCRM\",BIPVer:\"0100\",ActivityCode:\"${activityCode}\","
            + "ActionCode:\"0\",Routing:{RouteType:\"00\",RouteValue:\"${route!province}\"},ProcID:${transIDO},"
            + "TransIDO:${transIDO},ProcessTime:${processTime},TestFlag:\"${testFlag}\",SvcContVer:\"0100\",${content}:${msg}}";

    final String REQ_JSON_ONS = "{OrigDomain:\"${origDomain!'ECIP'}\",HomeDomain:\"UCRM\","
            + "BIPCode:\"${bipCode!'BIP2F036'}\",BIPVer:\"0100\",ActivityCode:\"${activityCode}\","
            + "ActionCode:\"0\",Routing:{RouteType:\"${routeType!'00'}\",RouteValue:\"${route!province}\"},ProcID:\"${procId!transIDO}\","
            + "TransIDO:\"${transIDO}\",ProcessTime:\"${processTime}\",TestFlag:\"${testFlag}\","
            + "${content}:${msg},OperatorID:\"${operatorId}\",Province:\"${province}\",City:\"${city}\","
            + "ChannelID:\"${channelId}\",ChannelType:\"${channelType}\"}";
    final String REQ_JSON_ESS = "{OrigDomain:\"${origDomain!'UESS'}\",HomeDomain:\"UCRM\","
            + "BIPCode:\"${bipCode!'BIP2F036'}\",BIPVer:\"0100\",ActivityCode:\"${activityCode}\","
            + "ActionCode:\"0\",Routing:{RouteType:\"00\",RouteValue:\"${route!province}\"},ProcID:\"${transIDO}\","
            + "TransIDO:\"${transIDO}\",ProcessTime:\"${processTime}\",TestFlag:\"${testFlag}\","
            + "${content}:${msg},OperatorID:\"${operatorId}\",Province:\"${province}\",City:\"${city}\","
            + "ChannelID:\"${channelId}\",ChannelType:\"${channelType}\"}";

    final String REQ_JSON_FBS = "{ORIG_DOMAIN:\"${origDomain!'ECIP'}\",SERVICE_NAME:\"${serviceName}\","
            + "OPERATE_NAME:\"${operateName}\",ACTION_CODE:\"0\",ACTION_RELATION:\"0\","
            + "ROUTING:{ROUTE_TYPE:\"${routeType!'00'}\",ROUTE_VALUE:\"${route!province}\"},PROC_ID:\"${transIDO}\",TRANS_IDO:\"${transIDO}\","
            + "TRANS_IDH:\"${processTime}\",PROCESS_TIME:\"${processTime}\",COM_BUS_INFO:{OPER_ID:\"${operatorId}\","
            + "PROVINCE_CODE:\"${province}\",EPARCHY_CODE:\"${city}\",CITY_CODE:\"${district}\","
            + "CHANNEL_ID:\"${channelId}\",CHANNEL_TYPE:\"${channelType}\",ACCESS_TYPE:\"${accessType}\","
            + "ORDER_TYPE:\"${orderType!'00'}\"},SP_RESERVE:{TRANS_IDC:\"${transIDO}\",CUTOFFDAY:\"${cutOffDay}\","
            + "OSNDUNS:\"${osnDuns!'0002'}\",HSNDUNS:\"${hsnDuns!osnDuns!'0002'}\",CONV_ID:\"${processTime}\"},"
            + "TEST_FLAG:\"${testFlag!'0'}\",MSG_SENDER:\"${msgSender!'9801'}\",MSG_RECEIVER:\"${msgReceiver!'9800'}\",${content}:${msg}}";

    final String REQ_JSON_FBS2 = "{ORIG_DOMAIN:\"${origDomain!'ECIP'}\",SERVICE_NAME:\"${serviceName}\","
            + "OPERATE_NAME:\"${operateName}\",ACTION_CODE:\"0\",ACTION_RELATION:\"0\","
            + "ROUTING:{ROUTE_TYPE:\"01\",ROUTE_VALUE:\"${numId}\"},PROC_ID:\"${transIDO}\",TRANS_IDO:\"${transIDO}\","
            + "TRANS_IDH:\"${processTime}\",PROCESS_TIME:\"${processTime}\",COM_BUS_INFO:{OPER_ID:\"${operatorId}\","
            + "PROVINCE_CODE:\"${province}\",EPARCHY_CODE:\"${city}\",CITY_CODE:\"${district}\","
            + "CHANNEL_ID:\"${channelId}\",CHANNEL_TYPE:\"${channelType}\",ACCESS_TYPE:\"${accessType}\","
            + "ORDER_TYPE:\"${orderType!'00'}\"},SP_RESERVE:{TRANS_IDC:\"${transIDO}\",CUTOFFDAY:\"${cutOffDay}\","
            + "OSNDUNS:\"${osnDuns!'0002'}\",HSNDUNS:\"${osnDuns!'0002'}\",CONV_ID:\"${processTime}\"},"
            + "TEST_FLAG:\"${testFlag!'0'}\",MSG_SENDER:\"${msgSender!'9801'}\",MSG_RECEIVER:\"${msgReceiver!'9800'}\",${content}:${msg}}";

    final String REQ_JSON_FPAY = "{ORIG_DOMAIN:\"${origDomain!'ECIP'}\",SERVICE_NAME:\"${serviceName}\","
            + "OPERATE_NAME:\"${operateName}\",ACTION_CODE:\"0\",ACTION_RELATION:\"0\","
            + "ROUTING:{ROUTE_TYPE:\"00\",ROUTE_VALUE:\"${routeValue}\"},PROC_ID:\"${transIDO}\",TRANS_IDO:\"${transIDO}\","
            + "TRANS_IDH:\"${processTime}\",PROCESS_TIME:\"${processTime}\",COM_BUS_INFO:{OPER_ID:\"${operatorId}\","
            + "PROVINCE_CODE:\"${province}\",EPARCHY_CODE:\"${city}\",CITY_CODE:\"${district}\","
            + "CHANNEL_ID:\"${channelId}\",CHANNEL_TYPE:\"${channelType}\",ACCESS_TYPE:\"${accessType}\","
            + "ORDER_TYPE:\"${orderType!'00'}\"},SP_RESERVE:{TRANS_IDC:\"${transIDO}\",CUTOFFDAY:\"${cutOffDay}\","
            + "OSNDUNS:\"${osnDuns!'0002'}\",HSNDUNS:\"${osnDuns!'0002'}\",CONV_ID:\"${processTime}\"},"
            + "TEST_FLAG:\"${testFlag!'0'}\",MSG_SENDER:\"9801\",MSG_RECEIVER:\"9800\",${content}:${msg}}";
}
