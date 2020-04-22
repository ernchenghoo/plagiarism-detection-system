package controllers

import java.io.BufferedReader
import java.nio.file.Paths
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import javax.inject.Inject
import models.JPlag
import play.api.data.Forms._
import play.api.data._
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest, _}
import play.api.routing.JavaScriptReverseRouter
import play.twirl.api.{Html, MimeTypes}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise, blocking}
import scala.io.Source
import scala.reflect.io.{Directory, File}
import scala.sys.process.{Process, ProcessIO}
import scala.util.Success

class RunJPlagController @Inject()(cc: MessagesControllerComponents, assets: Assets) extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)
  private val JPlagInstances = ArrayBuffer[JPlag]()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  val newInstance: JPlag = JPlag("python3")

  val form: Form[JPlag] = Form(
    mapping(
      "language" -> text
    )(JPlag.apply)(JPlag.unapply)
  )

  def detection: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    // pass an unpopulated form to the template
    Ok(views.html.detection_main(form))
  }

  def homepage (status: Option[Boolean]): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    status match {
      case Some(s) => {
        newInstance.exitCode match {
          case 0 => Ok(views.html.homepage(Some("The plagiarism detection has run successfully!")))
          case 1 => Ok(views.html.homepage(Some(newInstance.error)))
        }

      }
      case None => Ok(views.html.homepage(None))
    }
  }

  def resultTest (): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.result(newInstance))
  }

  def resultDetail (group: Int): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    group match {
      case 1 => Ok(views.html.result_detail(newInstance.resultList))
//      case 2 => Ok(views.html.result_detail(newInstance.group2))
//      case 3 => Ok(views.html.result_detail(newInstance.group3))
    }
  }

  def resultCodeComparison (matchIndex: Int): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>

    var file1: String = "<h3 id=\"title_text\" class=\"text-center\">Similarity comparison</h3>"
    //file1 += Source.fromFile("./public/results/match0-0.html").getLines().toString()

    val fileName1 = "match0-1.html"
    Ok(views.html.result_code_comparison(matchIndex))

  }

  def getCodeComparisons (matchIndex: Int): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    val fileName = "./public/results/match" + matchIndex + "-0.html"
    val fileString = Source.fromFile(fileName).getLines.mkString
    Ok(fileString)
  }

  def runJPlag() = Action { request =>
    //File("./public/results").deleteRecursively()
    val runStatus = newInstance.runJPlag()
    Redirect(routes.RunJPlagController.homepage(Some(runStatus)))
  }

  def result = EssentialAction { request =>
    assets.at("/public/results/", "index.html")(request)
  }

  def upload = Action(parse.multipartFormData) { request =>
    request.body.files.foreach( file => {
      val filename = Paths.get(file.filename).getFileName
      file.ref.moveTo(Paths.get(s"./testFiles/$filename").toFile, replace = true)
    })
    Redirect(routes.RunJPlagController.detection())
  }



//  def save: Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
//    val errorFunction = { formWithErrors: Form[JPlag] =>
//      logger.debug("CAME INTO errorFunction")
//      // this is the bad case, where the form had validation errors.
//      // show the user the form again, with the errors highlighted.
//      BadRequest(views.html.detection_main(formWithErrors))
//    }
//
//    val successFunction = { data: JPlag =>
//      logger.debug("CAME INTO successFunction")
//      // this is the SUCCESS case, where the form was successfully parsed as a JPlag
//      val JPlagInstance = JPlag(data.language)
//      logger.debug(JPlagInstances.toString)
//      JPlagInstances.append(JPlagInstance)
//      Redirect(routes.RunJPlagController.result()).flashing(
//        "Language" -> JPlagInstances.last.language)
//    }
//
//    val formValidationResult: Form[JPlag] = form.bindFromRequest
//    formValidationResult.fold(
//      errorFunction, // sad case
//      successFunction // happy case
//    )
//  }


}
