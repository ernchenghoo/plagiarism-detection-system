package models
import java.io.{ByteArrayOutputStream, File, PrintWriter}
import java.sql.{Date, DriverManager, ResultSet, SQLException}

import com.amazonaws.services.s3.iterable.S3Objects
import com.amazonaws.services.s3.model.{ProgressEvent, ProgressListener, PutObjectRequest, S3ObjectSummary}
import models.DetectionManager.{amazonS3Client, bucketName}
import org.apache.commons.io.FileUtils
import org.zeroturnaround.zip.ZipUtil

import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

class Detection extends Database {

  Class.forName(driver)
  var destinationPath = s"${System.getProperty("user.dir")}/public/results"
  val sourcePath = s"${System.getProperty("user.dir")}/public/studentfiles"
  var language = ""
  var detectionDetails: Option[DetectionDetail] = None
  val baseCodeDirectory = "baseCodeDirectory"
  val baseCodeDirectoryPath = s"$sourcePath/$baseCodeDirectory"
  var error: String = _
  var settings: Option[JPlagSettings] = None
  var exitCode: Int = 0
  var readyToExecute = false
  var baseCodeExist = false

  def checkJPlagRunConditions(): String = {

    if (language == "") {
      "Please enter a detection language"
    }
    else if (!filesForDetectionPresent()) {
      "Please upload files to check for plagiarism before running."
    }
    else if (detectionDetails.get.detectionName == "") {
      "Please enter a detection name"
    }
    else {
      readyToExecute = true
      "Pass"
    }
  }

  def runJPlag(): Option[String] = {

    //default settings
    if (settings.isEmpty) {
      settings = Some(new JPlagSettings("50", "30"))
    }

    generateNewDetectionInstance()

    var command = s"java -jar ./jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar -l $language -r $destinationPath/${detectionDetails.get.detectionID} -s $sourcePath -m ${settings.get.minPercentage}%"

    if (baseCodeExist) {
      command = command.concat(s" -bc $baseCodeDirectory")
    }

    val process = processRunner(command)
    println(process._2)
    exitCode = process._1.toString.toInt
    val rawResults = process._2.trim()
    val results = rawResults.split("===")
    val resultData = results(2)
    //errors during detection
    if (exitCode == 1) {
      error = process._2
      Some(error)
    }
    else {
      try {
        //create new detection in database
        val connection = DriverManager.getConnection(url, username, password)
        val statement = connection.createStatement()
        //create a record for detection
        statement.executeUpdate(s"Insert into rawData values('${resultData}', '${detectionDetails.get.detectionID}')")
        statement.executeUpdate(s"Insert into detectionSettings values('${language}', '${settings.get.sensitivity}', '${settings.get.minPercentage}', '${detectionDetails.get.detectionID}')")
      }
      catch {
        case e: SQLException =>
          println(e.printStackTrace())
      }
      //remove all uploaded files
      val uploadedFilesDirectory = new java.io.File(sourcePath)
      val basecodeDirectory = new java.io.File(baseCodeDirectoryPath)
      if (basecodeDirectory.isDirectory) {
        FileUtils.cleanDirectory(basecodeDirectory)
      }
      FileUtils.cleanDirectory(uploadedFilesDirectory)

      None
    }


  }

