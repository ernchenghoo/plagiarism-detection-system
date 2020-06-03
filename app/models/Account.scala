package models

import play.api.libs.json.{Json, Reads, Writes}

case class Account(username: String, var password: String) {
}

object Account {
  implicit val AccountsImplicitReads: Reads[Account] = Json.reads[Account]
  implicit val AccountsImplicitWrites: Writes[Account] = Json.writes[Account]
}
