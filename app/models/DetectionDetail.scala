package models

import play.api.libs.json.{Json, Reads, Writes}

case class DetectionDetail(detectionName: String, detectionID: String, detectionDateTime: String){

}

object DetectionDetail {
  implicit val detectionDetailImplicitReads: Reads[DetectionDetail] = Json.reads[DetectionDetail]
  implicit val detectionDetailImplicitWrites: Writes[DetectionDetail] = Json.writes[DetectionDetail]
}