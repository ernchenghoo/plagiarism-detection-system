package models

import play.api.libs.json.{Json, OWrites, Reads, Writes}

case class JPlagSettings(sensitivity: String, var minPercentage: String, baseCodeExist: Boolean) {
}

object JPlagSettings {
  implicit val jplagSettingsImplicitReads: Reads[JPlagSettings] = Json.reads[JPlagSettings]
  implicit val jplagSettingsImplicitWrites: Writes[JPlagSettings] = Json.writes[JPlagSettings]
}