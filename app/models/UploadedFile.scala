package models

import play.api.libs.json.{Json, Reads, Writes}

case class UploadedFile (fileName: String) {

}

object UploadedFile {
  implicit val uploadedFileImplicitReads: Reads[UploadedFile] = Json.reads[UploadedFile]
  implicit val uploadedFileImplicitWrites: Writes[UploadedFile] = Json.writes[UploadedFile]
}
