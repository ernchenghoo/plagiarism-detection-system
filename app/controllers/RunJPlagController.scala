package controllers
import java.io.File
import java.nio.file.{Path, Paths}
import java.util.concurrent.{ExecutorService, Executors}

import scala.concurrent.duration.Duration
import java.util.{Calendar, Date}

import javax.inject.Inject
import models.{Account, Database, Detection, DetectionDetail, DetectionManager, JPlagSettings, PotentialPlagiarismGroup, StudentFilePair}
import play.api.libs.Files
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import play.twirl.api.{Html, MimeTypes}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise, blocking}

class RunJPlagController @Inject()(cc: MessagesControllerComponents, assets: Assets) extends MessagesAbstractController(cc) {

  implicit val ec: ExecutionContext = new ExecutionContext {
    val threadPool: ExecutorService = Executors.newFixedThreadPool(25)

    def execute(runnable: Runnable) {
      threadPool.submit(runnable)
    }

    def reportFailure(t: Throwable) {}
  }

  DetectionManager.runningDetections.clear()
  DetectionManager.loggedInUsername = "ernchenghoo"
//  DetectionManager.clearAllStudentFiles()
//  println("Clear student files")

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
    Redirect(routes.RunJPlagController.getLoginPage()).withNewSession
  }

  def getDetectionMainPage: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    val session = request.session.get("username")
    session.map { username =>
      DetectionManager.generateNewDetectionInstance()
      Ok(views.html.detection_main())
    }.getOrElse(Ok(views.html.no_login_page()))
  }

  def generateNewDetectionInstance: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>

    Ok("Pass")
  }

  def getHomepage: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
      val session = request.session.get("username")
      session.map { username =>
          Ok(views.html.homepage())
      }.getOrElse(Ok(views.html.no_login_page()))
  }

  def getDetectionRan : Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    var detectionToRunID = ""
    if (DetectionManager.currentDetection.isDefined) {
      if (DetectionManager.currentDetection.get.readyToExecute) {
        detectionToRunID = DetectionManager.currentDetection.get.detectionDetails.get.detectionID
        DetectionManager.currentDetection.get.readyToExecute = false
        DetectionManager.runningDetections += DetectionManager.currentDetection.get
        DetectionManager.currentDetection = None
        Ok(Json.obj("Status" -> "Run",
                            "DetectionID" -> s"${detectionToRunID}"))
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
    println("\nChecking for running detections")
    if (DetectionManager.runningDetections.nonEmpty) {
      for (detection <- DetectionManager.runningDetections) {
        println(detection.detectionDetails.get.detectionName)
      }
    }
    Ok(Json.toJson(DetectionManager.getRunningDetectionDetails))
  }

  def getSelectPastDetectionPage : Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    val session = request.session.get("username")
    session.map { username =>
      Ok(views.html.select_result_page(DetectionManager.getPastDetectionList))
    }.getOrElse(Ok(views.html.no_login_page()))
  }

  def getResultPage (detectionID: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    val session = request.session.get("username")
    session.map { username =>
      Ok(views.html.result(DetectionManager.fetchResultFromDB(detectionID)))
    }.getOrElse(Ok(views.html.no_login_page()))
  }

  def getResultDetailPage (groupID: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    val session = request.session.get("username")
    session.map { username =>
      Ok(views.html.result_detail(DetectionManager.detectionResult.get.resultPlagiarismGroups.find(pairs => pairs.groupID == groupID).get))
    }.getOrElse(Ok(views.html.no_login_page()))
  }

  def getCodeComparisonPage (groupID: Option[String], matchIndex: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    val session = request.session.get("username")
    session.map { username =>
      var selectedPair: Option[StudentFilePair] = null
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
    }.getOrElse(Ok(views.html.no_login_page()))
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
    val loginStatus = DetectionManager.validateLoginCredentials(username, password)
    if (loginStatus == "Pass") {
      Ok(loginStatus).withSession("username" -> username)
    }
    else {
      Ok(loginStatus)
    }

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
    println("Detection name: " + detectionName)
    println("Detection language: " + detectionLanguage)
    //set details that is sent from frontend
    DetectionManager.setDetectionDetail(detectionLanguage, detectionName)
    val runCheck = DetectionManager.currentDetection.get.checkJPlagRunConditions()

    Ok(Json.obj("message" -> runCheck))
  }

  def runJPlag(detectionID: String): Action[AnyContent] = Action.async {
    println("\nRunning JPlag")
    val runningDetection = DetectionManager.runningDetections.find(_ .detectionDetails.get.detectionID == detectionID)

    val runningDetectionIndex =  DetectionManager.runningDetections.indexOf(runningDetection)

    val runResponse: Future[Option[String]] = scala.concurrent.Future {
      runningDetection.get.runJPlag()
    }
    Ok(Json.obj("Status" -> "Running"))
    runResponse.map(
      runResponse => {
        println("Future callback received")
        if (runResponse.isEmpty) {
          println("Response is empty")
          //remove complete detection from running detections list
          DetectionManager.runningDetections.remove(runningDetectionIndex)
          if (DetectionManager.runningDetections.nonEmpty) {
            for (detection <- DetectionManager.runningDetections) {
              println(detection.detectionDetails.get.detectionName)
            }
          }
          Ok(Json.obj("Status" -> "Success"))
        }
        else {
          println("Response not empty")
          //remove complete detection from running detections list
          DetectionManager.runningDetections.remove(runningDetectionIndex)
          if (DetectionManager.runningDetections.nonEmpty) {
            for (detection <- DetectionManager.runningDetections) {
              println(detection.detectionDetails.get.detectionName)
            }
          }
          Ok(Json.obj("Status" -> runResponse))
        }
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
      file.ref.moveTo(Paths.get(s"${DetectionManager.currentDetection.get.sourcePath}/$filename"), replace = true)
    })
    val uploadedFiles = DetectionManager.currentDetection.get.unZipUploadedFiles()
    if (uploadedFiles.isDefined) {
      Ok(Json.obj("message" -> "Your files have been uploaded!",
        "uploadedFiles" -> uploadedFiles))
    }
    else {
      Ok(Json.obj("message" -> "Error uploading file",
        "uploadedFiles" -> "None"))
    }
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
