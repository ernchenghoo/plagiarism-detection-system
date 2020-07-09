package models
import java.io.{ByteArrayOutputStream, File, PrintWriter}
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.sql.{Date, DriverManager, ResultSet, SQLException}
import java.util.UUID
import java.util.concurrent.{ExecutorService, Executors}

import scala.jdk.CollectionConverters._
import com.amazonaws.services.s3.iterable.S3Objects
import com.amazonaws.services.s3.model.{GetObjectRequest, ProgressEvent, ProgressListener, PutObjectRequest, S3ObjectSummary}
import models.DetectionManager.{amazonS3Client, bucketName}
import org.apache.commons.io.FileUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.zeroturnaround.zip.ZipUtil

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessLogger}

class Detection (val detectionID: String) extends Database with DetectionInfo with AmazonS3 {

  Class.forName(driver)
  var language = ""
  var detectionDetails: Option[DetectionDetail] = None
  val sourcePath = s"$defaultSourcePath/$detectionID"
  val destinationPath = s"$defaultDestinationPath/$detectionID"
  val baseCodeDirectoryPath = s"$sourcePath/$baseCodeDirectory"
  var error: String = _
  var detectionMode: String = ""
  var settings: Option[JPlagSettings] = None
  var exitCode: Int = 0
  var readyToExecute = false
  var baseCodeExist = false
  var detectionResult: Option[DetectionResult] = None
  var plagiarisedGroup = new ListBuffer[PotentialPlagiarismGroup]

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
    else if (!detectionModeIsFixed()) {
      "Please upload either all files or all directories only, do not upload a combination of both."
    }
    else {
      readyToExecute = true
      println("JPlag ready to run!")
      "Pass"
    }
  }

  def runJPlag(): String = {
    readyToExecute = false
    //default settings
    if (settings.isEmpty) {
      println("Using default settings")
      settings = Some(new JPlagSettings("100", "30"))
    }
    var command = s"java -jar jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar -l $language -r $destinationPath -s $sourcePath -m ${settings.get.minPercentage}%"

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
    println("Exit code: " + exitCode + "\n")


    //errors during detection
    if (exitCode == 1) {
      error = process._2
      println("Error" + error)
      error
    }
    else {
      println("Extracting JPlag results")
      val rawResults = process._2.trim()
      val results = rawResults.split("===")
      val resultData = results(2)
      detectionResult = Some(processResults(resultData, settings.get, detectionID, detectionMode))
      DetectionManager.afterDetectionActions(detectionID)
      "Success"
    }
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
        s"'${detectionDetails.get.detectionName}', '${DetectionManager.loggedInUsername}', '$detectionMode')")
    }
    catch {
      case e: SQLException =>
        println(e.printStackTrace())
    }
    //create new folder to store results generated by JPlag
    new File(s"$destinationPath").mkdirs()
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
      if (extension == "zip") {
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
    println("Unzip complete")
    getUploadedFiles()
  }

  def uploadResultFilesToS3 (detectionID: String): Unit = {
    val file = new java.io.File(destinationPath)
    if (file.isDirectory) {
      for (innerFile <- file.listFiles) {
        println(innerFile.getName)
        val request: PutObjectRequest = new PutObjectRequest(bucketName, DetectionManager.loggedInUsername + "/" + detectionID + "/" + innerFile.getName, innerFile)
        request.setProgressListener(new ProgressListener() {
          def progressChanged(progressEvent: ProgressEvent): Unit = {
            println("Transferred bytes: " + progressEvent.getBytesTransfered)
          }
        })
        amazonS3Client.putObject(request)
      }
    }
    FileUtils.deleteDirectory(file)
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

  def deleteBaseFileUploaded(fileName: String): String = {
    val uploadedFilesDirectory = new java.io.File(baseCodeDirectoryPath)
    for (uploadedFile <- uploadedFilesDirectory.listFiles()) {
      uploadedFile.delete
    }
    baseCodeExist = false
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

  def detectionModeIsFixed(): Boolean = {
    var detectionModeFixed = true
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    for (file <- uploadedFilesDirectory.listFiles) {
      if (file.getName != "baseCodeDirectory") {
        println(file.getName)
        if (detectionMode == "") {
          println("Detection mode empty")
          if (file.isDirectory) {
            println("Detection mode = directory")
            detectionMode = "directory"
          }
          else {
            println("Detection mode = file")
            detectionMode = "file"
          }
        }
        else {
          if (detectionMode == "file" && file.isDirectory) {
            println("Detection mode is file but file is directory")
            detectionModeFixed = false
          }
          else if (detectionMode == "directory" && !file.isDirectory ) {
            println("Detection mode is directory but file is file")
            detectionModeFixed = false
          }
        }
      }
    }

    detectionModeFixed
  }

  def processResults(result: String, settings: JPlagSettings, detectionID: String, detectionMode: String): DetectionResult = {
    connection = DriverManager.getConnection(url, username, password)
    val statement = connection.createStatement()
    val resultData = result.split("&").map(_.trim())
    val unplagiarisedGroup = new ListBuffer[StudentFilePair]

    var counter = 0

    while (counter+1 < resultData.length) {
      //for every 4 in resultData:
      //  Index 0: Student A name
      //  Index 1: Student B name
      //  Index 2: File pair percentage
      //  Index 3: File pair match index
      if ((counter+1) %4 != 0) {
        val studentFilePairID = UUID.randomUUID.toString
        //get each code file pair comparisons
        val studentFilePair = getStudentFilePairs(resultData(counter), resultData(counter+1),
          BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble, resultData(counter+3), detectionID, detectionMode)
        try {
          statement.executeUpdate(s"Insert into studentfilepair values('${studentFilePair.studentA}', '${studentFilePair.studentB}', '${studentFilePair.percentage}', " +
            s"'${studentFilePairID}', '$detectionID')")
        }
        catch {
          case e: SQLException =>
            println(e.printStackTrace())
        }

        val highToken = studentFilePair.tokenList.filter(pairs => pairs.tokenNum >= settings.sensitivity.toInt)
        //only matches that have similarity higher than minPercentage will be saved
        if (resultData(counter+2).toDouble >= settings.minPercentage.toInt) {
          //high chance to be plagiarising
          if (highToken.nonEmpty || studentFilePair.percentage >= 90) {
            addIntoPlagiarismGroup(studentFilePair, highToken, studentFilePairID)
          }
          //unplagiarised student pair
          else {
            unplagiarisedGroup += studentFilePair
          }
          //insert code files into database
          try {

            for (codefile <- studentFilePair.studentAFiles) {
              statement.executeUpdate(s"Insert into codefile values('${codefile.fileName}', '${codefile.code}', '${studentFilePairID}', 'A')")
            }
            for (codefile <- studentFilePair.studentBFiles) {
              statement.executeUpdate(s"Insert into codefile values('${codefile.fileName}', '${codefile.code}', '${studentFilePairID}', 'B')")
            }
            for (codetoken <- studentFilePair.tokenList) {
              statement.executeUpdate(s"Insert into codetokens values('${codetoken.tokenNum}', '${codetoken.lineNumA}', '${codetoken.lineNumB}', '$studentFilePairID')")
            }
          }
          catch {
            case e: SQLException =>
              println(e.printStackTrace())
          }
        }
      }
      counter += 4
    }


    val unPlagiarisedPairs = unplagiarisedGroup.toList.sortBy(_.percentage)(Ordering[Double].reverse)
    val plagiarismGroup = plagiarisedGroup.toList.sortBy(_.tokenNo)(Ordering[Int].reverse)
    val processedResults = new DetectionResult(unPlagiarisedPairs, plagiarismGroup, detectionMode)
    detectionResult = Some(processedResults)
    processedResults
  }

  def addIntoPlagiarismGroup(studentFilePair: StudentFilePair, highTokenList: List[CodeToken],
                             studentFilePairID: String): ListBuffer[PotentialPlagiarismGroup] = {
    // if plagiarism group already available, check and see if token matches
    if (plagiarisedGroup.nonEmpty) {
      println("Student " + studentFilePair.studentA + " & " + studentFilePair.studentB)
      var addedIntoGroup = false
      var addedIntoGroupAsMatch = false
      // loop to check if token matches, if yes assume they are in the same plagiarism group
      for (group <- plagiarisedGroup) {

        val groupID = group.groupID
//        //token matches, add into same group
//        for (highToken <- highTokenList) {
//          if (group.tokenNo == highToken.tokenNum) {
//            println("Insert into group " + group.tokenNo + "because of token match")
//            group.studentPairs += studentFilePair
//            addedIntoGroup = true
//            try {
//              connection = DriverManager.getConnection(url, username, password)
//              val statement = connection.createStatement()
//              statement.executeUpdate(s"Insert into groupstudent values('${groupID}', '${studentFilePairID}')")
//            }
//            catch {
//              case e: SQLException =>
//                println(e.printStackTrace())
//            }
//          }
//        }

        if (!addedIntoGroupAsMatch) {
          var studentMatch = 0
          val studentSize = group.studentPairs.size
          for (studentPair <- group.studentPairs) {
            if (studentPair.studentA == studentFilePair.studentA) {
              studentMatch += 1
            }
            else if (studentPair.studentA == studentFilePair.studentB) {
              studentMatch += 1
            }
            else if (studentPair.studentB == studentFilePair.studentA) {
              studentMatch += 1
            }
            else if (studentPair.studentB == studentFilePair.studentB) {
              studentMatch += 1
            }
          }
          if (studentMatch >=  1) {
            group.studentPairs += studentFilePair
            println("Added into group " + group.tokenNo + " because of name match")
            addedIntoGroupAsMatch = true
            try {
              connection = DriverManager.getConnection(url, username, password)
              val statement = connection.createStatement()
              statement.executeUpdate(s"Insert into groupstudent values('${groupID}', '${studentFilePairID}')")
            }
            catch {
              case e: SQLException =>
                println(e.printStackTrace())
            }
          }
        }
      }
      // boolean check failed, token is not similar to any groups currently available, create a new group
      if (!addedIntoGroupAsMatch) {
        val newPlagiarismGroupID = UUID.randomUUID.toString
        val studentPair = studentFilePair
        plagiarisedGroup += new PotentialPlagiarismGroup(new ListBuffer[StudentFilePair](), newPlagiarismGroupID)
        plagiarisedGroup.last.studentPairs += studentPair

        try {
          connection = DriverManager.getConnection(url, username, password)
          val statement = connection.createStatement()
          statement.executeUpdate(s"Insert into potentialplagiarismgroup values('${newPlagiarismGroupID}', '${detectionID}')")
          statement.executeUpdate(s"Insert into groupstudent values('${newPlagiarismGroupID}', '${studentFilePairID}')")
        }
        catch {
          case e: SQLException =>
            println(e.printStackTrace())
        }
      }
    }
    else {
      println("No plagiarism group exist yet")
      val newPlagiarismGroupID = UUID.randomUUID.toString
      val studentPair = studentFilePair
      plagiarisedGroup += new PotentialPlagiarismGroup(new ListBuffer[StudentFilePair](), newPlagiarismGroupID)
      plagiarisedGroup.last.studentPairs += studentPair

      try {
        connection = DriverManager.getConnection(url, username, password)
        val statement = connection.createStatement()
        statement.executeUpdate(s"Insert into potentialplagiarismgroup values('${newPlagiarismGroupID}', '${detectionID}')")
        statement.executeUpdate(s"Insert into groupstudent values('${newPlagiarismGroupID}', '${studentFilePairID}')")
      }
      catch {
        case e: SQLException =>
          println(e.printStackTrace())
      }
    }
    plagiarisedGroup
  }

  def findMaxToken(token1: CodeToken, token2: CodeToken): CodeToken = if (token1.tokenNum > token2.tokenNum) token1 else token2


  def getStudentFilePairs (studentA: String, studentB: String, percentage: Double, matchIndex: String, resultFolderName: String, detectionMode: String): StudentFilePair = {

    //get the file paths of the matches generated by JPlag
    val studentAMatchFile = s"${defaultDestinationPath}/$resultFolderName/match" + matchIndex + "-0.html"
    val studentBMatchFile = s"${defaultDestinationPath}/$resultFolderName/match" + matchIndex + "-1.html"
    val comparisonTable = s"${defaultDestinationPath}/$resultFolderName/match" + matchIndex + "-top.html"

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

    //generate token list
    var tokenCounter = 0
    var tokenList: ListBuffer[CodeToken] = new ListBuffer[CodeToken]()
    while (tokenCounter < comparisonLines.length) {
      //      val split1 = comparisonLines(tokenCounter).split("\\(")(1)
      //      val linesNumbersA = split1.split("\\)")(0)
      //      val split2 = comparisonLines(tokenCounter+1).split("\\(")(1)
      //      val linesNumbersB = split2.split("\\)")(0)
      tokenList += new CodeToken(comparisonTokens(tokenCounter+1).toInt, comparisonLines(tokenCounter), comparisonLines(tokenCounter+1))
      tokenCounter += 2
    }

    //generate code file pairs list
    val codeFileA = new ListBuffer[CodeFile]()
    val codeFileB = new ListBuffer[CodeFile]()
    var comparisonLinesIterator = 0
    var codeFileIterator = 0

    //if file mode
    if (detectionMode == "file") {
      val removeSingleColonCodeA = studentACodes.last.replace("\'", "")
      val removeSingleColonCodeB = studentBCodes.last.replace("\'", "")
      codeFileA += new CodeFile(studentAtitles.head, removeSingleColonCodeA)
      codeFileB += new CodeFile(studentBtitles.head, removeSingleColonCodeB)
    }
    //if directory mode
    else {
      for (index <- studentAtitles.indices) {
        val removeSingleColonCode = studentACodes(index).replace("\'", "")
        codeFileA += new CodeFile(studentAtitles(index), removeSingleColonCode)
      }
      for (index <- studentBtitles.indices) {
        val removeSingleColonCode = studentBCodes(index).replace("\'", "")
        codeFileB += new CodeFile(studentBtitles(index), removeSingleColonCode)
      }
    }
    //generate student pairs
    val studentFilePair = new StudentFilePair(studentA, studentB, percentage, UUID.randomUUID().toString, codeFileA.toList, codeFileB.toList, tokenList.toList)
    studentFilePair
  }
}
