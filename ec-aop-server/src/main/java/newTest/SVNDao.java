package newTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ailk.ecaop.common.utils.IsEmptyUtils;

public class SVNDao {

    private static Connection conn;
    private static String regex = "(\\[)|(\\])";

    static {
        // Properties prop = new Properties();
        try {
            // prop.load(new FileInputStream("src/main/java/data.properties"));
            // Class.forName(prop.getProperty("driverName"));
            // conn = DriverManager.getConnection(prop.getProperty("url"), prop.getProperty("username"),
            // prop.getProperty("password"));
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql:///svn?useUnicode=true&amp;characterEncoding=utf8", "root",
                    "");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        return conn;
    }

    /**
     * 将版本文件内容入库
     * @param data
     * @throws SQLException
     */
    public static void insertSVNDate(Map<String, String> data) throws SQLException {
        if (IsEmptyUtils.isEmpty(data.get("methodCodes"))) {
            return;
        }
        // 正式内容入库之前,先查之前是否存在,不存在就直接入库,存在则更新库中数据
        String sql = "insert into svn_data (appkey,methods,content,newContent,methodCodes,dataType,assignMethods,dataId) values (?,?,?,?,?,?,?,?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, data.get("appkey"));
        ps.setString(2, data.get("methods"));
        ps.setString(3, data.get("content"));
        ps.setString(4, data.get("newContent"));
        ps.setString(5, data.get("methodCodes"));
        ps.setString(6, data.get("dataType"));
        ps.setString(7, data.get("assignMethods"));
        ps.setString(8, data.get("dataId"));
        ps.execute();
    }

    /**
     * 将邮件内容入库
     * @param data
     */
    public static void insertEmailData(Map<String, String> data) throws SQLException {
        PreparedStatement ps = null;
        String sql = "insert into email_data (postLog,dataType,checkUrls,versonDate) values(?,?,?,?)";
        if ("1".equals(data.get("dataType"))) {
            String selectSql = "select * from email_data where versonDate = ? and dataType = ?";
            ps = conn.prepareStatement(selectSql);
            ps.setString(1, data.get("versonDate"));
            ps.setString(2, data.get("dataType"));
            ResultSet result = ps.executeQuery();
            // 如果能查到,就去更新
            if (result.next()) {
                sql = "update email_data set postLog = ?,dataType = ?,checkUrls = ? where versonDate = ?";
                data.put("postLog",
                        result.getString("postLog").replaceAll(regex, "") + data.get("postLog").replaceAll(regex, ""));
            }
        }
        ps = conn.prepareStatement(sql);
        ps.setString(1, data.get("postLog").replaceAll(regex, ""));
        ps.setString(2, data.get("dataType"));
        ps.setString(3, data.get("checkUrls"));
        ps.setString(4, data.get("versonDate"));
        ps.execute();
    }

    public static void main(String[] args) {
        List<String> list = new ArrayList<String>();
        list.add("yeah");
        String str = "[yeah]";
        String regex = "(\\[)|(\\])";
        System.out.println(str.replaceAll("(\\[)|(\\])", ""));
        System.out.println(list.toString().replaceAll(regex, ""));
    }
}
