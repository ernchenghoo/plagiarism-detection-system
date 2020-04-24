package models
import java.io.{ByteArrayOutputStream, File, PrintWriter}


import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

case class JPlag(language: String) {

  val resultString = s"java -jar ./jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar -l $language -r ./public/results -s ./testFiles"
  var resultList: List[StudentFilePairs] = List[StudentFilePairs]()
  var error: String = null
  var plagiarismGroup: ListBuffer[String] = new ListBuffer[String]()
  var plagiarismGroupAverage = 0.0

  var exitCode: Int = 0

  def runJPlag(): Boolean = {
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
    val filteredResults = new ListBuffer[StudentFilePairs]
    val results = rawData.split("===")
    val resultData = results(2).split("&").map(_.trim())
    var counter = 0
    var plagiarismGroupCounter = 0
    var plagiarismGroupTotal = 0.0
    while (counter+1 < resultData.length) {
      //for every 3 in resultData, first field is fileA, second field is fileB, third field is percentage
      if ((counter+1) %4 != 0) {
        if (resultData(counter+2).toDouble > 40) {
          if (resultData(counter+2).toDouble >= 70) {
            filteredResults += new StudentFilePairs(resultData(counter), resultData(counter+1), BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              resultData(counter+3).toInt,true)
            plagiarismGroupTotal += resultData(counter+2).toDouble
            plagiarismGroupCounter += 1
            if (!plagiarismGroup.contains(resultData(counter))) {
              plagiarismGroup += resultData(counter)
            }
            if (!plagiarismGroup.contains(resultData(counter+1))) {
              plagiarismGroup += resultData(counter+1)
            }
          }
          else {
            filteredResults += new StudentFilePairs(resultData(counter), resultData(counter+1), BigDecimal(resultData(counter+2).toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              resultData(counter+3).toInt, false)
          }
        }
      }
      counter += 4
    }
    plagiarismGroupAverage = BigDecimal(plagiarismGroupTotal/plagiarismGroupCounter).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    resultList = filteredResults.toList.sortBy(_.fileA)
  }


}