  def filesForDetectionPresent(): Boolean = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    if (uploadedFilesDirectory.listFiles.isEmpty) {
      false
    }
    else {
      true
    }
  }

  def processRunner(cmd: String): (Any, String, String) = {
    val stdoutStream = new ByteArrayOutputStream
    val stderrStream = new ByteArrayOutputStream
    val stdoutWriter = new PrintWriter(stdoutStream)
    val stderrWriter = new PrintWriter(stderrStream)
    val process = Process(cmd)
    val exitValue = process.!(ProcessLogger(stdoutWriter.println, stderrWriter.println))
    stdoutWriter.close()
    stderrWriter.close()
    (exitValue, stdoutStream.toString, stderrStream.toString)
  }

  def generateNewDetectionInstance() = {
    try {
      //create new detection in database
      val connection = DriverManager.getConnection(url, username, password)
      val statement = connection.createStatement()
      //create a record for detection
      statement.executeUpdate(s"Insert into detection values('${detectionDetails.get.detectionID}', '${detectionDetails.get.detectionDateTime}', " +
        s"'${detectionDetails.get.detectionName}', '${DetectionManager.loggedInUsername}')")
    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    //create new folder to store results generated by JPlag
    new File(s"$destinationPath/${detectionDetails.get.detectionID}").mkdirs()

  }

  def unZipUploadedFiles(): List[UploadedFile] = {
      val uploadedFilesDirectory = new java.io.File(sourcePath)
    for (file <- uploadedFilesDirectory.listFiles()) {
      val extension = file.toString.split("\\.").last
      if (extension == "zip" || extension == "rar") {
        val fileName = file.getName
        ZipUtil.unpack(new File(s"$sourcePath/${fileName}"), new File(s"$sourcePath"))
        if (file.delete()) {
          println(s"${file.getName} deleted")
        }
      }

    }
    //second round of checking
    for (extractedFile <- uploadedFilesDirectory.listFiles()) {
      val extension = extractedFile.toString.split("\\.").last
      if (extension == "zip") {
        val extractedFileName = extractedFile.getName
        ZipUtil.unpack(new File(s"$sourcePath/${extractedFileName}"), new File(s"$sourcePath"))
        if (extractedFile.delete()) {
          println(s"${extractedFile.getName} deleted")
        }
      }
    }

    val uploadedFilesName = new ListBuffer[UploadedFile]()
    for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
      if (uploadedFile.isDirectory) {
        for (innerFile <- uploadedFile.listFiles) {
          val extension = innerFile.toString.split("\\.").last
          if (extension != "py" || extension == "java") {
            innerFile.delete

          }
        }
      }
      uploadedFilesName.append(new UploadedFile(uploadedFile.getName))
    }

    getUploadedFiles()
  }

  def emptyS3StudentFiles(): Unit = {
    S3Objects.inBucket(amazonS3Client, bucketName).forEach((objectSummary: S3ObjectSummary) => {
      println(objectSummary.getKey)
      if (!objectSummary.getKey.contains("baseCodeDirectory")) {
        amazonS3Client.deleteObject(bucketName, objectSummary.getKey)
      }
    })
  }

  def emptyS3BaseCodeDirectory(): Unit = {
    amazonS3Client.deleteObject(bucketName, "baseCodeDirectory")
  }

  def uploadBaseCodeFileToS3(): Unit = {
    val uploadedFilesDirectory = new java.io.File(baseCodeDirectoryPath)
    for (file <- uploadedFilesDirectory.listFiles()) {
      val request: PutObjectRequest = new PutObjectRequest(bucketName, uploadedFilesDirectory.getName + "/" + file.getName, file)
      request.setProgressListener(new ProgressListener() {
        def progressChanged(progressEvent: ProgressEvent): Unit = {
          println("Transferred bytes: " + progressEvent.getBytesTransfered)
        }
      })
      amazonS3Client.putObject(request)
    }
  }

  def uploadStudentFilesToS3 (file: File): Unit = {
    if (file.isDirectory) {
      for (innerFile <- file.listFiles) {
        if (innerFile.isDirectory) {
          for (innerFile2 <- innerFile.listFiles) {
            val request: PutObjectRequest = new PutObjectRequest(bucketName, file.getName + "/" + innerFile2.getName, innerFile2)
            request.setProgressListener(new ProgressListener() {
              def progressChanged(progressEvent: ProgressEvent): Unit = {
                println("Transferred bytes: " + progressEvent.getBytesTransfered)
              }
            })
            amazonS3Client.putObject(request)

          }
        }
        else {
          val request: PutObjectRequest = new PutObjectRequest(bucketName, file.getName + "/" + innerFile.getName, innerFile)
          request.setProgressListener(new ProgressListener() {
            def progressChanged(progressEvent: ProgressEvent): Unit = {
              println("Transferred bytes: " + progressEvent.getBytesTransfered)
            }
          })
          amazonS3Client.putObject(request)
        }

      }
    }
  }

  def clearUploadedFiles(): String = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
      if (uploadedFile.getName != "dummyfile.txt") {
        println("File path: " + uploadedFile.getAbsolutePath)
        if (uploadedFile.isDirectory) {
          FileUtils.deleteDirectory(uploadedFile)
        }
        else if (uploadedFile.isFile) {
          uploadedFile.delete()
        }
      }
    }
    "Success"
  }

  def deleteSingleUploadedFile(fileName: String): String = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    S3Objects.inBucket(amazonS3Client, bucketName).forEach((objectSummary: S3ObjectSummary) => {
      println(objectSummary.getKey)
      if (objectSummary.getKey.contains(fileName + "/")) {
        amazonS3Client.deleteObject(bucketName, objectSummary.getKey)
      }
    })
    for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
      if (uploadedFile.getName == fileName) {
        if (uploadedFile.isDirectory) {
          FileUtils.deleteDirectory(uploadedFile)
        }
        else if (uploadedFile.isFile) {
          uploadedFile.delete()
        }
      }
    }
    "Success"
  }

  def getUploadedFiles(): List[UploadedFile] = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    val uploadedFilesName = new ListBuffer[UploadedFile]()
    for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
      if (uploadedFile.getName != "baseCodeDirectory" && uploadedFile.getName != "dummyfile.txt") {
        uploadedFilesName.append(new UploadedFile(uploadedFile.getName))
      }
    }
    uploadedFilesName.toList
  }

  def getUploadedBaseFile(): String = {
    val uploadedFilesDirectory = new java.io.File(baseCodeDirectoryPath)
    if (!uploadedFilesDirectory.isDirectory) {
      new File(s"$sourcePath/${baseCodeDirectory}").mkdirs()
    }
    if (uploadedFilesDirectory.listFiles.nonEmpty) {
      var uploadedBaseFileName = uploadedFilesDirectory.listFiles().head
      uploadedBaseFileName.getName
    }
    else {
      "None"
    }
  }
}
