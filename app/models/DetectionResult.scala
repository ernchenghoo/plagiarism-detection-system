package models

class DetectionResult(val resultNonPlagiarismStudentPairs: List[StudentFilePair], val resultPlagiarismGroups: List[PotentialPlagiarismGroup], val detectionMode: String) {

}
