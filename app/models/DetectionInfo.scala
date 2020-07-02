package models

trait DetectionInfo {
  var defaultDestinationPath = s"${System.getProperty("user.dir")}/public/results"
  val defaultSourcePath = s"${System.getProperty("user.dir")}/public/studentfiles"
  val baseCodeDirectory = "baseCodeDirectory"
}
