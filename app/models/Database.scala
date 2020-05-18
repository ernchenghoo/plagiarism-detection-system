package models

import java.sql.{Connection, DriverManager, SQLException}

trait Database {
  val url = "jdbc:mysql://localhost:3306/fyp?useTimezone=true&serverTimezone=UTC"
  val driver = "com.mysql.cj.jdbc.Driver"
  val username = "erncheng"
  val password = "password"
  var connection: Connection = _
}

object Database extends Database {
  def testDatabase(): Unit = {
    try {
      Class.forName(driver)
      val connection = DriverManager.getConnection(url, username, password)
      val statement = connection.createStatement()
      val result  = statement.executeQuery("select * from codefile")

      println("SQL testing")
      while (result.next) {

        println(result.getString("filename"))
        println(result.getString("sourcecode"))
      }
    }
    catch {
      case e: SQLException => println(e)
    }
  }

}
