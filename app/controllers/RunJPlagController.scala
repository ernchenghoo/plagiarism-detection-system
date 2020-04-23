package controllers


import java.nio.file.Paths

import javax.inject.Inject
import models.{JPlag, StudentFilePairs}
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise, blocking}


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

  def result (): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
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

    val selectedPair: Option[StudentFilePairs] = newInstance.resultList.find(student => student.matchIndex == matchIndex)

    Ok(views.html.result_code_comparison(matchIndex, selectedPair))

  }

  def runJPlag() = Action { request =>
    //File("./public/results").deleteRecursively()
    val runStatus = newInstance.runJPlag()
    Redirect(routes.RunJPlagController.homepage(Some(runStatus)))
  }

  def upload = Action(parse.multipartFormData) { request =>
    request.body.files.foreach( file => {
      val filename = Paths.get(file.filename).getFileName
      file.ref.moveTo(Paths.get(s"./testFiles/$filename").toFile, replace = true)
    })
    Redirect(routes.RunJPlagController.detection())
  }

}
