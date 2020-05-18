package models
import java.io.{ByteArrayOutputStream, File, PrintWriter}
import java.sql.{Date, DriverManager, ResultSet, SQLException}
import java.util.{Base64, Calendar, UUID}
import org.apache.commons.io.FileUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.zeroturnaround.zip.ZipUtil
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.sys.process.{Process, ProcessLogger}

object JPlag extends Database {

  Class.forName(driver)

  var destinationPath = "./public/results"
  val sourcePath = "./studentFiles"
  var language = ""
  val baseCodeDirectory = "baseCodeDirectory"
  val baseCodeDirectoryPath = s"$sourcePath/$baseCodeDirectory"
  var error: String = _
  var settings: Option[JPlagSettings] = None
  var exitCode: Int = 0
  var detectionResult: Option[DetectionResult] = None

  def checkJPlagRunConditions: String = {
    if (!filesForDetectionPresent()) {
      "Please upload files to check for plagiarism before running."
    }
    else if (language == "none" || settings.isEmpty) {
      "Please select the language of your detection"
    }
    else {
      "Pass"
    }
  }

  def runJPlag(): Future[Option[String]] = Future {
    println("Min percentage: " + settings.get.minPercentage)

    val currentDetectionID = generateNewDetectionInstance()

    var command = s"java -jar ./jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar -l $language -r $destinationPath/$currentDetectionID -s $sourcePath -m ${settings.get.minPercentage}%"

    if (settings.get.baseCodeExist) {
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
        statement.executeUpdate(s"Insert into rawData values('${resultData}', '${currentDetectionID}')")
        statement.executeUpdate(s"Insert into detectionSettings values('${language}', '${settings.get.sensitivity}', '${settings.get.minPercentage}', '${currentDetectionID}')")
      }
      catch {
        case e: SQLException =>
          println(e.printStackTrace())
      }
      //remove all uploaded files
      val uploadedFilesDirectory = new java.io.File(sourcePath)
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

  def generateNewDetectionInstance(): String = {
    val detectionID = UUID.randomUUID().toString
    try {
      //create new detection in database
      val connection = DriverManager.getConnection(url, username, password)
      val statement = connection.createStatement()
      //create a record for detection
      statement.executeUpdate(s"Insert into detection values('${detectionID}', default)")
    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    //create new folder to store results generated by JPlag
    new File(s"$destinationPath/$detectionID").mkdirs()
    detectionID
  }

  def getPastDetectionList: List[(String, String)] = {
    val connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    var resultList = new ListBuffer[(String, String)]()
    try {
      val queryResult = statement.executeQuery(s"select * from detection")

      while (queryResult.next) {
        resultList += ((queryResult.getString("detectionID"), queryResult.getString("detectionDateTime")))
      }

    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    connection.close()
    resultList.toList
  }

  def fetchResultFromDB (detectionID: String): DetectionResult = {

    val connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    var result: Option[DetectionResult] = None
    try {
      val queryResult = statement.executeQuery(s"select * from detection inner join rawdata on detection.detectionid = rawdata.detectionid where detection.detectionID = '${detectionID}'")

      var dateTime = ""
      var resultRawData = ""

      while (queryResult.next) {
        resultRawData = queryResult.getString("data")
      }
      println("Result raw data")
      println(resultRawData)
      result = Some(processResults(resultRawData, settings.get.minPercentage.toDouble, detectionID))
    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    connection.close()
    result.get
  }

  def processResults(result: String, minPercentage: Double, detectionID: String): DetectionResult = {


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
          val highToken = studentCodeFilePairs.find(pairs => pairs.tokenNum >= settings.get.sensitivity.toInt)
          //only matches that have similarity higher than minPercentage will be saved
          if (resultData(counter+2).toDouble >= minPercentage) {
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
  def unZipUploadedFiles(): Unit = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    for (file <- uploadedFilesDirectory.listFiles()) {
      val extension = file.toString.split("\\.").last

      if (extension == "zip" || extension == "rar") {
        println("Zip file detected")
        val fileName = file.getName
        ZipUtil.unpack(new File(s"./studentFiles/${fileName}"), new File("./studentFiles"))
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
        ZipUtil.unpack(new File(s"./studentFiles/${extractedFileName}"), new File("./studentFiles"))
        if (extractedFile.delete()) {
          println(s"${extractedFile.getName} deleted")
        }
      }
    }
  }


}
