package models

import java.io.File
import java.sql.{DriverManager, SQLException}
import java.text.SimpleDateFormat
import java.util
import java.util.{Calendar, TimeZone, UUID}

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.transfer.TransferManager
import org.apache.commons.io.FileUtils

import scala.jdk.CollectionConverters._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


object DetectionManager extends Database with AmazonS3 with DetectionInfo {
  var loggedInUsername = ""
  val runningDetections: ListBuffer[Detection] = ListBuffer[Detection]()
  var currentDetection: Option[Detection] = None
  var detectionResult: Option[DetectionResult] = None

  def validateLoginCredentials(loginUsername: String, loginPassword: String): String = {
    connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    var loggedIn = false
    try {
      var validity = ""
      val queryResult = statement.executeQuery(s"SELECT IF ((SELECT password FROM account WHERE username = '$loginUsername') = SHA1('$loginPassword'), 'true', 'false') AS valid;")
      while (queryResult.next) {
        validity = queryResult.getString("valid")
      }

      if (validity == "true") {
        val accountIDQuery = statement.executeQuery(s"select username from account where username = '$loginUsername';")
        while (accountIDQuery.next) {
          loggedInUsername = accountIDQuery.getString("username")
          loggedIn = true
        }
      }
    }
    catch {
      case e: SQLException =>
       e.printStackTrace()
    }
    connection.close()
    if (loggedIn) {
      "Pass"
    }
    else {
      "Username or password error"
    }
  }

  def registerAccount(registrationUsername: String, registrationPassword: String): String = {
    connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    var status = "Pass"

    try {
      statement.executeUpdate(s"insert into account values('$registrationUsername', SHA1('$registrationPassword'));")
    }
    catch {
      case e: SQLException =>
        e.printStackTrace()
        status = "Fail"
    }
    connection.close()
    loggedInUsername = registrationUsername
    status
  }

  def getRunningDetectionDetails: List[DetectionDetail] = {
    val runningDetectionDetails = mutable.ListBuffer[DetectionDetail]()
    for (detection <- DetectionManager.runningDetections) {
      runningDetectionDetails += detection.detectionDetails.get
    }
    runningDetectionDetails.toList
  }

  def downloadResultFilesFromS3 (detectionID: String): Unit = {
    val transferManager = new TransferManager(yourAWSCredentials)
    val file = new java.io.File(s"$defaultDestinationPath")
    amazonS3Client.getObject(new GetObjectRequest(bucketName, s"ernchenghoo/$detectionID"), file)
//    val download = transferManager.downloadDirectory(bucketName, s"${DetectionManager.loggedInUsername}/$detectionID", file)
//    while (!download.isDone) {
//      println("Download process: " + download.getProgress.getPercentTransfered + "%")
//    }
  }

  def setDetectionDetail(detectionLanguage: String, detectionName: String): Unit = {
    println("Settings detection details")
    currentDetection.get.language = detectionLanguage
    val currentTime = Calendar.getInstance()
    currentTime.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"))
    val year = currentTime.get(Calendar.YEAR)
    val month = currentTime.get(Calendar.MONTH) + 1
    val day = currentTime.get(Calendar.DAY_OF_MONTH)
    val hour = currentTime.get(Calendar.HOUR_OF_DAY)
    val minute = currentTime.get(Calendar.MINUTE)
    var minuteString = minute.toString
    if (minute < 10) {
      minuteString = "0" + minute.toString
    }
    var formattedDateTimeString = ""
    if (hour >= 12) {
      formattedDateTimeString = s"${hour-12}:${minuteString}pm, $day-$month-$year"
    }
    else {
      formattedDateTimeString = s"$hour:${minuteString}am, $day-$month-$year"
    }
    currentDetection.get.detectionDetails = Some(new DetectionDetail(detectionName, currentDetection.get.detectionID, formattedDateTimeString))
  }

  def generateNewDetectionInstance(): Unit = {
    currentDetection = Some(new Detection(UUID.randomUUID().toString))
    currentDetection.get.generateDetectionDirectories()
    println("Generate new detection instance complete")
  }

