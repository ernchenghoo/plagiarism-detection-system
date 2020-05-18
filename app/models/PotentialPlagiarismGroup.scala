package models

import scala.collection.mutable.ListBuffer

class PotentialPlagiarismGroup(val studentPairs: ListBuffer[StudentFilePairs], val groupID: String, val tokenNo: Int) {

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
