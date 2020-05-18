package controllers
import java.io.File
import java.nio.file.{Path, Paths}
import javax.inject.Inject
import models.{Database, JPlag, JPlagSettings, PotentialPlagiarismGroup, StudentFilePairs}
import play.api.libs.Files
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import play.twirl.api.MimeTypes

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise, blocking}



class RunJPlagController @Inject()(cc: MessagesControllerComponents, assets: Assets) extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  def javascriptRoutes: Action[AnyContent] = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.RunJPlagController.homepage,
      )
    ).as(MimeTypes.JAVASCRIPT)
  }


  def detection(): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.detection_main())
  }

  def homepage (): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.homepage(None))
  }

  def homepageAfterRun (status: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.homepage(Some(status)))
  }

  def resultSelect : Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.select_result_page(JPlag.getPastDetectionList))
  }

  def result (detectionID: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    println("Detection ID: " + detectionID)
    Ok(views.html.result(JPlag.fetchResultFromDB(detectionID)))
  }

  def resultDetail (groupID: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>

    Ok(views.html.result_detail(JPlag.detectionResult.get.resultPlagiarismGroups.find(pairs => pairs.groupID == groupID).get))
  }

  def resultCodeComparison (groupID: Option[String], matchIndex: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    var selectedPair: Option[StudentFilePairs] = null
    if (groupID.isDefined) {
      for (plgGroup <- JPlag.detectionResult.get.resultPlagiarismGroups) {
        if (plgGroup.groupID == groupID.get) {
          selectedPair = plgGroup.studentPairs.find(student => student.studentFilePairID == matchIndex)
        }
      }
    }
    else {
      selectedPair = JPlag.detectionResult.get.resultNonPlagiarismStudentPairs.find(student => student.studentFilePairID == matchIndex)
    }

    Ok(views.html.result_code_comparison(matchIndex, selectedPair))

  }

  def validateDetection: Action[AnyContent] = Action { request =>
    val runCheck = JPlag.checkJPlagRunConditions
    Ok(Json.obj("message" -> runCheck))
  }


  def runJPlag: Action[AnyContent] = Action { request =>
    val runResponse: Future[Option[String]] = JPlag.runJPlag()
    runResponse.map(
      response =>
        if (response.isEmpty) {
          println("Success")
          Ok(Json.obj("Status" -> "Success"))

        }
        else {
          println(response)
          println("error")
          Ok(Json.obj(
            "Status" -> response))
        }
    )
    Ok(Json.obj("Status" -> "Processing"))
  }

  def studentFileUpload: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    println("Backend got it")
    request.body.files.foreach( file => {
      val filename = Paths.get(file.filename).getFileName.toFile
      println("File name: " + filename)
      file.ref.moveTo(Paths.get(s"./studentFiles/$filename").toFile, replace = true)
    })
    JPlag.unZipUploadedFiles()
    Ok(Json.obj("message" -> "Your files have been uploaded!"))
  }

  def submitSettings: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    var language = ""
    var sensitivity = ""
    var minPercentage = ""
    request.body.dataParts.foreach( data => {
      data._1 match {
        case "language" => language = data._2.head
        case "sensitivity" => sensitivity = data._2.head
        case "minPercentage" => minPercentage = data._2.head
      }
    })
    JPlag.language = language
    if (request.body.files.nonEmpty) {
      new File(s"${JPlag.baseCodeDirectoryPath}").mkdirs()
      request.body.files.foreach( file => {
        val filename = file.filename
        val baseCodeFile = Paths.get(file.filename).getFileName.toFile
        println(s"File name: ${filename}")
        file.ref.moveTo(Paths.get(s"${JPlag.baseCodeDirectoryPath}/$baseCodeFile").toFile, replace = true)

        JPlag.settings = Some(new JPlagSettings(sensitivity, minPercentage, true))
      })
    }
    else {
      JPlag.settings = Some(new JPlagSettings(sensitivity, minPercentage, false))
    }

    Ok(Json.obj("message" -> "Your settings have been saved!"))

  }
}
