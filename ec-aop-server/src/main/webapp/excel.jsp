<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <head>
    <base href="<%=basePath%>">
    
    <title>My JSP 'excel.jsp' starting page</title>
    
	<meta http-equiv="pragma" content="no-cache">
	<meta http-equiv="cache-control" content="no-cache">
	<meta http-equiv="expires" content="0">    
	<meta http-equiv="keywords" content="keyword1,keyword2,keyword3">
	<meta http-equiv="description" content="This is my page">
	<!--
	<link rel="stylesheet" type="text/css" href="styles.css">
	-->

  </head>
  
  <body>
    <h2>版本文件</h2>
    <form action="SvnCommit.do" method="post">
        <table>
            <tr>
                <td>版本:</td>
                <td>
                    <input type="text" name="nicai">
                </td>
            </tr>
            <tr>
                <td>内容:</td>
                <td>
                    <input type="text" name="nicaiwocaibucai">
                </td>
            </tr>
        </table>
        <p>
            <input type="submit" class="button" value="确定" />
        </p>
    </form>
  </body>
</html>
