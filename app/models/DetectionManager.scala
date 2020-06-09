package models

import java.io.File
import java.sql.{DriverManager, SQLException}
import java.util.{Calendar, UUID}

import scala.jdk.CollectionConverters._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object DetectionManager extends Database {

  var loggedInUsername = ""
  val runningDetections: ListBuffer[Detection] = ListBuffer[Detection]()
  var currentDetection: Option[Detection] = None
  var detectionResult: Option[DetectionResult] = None

  def validateLoginCredentials(loginUsername: String, loginPassword: String): String = {
    println(username)
    val connection = DriverManager.getConnection(url, username, password)
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
    val connection = DriverManager.getConnection(url, username, password)
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

  def setDetectionDetail(detectionLanguage: String, detectionName: String): Unit = {
    currentDetection.get.language = detectionLanguage
    currentDetection.get.detectionDetails = Some(new DetectionDetail(detectionName, UUID.randomUUID().toString, Calendar.getInstance().getTime.toString))
  }

  def getPastDetectionList: List[DetectionDetail] = {
    val connection = DriverManager.getConnection(url, username, password)
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

  def fetchResultFromDB (detectionID: String): DetectionResult = {

    val connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    var result: Option[DetectionResult] = None
    try {
      val queryResult = statement.executeQuery("select * from detection " +
        "inner join rawdata on detection.detectionid = rawdata.detectionid " +
        "inner join detectionsettings on detection.detectionid = detectionsettings.detectionid " +
        "inner join account on detection.accountUsername = account.username " +
        s"where detection.detectionID = '${detectionID}' and detection.accountUsername = account.username;")

      var minPercentage = ""
      var sensitivity = ""
      var resultRawData = ""

      while (queryResult.next) {
        resultRawData = queryResult.getString("data")
        minPercentage = queryResult.getString("minPercentage")
        sensitivity = queryResult.getString("sensitivity")
      }
      val settings = new JPlagSettings(sensitivity, minPercentage)
      println(s"Sensitivity: ${settings.sensitivity}")
      println("Result raw data")
      println(resultRawData)
      result = Some(processResults(resultRawData, settings, detectionID))
    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    connection.close()
    result.get
  }

  def processResults(result: String, settings: JPlagSettings, detectionID: String): DetectionResult = {
    val resultData = result.split("&").map(_.trim())
    val unplagiarisedGroup = new ListBuffer[StudentFilePairs]
    val plagiarisedGroup = new ListBuffer[PotentialPlagiarismGroup]

    var counter = 0

    while (counter+1 < resultData.length) {
      //for every 4 in resultData:
      //  Index 0: Student A name
      //  Index 1: Student B name
      //  Index 2: File pair percentage
      //  Index 3: File pair match index
      if ((counter+1) %4 != 0) {
        //get each code file pair comparisons
        val studentCodeFilePairs = getCodeFilePairs(resultData(counter+3).toString, detectionID)
        val highToken = studentCodeFilePairs.find(pairs => pairs.tokenNum >= settings.sensitivity.toInt)
        //only matches that have similarity higher than minPercentage will be saved
        if (resultData(counter+2).toDouble >= settings.minPercentage.toInt) {
          //high chance to be plagiarising
          if (highToken.isDefined) {
            // if plagiarism group already available, check and see if token matches
            if (plagiarisedGroup.nonEmpty) {
              var addedIntoGroup = false
              // loop to check if token matches, if yes assume they are in the same plagiarism group
              for (group <- plagiarisedGroup) {
                //token matches, add into same group
                if (group.tokenNo == highToken.get.tokenNum) {
                  group.studentPairs += new StudentFilePairs(resultData(counter).toString, resultData(counter+1).toString, BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                    resultData(counter+3).toString, studentCodeFilePairs)
                  addedIntoGroup = true
                }
              }
              // boolean check failed, token is not similar to any groups currently available, create a new group
              if (!addedIntoGroup) {
                val studentPair = new StudentFilePairs(resultData(counter).toString, resultData(counter+1).toString, BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                  resultData(counter+3).toString, studentCodeFilePairs)
                plagiarisedGroup += new PotentialPlagiarismGroup(new ListBuffer[StudentFilePairs](), UUID.randomUUID().toString, highToken.get.tokenNum)
                plagiarisedGroup.last.studentPairs += studentPair
              }
            }
            else {
              val studentPair = new StudentFilePairs(resultData(counter).toString, resultData(counter+1).toString, BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                resultData(counter+3).toString, studentCodeFilePairs)
              plagiarisedGroup += new PotentialPlagiarismGroup(new ListBuffer[StudentFilePairs](), UUID.randomUUID().toString, highToken.get.tokenNum)
              plagiarisedGroup.last.studentPairs += studentPair
            }
          }
          //unplagiarised student pair
          else {
            unplagiarisedGroup += new StudentFilePairs(resultData(counter).toString, resultData(counter+1).toString, BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              resultData(counter+3).toString, studentCodeFilePairs)
          }
        }
      }
      counter += 4
    }


    val unPlagiarisedPairs = unplagiarisedGroup.toList.sortBy(_.percentage)(Ordering[Double].reverse)
    val plagiarismGroup = plagiarisedGroup.toList.sortBy(_.tokenNo)(Ordering[Int].reverse)
    val processedResults = new DetectionResult(unPlagiarisedPairs, plagiarismGroup)
    detectionResult = Some(processedResults)
    processedResults
  }

  def getCodeFilePairs (matchIndex: String, resultFolderName: String): List[CodeFilePair] = {
    println("Match index")
    println(matchIndex)
    val connection = DriverManager.getConnection(url, username, password)

    //get the file paths of the matches generated by JPlag
    val studentAMatchFile = s"./public/results/$resultFolderName/match" + matchIndex + "-0.html"
    val studentBMatchFile = s"./public/results/$resultFolderName/match" + matchIndex + "-1.html"
    val comparisonTable = s"./public/results/$resultFolderName/match" + matchIndex + "-top.html"

    //generate a JSoup doc to extract the contents from static HTML generated
    val studentADoc: Document = Jsoup.parse(new File(studentAMatchFile), "utf-8")
    val studentBDoc: Document = Jsoup.parse(new File(studentBMatchFile), "utf-8")
    val comparisonTableDoc: Document = Jsoup.parse(new File(comparisonTable), "utf-8")

    //remove unnecessary elements
    studentADoc.getElementsByTag("img").remove()
    studentBDoc.getElementsByTag("img").remove()
    studentADoc.getElementsByTag("a").remove()
    studentBDoc.getElementsByTag("a").remove()
    studentADoc.getElementsByTag("div").remove()
    studentBDoc.getElementsByTag("div").remove()

    //create an empty list buffer to add each separate files from a student to identify the total number of files that has been detected
    val studentACodes = new ListBuffer[String]()
    val studentBCodes = new ListBuffer[String]()
    val studentAtitles = new ListBuffer[String]()
    val studentBtitles = new ListBuffer[String]()
    val comparisonLines = new ListBuffer[String]()
    val comparisonTokens = new ListBuffer[String]()
    val tableHeaders = new ListBuffer[String]()


    studentADoc.getElementsByTag("pre").asScala.foreach(studentACodes += _.outerHtml())
    studentBDoc.getElementsByTag("pre").asScala.foreach(studentBCodes += _.outerHtml())
    studentADoc.getElementsByTag("h3").asScala.foreach(studentAtitles += _.text())
    studentBDoc.getElementsByTag("h3").asScala.foreach(studentBtitles += _.text())
    comparisonTableDoc.select("a").asScala.foreach(comparisonLines += _.html())
    comparisonTableDoc.getElementsByTag("font").asScala.foreach(comparisonTokens += _.html())
    comparisonTableDoc.getElementsByTag("th").asScala.foreach(tableHeaders += _.html())

    val studentFileA = new ListBuffer[CodeFile]()
    val studentFileB = new ListBuffer[CodeFile]()

    for (index <- studentACodes.indices) {
      studentFileA += new CodeFile(studentAtitles(index), studentACodes(index))
    }
    for (index <- studentBCodes.indices) {
      studentFileB += new CodeFile(studentBtitles(index), studentBCodes(index))
    }

    val codeFilePairs = new ListBuffer[CodeFilePair]()

    var counter = 0
    while (counter < comparisonLines.length) {
      var codeFileA: Option[CodeFile] = None
      var codeFileB: Option[CodeFile] = None
      var token: Option[Int] = None

      for (element <- studentFileA) {
        //for titles in comparisonLines, counter is file on the left, counter+1 is file on the right
        if (comparisonLines(counter).contains(element.fileName)) {
          codeFileA = Some(element)
        }
      }
      for (element <- studentFileB) {
        if (comparisonLines(counter+1).contains(element.fileName)) {
          codeFileB = Some(element)
        }
      }
      token = Some(comparisonTokens(counter+1).toInt)
      connection.close()
      val codeFilePair = new CodeFilePair(codeFileA.get, codeFileB.get, token.get)

      codeFilePairs += codeFilePair
      counter += 2
    }

    codeFilePairs.toList
  }

}
