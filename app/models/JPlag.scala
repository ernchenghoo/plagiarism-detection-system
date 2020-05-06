package models
import java.io.{ByteArrayOutputStream, File, PrintWriter}

import org.apache.commons.io.FileUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.zeroturnaround.zip.ZipUtil

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

case class JPlag(language: String) {

  val destinationPath = "./public/results"
  val sourcePath = "./testFiles"
  val resultString = s"java -jar ./jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar -l $language -r $destinationPath -s $sourcePath"
  var unPlagiarisedPairs: List[StudentFilePairs] = List[StudentFilePairs]()
  var error: String = null
  var plagiarismGroup: ListBuffer[PotentialPlagiarismGroup] = new ListBuffer[PotentialPlagiarismGroup]()

  var exitCode: Int = 0

  def runJPlag(): Boolean = {
    plagiarismGroup.clear()
    val command = resultString
    val process = processRunner(command)
    println(process._2)
    exitCode = process._1.toString.toInt
    val rawResults = process._2.trim()
    if (exitCode == 1) {
      error = process._2
      false
    }
    else {
      processResults(rawResults)
      //remove all uploaded files
      val uploadedFilesDirectory = new java.io.File(sourcePath)
      FileUtils.cleanDirectory(uploadedFilesDirectory)
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

  def processResults(rawData: String): Unit = {
    val unplagiarisedGroup = new ListBuffer[StudentFilePairs]
    val results = rawData.split("===")
    val resultData = results(2).split("&").map(_.trim())
    var counter = 0

    while (counter+1 < resultData.length) {
      //for every 4 in resultData:
      //  Index 0: Student A name
      //  Index 1: Student B name
      //  Index 2: File pair percentage
      //  Index 3: File pair match index
      if ((counter+1) %4 != 0) {
          val studentCodeFilePairs = getCodeFilePairs(resultData(counter+3))
          val highToken = studentCodeFilePairs.find(pairs => pairs.tokenNum >= 50)
          //high chance to be plagiarising
          if (highToken.isDefined) {
            // if plagiarism group already available, check and see if token matches
            if (plagiarismGroup.nonEmpty) {
              var addedIntoGroup = false
              // loop to check if token matches, if yes assume they are in the same plagiarism group
              for (group <- plagiarismGroup) {
                //token matches, add into same group
                if (group.tokenNo == highToken.get.tokenNum) {
                  group.studentPairs += new StudentFilePairs(resultData(counter), resultData(counter+1), BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                    resultData(counter+3).toInt, studentCodeFilePairs)
                  addedIntoGroup = true
                }
              }
              // boolean check failed, token is not similar to any groups currently available, create a new group
              if (!addedIntoGroup) {
                val studentPair = new StudentFilePairs(resultData(counter), resultData(counter+1), BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                  resultData(counter+3).toInt, studentCodeFilePairs)
                plagiarismGroup += new PotentialPlagiarismGroup(new ListBuffer[StudentFilePairs](), plagiarismGroup.length + 1, highToken.get.tokenNum)
                plagiarismGroup.last.studentPairs += studentPair
              }
            }
            else {
              val studentPair = new StudentFilePairs(resultData(counter), resultData(counter+1), BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                resultData(counter+3).toInt, studentCodeFilePairs)
              plagiarismGroup += new PotentialPlagiarismGroup(new ListBuffer[StudentFilePairs](), 1, highToken.get.tokenNum)
              plagiarismGroup.last.studentPairs += studentPair
            }
          }
          else {
            unplagiarisedGroup += new StudentFilePairs(resultData(counter), resultData(counter+1), BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              resultData(counter+3).toInt, studentCodeFilePairs)
          }
      }
      counter += 4
    }
    unPlagiarisedPairs = unplagiarisedGroup.toList.sortBy(_.percentage)(Ordering[Double].reverse)
  }

  def getCodeFilePairs (matchIndex: String): List[CodeFilePair] = {
    //get the file paths of the matches generated by JPlag
    val studentAMatchFile = "./public/results/match" + matchIndex + "-0.html"
    val studentBMatchFile = "./public/results/match" + matchIndex + "-1.html"
    val comparisonTable = "./public/results/match" + matchIndex + "-top.html"

    //generate a JSoup doc to extract the contents from static HTML generated
    val studentADoc: Document = Jsoup.parse(new File(studentAMatchFile), "utf-8")
    val studentBDoc: Document = Jsoup.parse(new File(studentBMatchFile), "utf-8")
    val comparisonTableDoc: Document = Jsoup.parse(new File(comparisonTable), "utf-8")

    //remove unnecessary elements
    studentADoc.getElementsByTag("img").remove()
    studentBDoc.getElementsByTag("img").remove()
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

    val studentFilePairs = new ListBuffer[CodeFilePair]()

    var counter = 0
    while (counter < comparisonLines.length) {
      val codeFilePair = new CodeFilePair()
      for (element <- studentFileA) {
        //for titles in comparisonLines, counter is file on the left, counter+1 is file on the right
        if (comparisonLines(counter).contains(element.fileName)) {
          codeFilePair.codeFileA = element
        }
      }
      for (element <- studentFileB) {
        if (comparisonLines(counter+1).contains(element.fileName)) {
          codeFilePair.codeFileB = element
        }
      }
      codeFilePair.tokenNum = comparisonTokens(counter+1).toInt
      studentFilePairs += codeFilePair
      counter += 2
    }

    studentFilePairs.toList
  }

  def unZipUploadedFiles(): Unit = {
    val uploadedFilesDirectory = new java.io.File(sourcePath)
    for (file <- uploadedFilesDirectory.listFiles()) {
      val extension = file.toString.split("\\.").last

      println(extension)
      if (extension == "zip" || extension == "rar") {
        val fileName = file.getName
        ZipUtil.unpack(new File(s"./testFiles/${fileName}"), new File("./testFiles"))
      }
      if (file.delete()) {
        println(s"${file.getName} deleted")
      }
    }

    for (extractedFile <- uploadedFilesDirectory.listFiles()) {
      val extension = extractedFile.toString.split("\\.").last
      if (extension == "zip") {
        val extractedFileName = extractedFile.getName
        ZipUtil.unpack(new File(s"./testFiles/${extractedFileName}"), new File("./testFiles"))
      }
      if (extractedFile.delete()) {
        println(s"${extractedFile.getName} deleted")
      }
    }
  }


}
