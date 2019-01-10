<%@page import="org.dom4j.Document"%>
<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%
    String path = request.getContextPath();
    String basePath = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
            + path + "/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<base href="<%=basePath%>">

<title>用于验证国政通服务</title>

<meta http-equiv="pragma" content="no-cache">
<meta http-equiv="cache-control" content="no-cache">
<meta http-equiv="expires" content="0">
<meta http-equiv="keywords" content="keyword1,keyword2,keyword3">
<meta http-equiv="description" content="This is my page">
<!--
	<link rel="stylesheet" type="text/css" href="styles.css">
	-->
</head>
<form action="" method="post">
  <body bgcolor="#ADD8E6">
    <b>请选择地址：</b>
    <br />
    <input type="radio" name="http" value="http://127.0.0.1:7001/aop/aopservlet" title="127.0.0.1:7001">本机环境
    </input> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <input type="radio" name="http" value="http://132.35.81.218:8000/aop/test" title="132.35.81.218:8000">测试环境
    </input> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <input type="radio" name="http" value="http://132.35.81.218:8000/aop/aopservlet" title="132.35.81.218:8000">集成环境
    </input> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <input type="radio" name="http" value="http://132.35.88.104/aop/aopservlet" title="132.35.88.104">生产环境
    </input> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <input type="radio" name="http" value="http://132.35.90.165:8003/aop/aopservlet" title="132.35.90.165:8003">生产环境测试机
    </input> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <br />
    </center>
    <br></form>
源码格式 
<form action="gzt" method="get">
    <select name="orign">
        <option value="GBK" selected="selected">GBK</option>
        <option value="UTF-8">UTF-8</option>
        <option value="GB2312">GB2312</option>
        <option value="ISO8859-1">ISO8859-1</option>
      </select> 转换后的编码格式 
      <select name="result">
        <option value="GBK">GBK</option>
        <option value="UTF-8">UTF-8</option>
        <option value="GB2312">GB2312</option>
        <option value="ISO8859-1">ISO8859-1</option>
      </select>
      <br>请出入要转换的字符
      <input type="textarea" name="toChangeStr"/>
      <input type="submit" id="change" value="转换"/>
      <% Object changed = request.getAttribute("changed"); %>
      <br/>转换后的字符串如下:<%= null== changed ? "" :changed%>
      </form>
</body>
</html>
