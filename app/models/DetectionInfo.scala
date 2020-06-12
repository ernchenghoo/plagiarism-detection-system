package models

trait DetectionInfo {
  var destinationPath = s"${System.getProperty("user.dir")}/public/results"
  val sourcePath = s"${System.getProperty("user.dir")}/public/studentfiles"
  val baseCodeDirectory = "baseCodeDirectory"
}
