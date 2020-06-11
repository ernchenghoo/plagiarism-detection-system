package controllers
import java.io.File
import java.nio.file.{Path, Paths}
import java.util.Calendar
import javax.inject.Inject
import models.{Account, Database, Detection, DetectionDetail, DetectionManager, JPlagSettings, PotentialPlagiarismGroup, StudentFilePairs}
import play.api.libs.Files
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import play.twirl.api.MimeTypes
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise, blocking}

class RunJPlagController @Inject()(cc: MessagesControllerComponents, assets: Assets) extends MessagesAbstractController(cc) {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  DetectionManager.runningDetections.clear()
  DetectionManager.loggedInUsername = "ernchenghoo"

  def javascriptRoutes: Action[AnyContent] = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.RunJPlagController.getHomepage,
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  def getLoginPage: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.login_page())
  }



  def getRegisterPage: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.register_page())
  }

  def logout: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    DetectionManager.loggedInUsername = ""
    Redirect(routes.RunJPlagController.getLoginPage())
  }


  def getDetectionMainPage: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    DetectionManager.currentDetection = Some(new Detection())
    Ok(views.html.detection_main())
  }

  def getHomepage: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
      Ok(views.html.homepage())
  }

  def getDetectionRan : Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    if (DetectionManager.runningDetections.nonEmpty) {
      if (DetectionManager.runningDetections.last.readyToExecute) {
        DetectionManager.runningDetections.last.readyToExecute = false
        Ok(Json.obj("Status" -> "Run"))
      }
      else {
        Ok(Json.obj("Status" -> "No run"))
      }
    }
    else {
      Ok(Json.obj("Status" -> "No run"))
    }
  }

  def getRunningDetections : Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>

    for (detection <- DetectionManager.runningDetections) {
      detection.detectionDetails
    }
    Ok(Json.toJson(DetectionManager.getRunningDetectionDetails))
  }


  def getSelectPastDetectionPage : Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.select_result_page(DetectionManager.getPastDetectionList))
  }

  def getResultPage (detectionID: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.result(DetectionManager.fetchResultFromDB(detectionID)))
  }

  def getResultDetailPage (groupID: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>

    Ok(views.html.result_detail(DetectionManager.detectionResult.get.resultPlagiarismGroups.find(pairs => pairs.groupID == groupID).get))
  }

  def getCodeComparisonPage (groupID: Option[String], matchIndex: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    var selectedPair: Option[StudentFilePairs] = null
    if (groupID.isDefined) {
      for (plgGroup <- DetectionManager.detectionResult.get.resultPlagiarismGroups) {
        if (plgGroup.groupID == groupID.get) {
          selectedPair = plgGroup.studentPairs.find(student => student.studentFilePairID == matchIndex)
        }
      }
    }
    else {
      selectedPair = DetectionManager.detectionResult.get.resultNonPlagiarismStudentPairs.find(student => student.studentFilePairID == matchIndex)
    }

    Ok(views.html.result_code_comparison(matchIndex, selectedPair))

  }

  def getUploadedFiles: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(Json.obj("uploadedFiles" -> DetectionManager.currentDetection.get.getUploadedFiles()))
  }

  def getUploadedBaseFile: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(Json.obj("uploadedBaseFile" -> DetectionManager.currentDetection.get.getUploadedBaseFile()))
  }

  def login: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    var username = ""
    var password = ""
    request.body.dataParts.foreach( data => {
      data._1 match {
        case "username" => username = data._2.head
        case "password" => password = data._2.head
      }
    })
    Ok(DetectionManager.validateLoginCredentials(username, password))
  }

  def register: Action[JsValue] = Action(parse.json) { request =>
    var registerResult = ""
    val accountJSON = request.body.validate[Account]
    accountJSON.fold(
      errors => {
        BadRequest(Json.obj("message" -> JsError.toJson(errors)))
      },
      account => {
        registerResult = DetectionManager.registerAccount(account.username, account.password)
      }
    )
    Ok(Json.obj("message" -> registerResult))
  }

  def validateDetection: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    var detectionName = ""
    var detectionLanguage = ""
    request.body.dataParts.foreach( data => {
      data._1 match {
        case "detectionName" => detectionName = data._2.head
        case "detectionLanguage" => detectionLanguage = data._2.head
      }
    })
    DetectionManager.setDetectionDetail(detectionLanguage, detectionName)
    val runCheck = DetectionManager.currentDetection.get.checkJPlagRunConditions()
    if (runCheck == "Pass") {
      DetectionManager.runningDetections += DetectionManager.currentDetection.get
      DetectionManager.currentDetection = None
    }
    Ok(Json.obj("message" -> runCheck))
  }

  def runJPlag: Action[AnyContent] = Action.async {
    val runResponse: Future[Option[String]] = scala.concurrent.Future {
      DetectionManager.runningDetections.last.runJPlag()
    }
    var status = ""
    runResponse.map(
      response =>
        if (response.isEmpty) {
          DetectionManager.runningDetections.remove(0)
          Ok(Json.obj("Status" -> "Success"))
        }
        else {
          DetectionManager.runningDetections.remove(0)
          Ok(Json.obj("Status" -> response))
        }
    )
  }

  def clearUploadedFiles: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(Json.obj("message" -> DetectionManager.currentDetection.get.clearUploadedFiles()))
  }

  def deleteSingleUploadedFile: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    var fileName = ""
    request.body.asText.foreach( data => {
      fileName = data
    })
    Ok(DetectionManager.currentDetection.get.deleteSingleUploadedFile(fileName))
  }

  def studentFileUpload: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    request.body.files.foreach( file => {
      val filename = Paths.get(file.filename).getFileName
      println(System.getProperty("user.dir"))
      file.ref.moveTo(Paths.get(s"${DetectionManager.currentDetection.get.sourcePath}/$filename"), replace = true)
    })
    val uploadedFiles = DetectionManager.currentDetection.get.unZipUploadedFiles()
    Ok(Json.obj("message" -> "Your files have been uploaded!",
      "uploadedFiles" -> uploadedFiles))
  }

  def submitSettings: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    var sensitivity = ""
    var minPercentage = ""
    request.body.dataParts.foreach( data => {
      data._1 match {
        case "sensitivity" => sensitivity = data._2.head
        case "minPercentage" => minPercentage = data._2.head
      }
    })

    if (request.body.files.nonEmpty) {
      new File(s"${DetectionManager.currentDetection.get.baseCodeDirectoryPath}").mkdirs()
      request.body.files.foreach( file => {
        val filename = file.filename
        val baseCodeFile = Paths.get(file.filename).getFileName.toFile
        println(s"File name: ${filename}")
        file.ref.moveTo(Paths.get(s"${DetectionManager.currentDetection.get.baseCodeDirectoryPath}/$baseCodeFile").toFile, replace = true)
        DetectionManager.currentDetection.get.settings = Some(new JPlagSettings(sensitivity, minPercentage))
        DetectionManager.currentDetection.get.baseCodeExist = true
      })
    }
    else {
      DetectionManager.currentDetection.get.settings = Some(new JPlagSettings(sensitivity, minPercentage))
    }

    Ok(Json.obj("message" -> "Your settings have been saved!"))

  }
}
