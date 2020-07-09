package models

class StudentFilePair(val studentA: String, val studentB: String, val percentage: Double,
                      val studentFilePairID: String, val studentAFiles: List[CodeFile], val studentBFiles: List[CodeFile], var tokenList: List[CodeToken]) {

    var inPlagiarismGroup = false;
}
