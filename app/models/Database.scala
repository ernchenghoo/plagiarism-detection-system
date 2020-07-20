package models

import java.sql.{Connection, DriverManager, SQLException}

trait Database {
  val url = "jdbc:mysql://us-cdbr-east-05.cleardb.net/heroku_11c4949e6e9284f?reconnect=true"
  val driver = "com.mysql.cj.jdbc.Driver"
  val username = "baed0b2928ff17"
  val password = "d5f285a1d98c331"
//  val url = "jdbc:mysql://localhost:3306/fyp?useTimezone=true&serverTimezone=UTC"
//  val driver = "com.mysql.cj.jdbc.Driver"
//  val username = "root"
//  val password = "password"
//  var connection: Connection = DriverManager.getConnection(url, username, password)
}

object Database extends Database {

  def testDatabase(): Unit = {
    try {
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
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
