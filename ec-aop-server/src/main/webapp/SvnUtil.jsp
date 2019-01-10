<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <head>
    <base href="<%=basePath%>">
    
    <title>版本文件</title>
    
	<meta http-equiv="pragma" content="no-cache">
	<meta http-equiv="cache-control" content="no-cache">
	<meta http-equiv="expires" content="0">    
	<meta http-equiv="keywords" content="keyword1,keyword2,keyword3">
	<meta http-equiv="description" content="This is my page">
    <style type="text/css">
        h2 {
            text-align:center;
        }
        table {
            width:120%;
            margin:0 auto;
            border:2px solid #aaa;
            border-collapse:collapse;
        }
        table th, table td {
            border:2px solid #aaa;
            padding:5px;
        }
        th {
            background-color:#eee;
        }
        
        #left{
            float:left;
        }
        #right{
            float:right;
        }
      </style>
      
    <script type="text/javascript" src="jquery-1.11.1.js"></script>
    <script type="text/javascript">
        
    </script>

  </head>
  
  <body>
    <div class="hader-table">
        <table>
            <tr>
                <th width="120">分类</th>
                <th width="140">系统</th>
                <th width="150">接入方编码</th>
                <th width="170">业务场景</th>
                <th width="170">接口列表</th>
                <th width="600">接口名</th>
                <th width="120">变更内容</th>
                <th width="120">是否验证</th>
                <th width="130">生产验证配合人</th>
                <th width="150">联系电话</th>
            </tr>
         </table>
    </div>
    <form action="SvnCommit.do" method="post">
        <div class="content-table">
            <div id="lift">
                <table>
                    <tr>
                        <th width="120">需求</th>
                    </tr>
                </table>
            </div>
            <div id="right">
                <table>
                <tr>
                    <td width="140"></td>
                    <td width="150"></td>
                    <td width="170"></td>
                    <td width="170"></td>
                    <td width="600"></td>
                    <td width="120"></td>
                    <td width="120"></td>
                    <td width="130"></td>
                    <td width="150"></td>
                </tr>
            </table>
            </div>
        </div>
    </form>
  </body>
</html>
