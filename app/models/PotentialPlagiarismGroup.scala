package models

import scala.collection.mutable.ListBuffer

class PotentialPlagiarismGroup(val studentPairs: ListBuffer[StudentFilePair], val groupID: String) {

  def findMaxToken(token1: CodeToken, token2: CodeToken): CodeToken = if (token1.tokenNum > token2.tokenNum) token1 else token2

  def tokenNo: Int = {
    var largestToken = 0
    for (pair <- studentPairs) {
      val currentPairLargestToken = pair.tokenList.reduceLeft(findMaxToken).tokenNum
      if (largestToken < currentPairLargestToken) {
        largestToken = currentPairLargestToken
      }
    }
    largestToken
  }

  def sortedStudentPairsBySimilarity: List[StudentFilePair] = {
    studentPairs.toList.sortBy( - _.percentage)
  }

  def averageStudentSimilarityPercentage: Double = {
    var sum = 0.0
    for (pair <- studentPairs) {
      sum += pair.percentage
    }
    val averagePercentage = sum / studentPairs.size
    BigDecimal(averagePercentage).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  def getIndividualStudent: List[String] = {
    val individualStudentList: ListBuffer[String] = new ListBuffer[String]()
    for (pair <- studentPairs) {
      if (!individualStudentList.contains(pair.studentA)) {
        individualStudentList += pair.studentA
      }
      if (!individualStudentList.contains(pair.studentB)) {
        individualStudentList += pair.studentB
      }
    }
    individualStudentList.toList
  }
}