  def getPastDetectionList: List[DetectionDetail] = {
    connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    var resultList = new ListBuffer[DetectionDetail]()
    try {
      val queryResult = statement.executeQuery(s"select * from detection where accountUsername = '${DetectionManager.loggedInUsername}'")

      var detectionName = ""
      var detectionID = ""
      var detectionDateTime = ""

      while (queryResult.next) {
        detectionName = queryResult.getString("detectionName")
        detectionID = queryResult.getString("detectionID")
        detectionDateTime = queryResult.getString("detectionDateTime")
        var detectionDetail = new DetectionDetail(detectionName, detectionID, detectionDateTime)
        resultList += detectionDetail
      }

    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    connection.close()
    println("Size: " + resultList.size)
    resultList.toList
  }

  def clearAllStudentFiles(): Unit = {
    val uploadedFilesDirectory = new java.io.File(defaultSourcePath)
    for (studentFiles <- uploadedFilesDirectory.listFiles()) {
      if (studentFiles.getName != "dummyfile.txt") {
        if (studentFiles.isDirectory) {
          FileUtils.deleteDirectory(studentFiles)
        }
      }
    }
  }

  def getResultsFromDB (detectionID: String): DetectionResult = {
    connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    println(detectionID)
    try {
      val studentPairQuery = statement.executeQuery("select * from codefile " +
        "inner join studentfilepair on codefile.studentfilepairid = studentfilepair.studentfilepairid " +
        s"where studentfilepair.detectionID = '$detectionID' order by codefile.studentFilePairID;")


      var lastStudentPairA = ""
      var lastStudentPairB = ""
      var lastStudentPairPercentage = 0.0
      var lastStudentPairID = ""
      val codeFileListA = new ListBuffer[CodeFile]()
      val codeFileListB = new ListBuffer[CodeFile]()
      val codeTokenList = new ListBuffer[CodeToken]()
      var studentPairList = new ListBuffer[StudentFilePair]()


      while (studentPairQuery.next) {
        val currentStudentPairID = studentPairQuery.getString("studentFilePairID")
        if (lastStudentPairID != "") {
          if (currentStudentPairID != lastStudentPairID) {
            studentPairList += new StudentFilePair(lastStudentPairA, lastStudentPairB, BigDecimal(lastStudentPairPercentage).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              lastStudentPairID, codeFileListA.toList, codeFileListB.toList, codeTokenList.toList)
            codeFileListA.clear
            codeFileListB.clear
          }
        }
        lastStudentPairA = studentPairQuery.getString("studentA")
        lastStudentPairB = studentPairQuery.getString("studentB")
        lastStudentPairPercentage = studentPairQuery.getFloat("percentage").toDouble
        lastStudentPairID = studentPairQuery.getString("studentFilePairID")

        if (studentPairQuery.getString("student") == "A") {
          codeFileListA += new CodeFile(studentPairQuery.getString("fileName"), studentPairQuery.getString("fileCode"))
        }
        else {
          codeFileListB += new CodeFile(studentPairQuery.getString("fileName"), studentPairQuery.getString("fileCode"))
        }
      }
      studentPairList += new StudentFilePair(lastStudentPairA, lastStudentPairB, BigDecimal(lastStudentPairPercentage).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        lastStudentPairID, codeFileListA.toList, codeFileListB.toList, codeTokenList.toList)

      for (pair <- studentPairList) {
        val tokenList = new ListBuffer[CodeToken]()
        val codeTokensQuery = statement.executeQuery(s"select * from codetokens where codetokens.studentfilepairid = '${pair.studentFilePairID}'")
        while (codeTokensQuery.next) {
          tokenList += new CodeToken(codeTokensQuery.getString("tokenNum").toInt, codeTokensQuery.getString("lineNumA"),
            codeTokensQuery.getString("lineNumB"))
        }
        pair.tokenList = tokenList.toList
      }

      val potentialPlagiarismGroupList = new ListBuffer[PotentialPlagiarismGroup]()
      val studentPairToRemove = new ListBuffer[StudentFilePair]()
      var studentPairAdded = false
      //create potentialPlagiarismGroups
      val groupsResultSet = statement.executeQuery(s"select * from potentialplagiarismgroup where detectionID = '$detectionID';")
      while (groupsResultSet.next) {
        potentialPlagiarismGroupList += new PotentialPlagiarismGroup(new ListBuffer[StudentFilePair], groupsResultSet.getString("groupID"))
      }

      for (studentFilePair <- studentPairList) {
        val queryPotentialPlagiarismGroup = statement.executeQuery("select * from groupstudent " +
          "inner join potentialplagiarismgroup on groupstudent.groupid = potentialplagiarismgroup.groupid " +
          s"where groupstudent.studentpairid = '${studentFilePair.studentFilePairID}';")
        while (queryPotentialPlagiarismGroup.next) {
          for (group <- potentialPlagiarismGroupList) {
            if (group.groupID == queryPotentialPlagiarismGroup.getString("groupID")) {
              group.studentPairs += studentFilePair
              studentPairList = studentPairList.filter(_.studentFilePairID != studentFilePair.studentFilePairID)
            }
          }
        }
      }


      val detectionQuery = statement.executeQuery(s"select detectionMode from detection where detectionID = '$detectionID'")
      var detectionMode = ""
      while (detectionQuery.next) {
        detectionMode = detectionQuery.getString("detectionMode")
      }

      detectionResult = Some(new DetectionResult(studentPairList.toList.sortBy(- _.percentage), potentialPlagiarismGroupList.toList.sortBy(- _.averageStudentSimilarityPercentage),
        detectionMode))

    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    connection.close()
    detectionResult.get
  }



  def afterDetectionActions(detectionID: String): Unit = {
    var sourcePath = ""
    //remove from running detection list
    for (detection <- runningDetections) {
      if (detection.detectionID == detectionID) {
        sourcePath = detection.sourcePath
        runningDetections.remove(runningDetections.indexOf(detection))
      }
    }

    //remove uploaded code files
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    FileUtils.deleteDirectory(uploadedFilesDirectory)

    //remove JPlag generated HTML results files
    val resultFilesDirectory = new java.io.File(s"$defaultDestinationPath/$detectionID")
    FileUtils.deleteDirectory(resultFilesDirectory)
  }







}
