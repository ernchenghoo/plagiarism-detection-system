package models

class StudentFilePair(val studentA: String, val studentB: String, val percentage: Double,
                      val studentFilePairID: String, val similarFiles: List[CodeFilePair], var tokenList: List[CodeTokens]) {


}
