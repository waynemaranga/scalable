package db

import java.sql.{Connection, DriverManager, ResultSet}
import scala.util.{Try, Using}
import java.time.LocalDateTime

object JDBCClient {
  // Load the SQL Server JDBC driver
  Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")

  val connString = sys.env("AZURE_MSSQL_CONN_STRING")

  // Establish a connection
  def getConnection: Try[Connection] = Try {
    DriverManager.getConnection(connString)
  }

  // Retrieve the current timestamp from the database
  def getCurrentTimestamp(): Try[LocalDateTime] = {
    val query = "SELECT CURRENT_TIMESTAMP"
    Using.Manager { use =>
      val conn = use(getConnection.get)
      val stmt = use(conn.createStatement())
      val rs = use(stmt.executeQuery(query))
      if (rs.next()) rs.getTimestamp(1).toLocalDateTime else LocalDateTime.MIN
    }
  }

  // List all tables in the current database
  def listTables(): Try[List[String]] = {
    val query =
      "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'"
    Using.Manager { use =>
      val conn = use(getConnection.get)
      val stmt = use(conn.createStatement())
      val rs = use(stmt.executeQuery(query))
      Iterator
        .continually((rs.next(), rs))
        .takeWhile(_._1)
        .map { case (_, resultSet) => resultSet.getString("TABLE_NAME") }
        .toList
    }
  }

  // List all columns for a given table
  def listColumns(tableName: String): Try[List[String]] = {
    val query =
      s"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '$tableName'"
    Using.Manager { use =>
      val conn = use(getConnection.get)
      val stmt = use(conn.createStatement())
      val rs = use(stmt.executeQuery(query))
      Iterator
        .continually((rs.next(), rs))
        .takeWhile(_._1)
        .map { case (_, resultSet) => resultSet.getString("COLUMN_NAME") }
        .toList
    }
  }

  // Count the number of rows in a given table
  def countRows(tableName: String): Try[Int] = {
    val query = s"SELECT COUNT(*) FROM [$tableName]"
    Using.Manager { use =>
      val conn = use(getConnection.get)
      val stmt = use(conn.createStatement())
      val rs = use(stmt.executeQuery(query))
      if (rs.next()) rs.getInt(1) else 0
    }
  }

//   def countRows(tableName: String): Try[Long] = {
//     val query = s"SELECT COUNT(*) FROM [$tableName]"
//     Using.Manager { use =>
//       val conn = use(getConnection.get)
//       val stmt = use(conn.createStatement())
//       val rs = use(stmt.executeQuery(query))
//       if (rs.next()) rs.getLong(1) else 0L
//     }
//   }

}
