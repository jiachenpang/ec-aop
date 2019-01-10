package org.n3r.acc;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

// http://jeffrick.com/2010/03/23/bulk-insert-into-a-mysql-database/
public class MySqlBulkInsert {
    public static void main(String[] args) {
        DBase db = new DBase();
        Connection conn = db.connect(
                "jdbc:mysql://localhost:3306/test", "root", "xule");
        db.importData(conn, args[0]);
    }

}

class DBase {
    public Connection connect(String db_connect_str, String db_userid, String db_password) {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();

            return DriverManager.getConnection(db_connect_str, db_userid, db_password);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void importData(Connection conn, String filename) {
        Statement stmt;
        String query;

        try {
            stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);

            query = "LOAD DATA INFILE '" + filename + "' INTO TABLE testtable (text,price)" +
                    "FIELDS TERMINATED BY ','" +
                    "ENCLOSED BY ''" +
                    "LINES TERMINATED BY '\\n';";

            stmt.executeUpdate(query);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//If you want to import a CSV file, you can use the following query:

    //       query = "LOAD DATA INFILE '"+filename+"' INTO TABLE testtable  FIELDS
    //     TERMINATED BY ',' (text,price)";

    public void bulkInsert(Connection conn, Long personId,
                           HashMap<String, String> hashOfNameValues)
            throws SQLException {

        // First create a statement off the connection and turn off unique checks and key creation
        com.mysql.jdbc.Statement statement = (com.mysql.jdbc.Statement) conn.createStatement();
        statement.execute("SET UNIQUE_CHECKS=0; ");
        statement.execute("ALTER TABLE real_big_table DISABLE KEYS");

        /*
        String esquel = " LOAD DATA LOCAL INFILE '" + path +
                "' INTO TABLE recommendations " +
                " FIELDS TERMINATED BY \',\' ENCLOSED BY \'\"'" +
                " LINES TERMINATED BY \'\\n\'";
        */
        // Define the query we are going to execute
        String statementText = "LOAD DATA LOCAL INFILE 'file.txt' " +
                "INTO TABLE real_big_table " +
                "(name, value) " +
                " SET owner_id = " + personId + ", " +
                " version = 0; ";

        // Create StringBuilder to String that will become stream
        StringBuilder builder = new StringBuilder();

        // Iterate over map and create tab-text string
        for (Map.Entry<String, String> entry : hashOfNameValues.entrySet()) {
            builder.append(entry.getKey());
            builder.append('\t');
            builder.append(entry.getValue());
            builder.append('\n');
        }

        // Create stream from String Builder
        InputStream is = IOUtils.toInputStream(builder.toString());

        // Setup our input stream as the source for the local infile
        statement.setLocalInfileInputStream(is);

        // Execute the load infile
        statement.execute(statementText);

        // Turn the checks back on
        statement.execute("ALTER TABLE affinity ENABLE KEYS");
        statement.execute("SET UNIQUE_CHECKS=1; ");
    }
}
