package models

import java.sql.{Connection, DriverManager, SQLException}

trait Database {
  val url = "jdbc:mysql://sq65ur5a5bj7flas.cbetxkdyhwsb.us-east-1.rds.amazonaws.com:3306/hxam54z8bn8akfgd?useTimezone=true&serverTimezone=UTC"
  val driver = "com.mysql.cj.jdbc.Driver"
  val username = "xlgi69op5dtxcv6a"
  val password = "ll2mnxm6mr45b87q"
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
