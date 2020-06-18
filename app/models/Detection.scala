package models
import java.io.{ByteArrayOutputStream, File, PrintWriter}
import java.sql.{Date, DriverManager, ResultSet, SQLException}
import java.util.concurrent.{ExecutorService, Executors}

import com.amazonaws.services.s3.iterable.S3Objects
import com.amazonaws.services.s3.model.{ProgressEvent, ProgressListener, PutObjectRequest, S3ObjectSummary}
import models.DetectionManager.{amazonS3Client, bucketName}
import org.apache.commons.io.FileUtils
import org.zeroturnaround.zip.ZipUtil

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessLogger}

class Detection (val detectionID: String) extends Database with DetectionInfo{

  Class.forName(driver)
  var language = ""
  var detectionDetails: Option[DetectionDetail] = None
  val sourcePath = s"$defaultSourcePath/$detectionID"
  val baseCodeDirectoryPath = s"$sourcePath/$baseCodeDirectory"
  var error: String = _
  var settings: Option[JPlagSettings] = None
  var exitCode: Int = 0
  var readyToExecute = false
  var baseCodeExist = false

  implicit val ec: ExecutionContext = new ExecutionContext {
    val threadPool: ExecutorService = Executors.newFixedThreadPool(25)

    def execute(runnable: Runnable) {
      threadPool.submit(runnable)
    }

    def reportFailure(t: Throwable) {}
  }


  def checkJPlagRunConditions(): String = {
    println("Validating detection")
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
      println("JPlag ready to run!")
      "Pass"
    }
  }

  def runJPlag(): Option[String] = {

    //default settings
    if (settings.isEmpty) {
      println("Using default settings")
      settings = Some(new JPlagSettings("50", "30"))
    }

    var command = s"java -jar jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar -l $language -r $destinationPath/${detectionID} -s $sourcePath -m ${settings.get.minPercentage}%"

    if (baseCodeExist) {
      println("Base code detected")
      command = command.concat(s" -bc $baseCodeDirectory")
    }
    else {
      println("No base code detected")
      val baseCodeDirectory = new java.io.File(baseCodeDirectoryPath)
      baseCodeDirectory.delete()
    }

    generateNewDetectionInstance()
    val process = processRunner(command)
    exitCode = process._1.toString.toInt
    println("Exit code: " + exitCode)
    println("")
    //errors during detection

    //errors during detection
    if (exitCode == 1) {
      error = process._2
      println("Error" + error)
      Some(error)
    }
    else {
      println("Extracting JPlag results")
      val rawResults = process._2.trim()
      val results = rawResults.split("===")
      val resultData = results(2)
      try {
        //create new detection in database
        println("Connecting to database")
        connection = DriverManager.getConnection(url, username, password)
        val statement = connection.createStatement()
        //create a record for detection
        println("Inserting into database")
        statement.executeUpdate(s"Insert into rawData values('${resultData}', '${detectionID}')")
        statement.executeUpdate(s"Insert into detectionSettings values('${language}', '${settings.get.sensitivity}', '${settings.get.minPercentage}', '${detectionID}')")
        println("Database insertion complete")
      }
      catch {
        case e: SQLException =>
          println(e.printStackTrace())
      }
      //remove all uploaded files
      clearUploadedFiles()
      println("Detection complete")
      None
    }
    None

  }

  def filesForDetectionPresent(): Boolean = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    if (uploadedFilesDirectory.listFiles.length < 3) {
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
    var exitValue = 0
    try {
      val process = Process(cmd)
      println("Running JPlag")
      exitValue = process.!(ProcessLogger(stdoutWriter.println, stderrWriter.println))
      println("JPlag run complete")
    }
    catch {
      case e: Exception => println(e.printStackTrace())
    }

    stdoutWriter.close()
    stderrWriter.close()
    (exitValue, stdoutStream.toString, stderrStream.toString)
  }

  def generateNewDetectionInstance(): Boolean = {
    println("Generating new detection instance")
    try {
      //create new detection in database
      connection = DriverManager.getConnection(url, username, password)
      val statement = connection.createStatement()
      //create a record for detection
      statement.executeUpdate(s"Insert into detection values('${detectionID}', '${detectionDetails.get.detectionDateTime}', " +
        s"'${detectionDetails.get.detectionName}', '${DetectionManager.loggedInUsername}')")
    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    //create new folder to store results generated by JPlag
    new File(s"$destinationPath/${detectionID}").mkdirs()
  }

  def generateDetectionDirectories(): Unit = {
    val studentCodeDirectory = new java.io.File(sourcePath)
    val baseCodeDirectory = new java.io.File(baseCodeDirectoryPath)
  }

  def unZipUploadedFiles(): Option[List[UploadedFile]] = {
    println("Unzipping files:\n")
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    for (file <- uploadedFilesDirectory.listFiles()) {
      val extension = file.toString.split("\\.").last
      if (extension == "zip" || extension == "rar") {
        val fileName = file.getName
        println("Unzipping " + file.getName)
        ZipUtil.unpack(new File(s"$sourcePath/${fileName}"), new File(s"$sourcePath"))
        file.delete
      }
    }
    println("\nSecond round of unzipping files:\n")
    //second round of checking
    for (extractedFile <- uploadedFilesDirectory.listFiles()) {
      val extension = extractedFile.toString.split("\\.").last
      if (extension == "zip") {
        val extractedFileName = extractedFile.getName
        ZipUtil.unpack(new File(s"$sourcePath/${extractedFileName}"), new File(s"$sourcePath"))
        extractedFile.delete
      }
    }
    println("\nDeleting non-source code files:\n")
    val uploadedFilesName = new ListBuffer[UploadedFile]()
    for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
      if (uploadedFile.isDirectory) {
        for (innerFile <- uploadedFile.listFiles) {
          val extension = innerFile.toString.split("\\.").last
          if (extension != "py" || extension == "java") {
            innerFile.delete
            println(innerFile.getName + " deleted")
          }
        }
      }
      uploadedFilesName.append(new UploadedFile(uploadedFile.getName))
    }
    println("Unzip complete")
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
    println("\nClearing uploaded files\n")
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
      if (uploadedFile.getName != "dummyfile.txt") {
        if (uploadedFile.isDirectory) {
          println(uploadedFile.getName + " deleted")
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

  def getUploadedFiles(): Option[List[UploadedFile]] = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    if (uploadedFilesDirectory.exists) {
      val uploadedFilesName = new ListBuffer[UploadedFile]()
      for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
        if (uploadedFile.getName != "baseCodeDirectory" && uploadedFile.getName != "dummyfile.txt") {
          uploadedFilesName.append(new UploadedFile(uploadedFile.getName))
        }
      }
      Some(uploadedFilesName.toList)
    }
    else {

      None
    }
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
