package controllers


import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.nio.file.{Path, Paths}
import java.util.zip.{ZipEntry, ZipInputStream}

import javax.inject.Inject
import models.{JPlag, PotentialPlagiarismGroup, StudentFilePairs}
import org.zeroturnaround.zip.ZipUtil
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
    Ok(views.html.result_detail(newInstance.plagiarismGroup.find(pairs => pairs.groupNo == group).get))
  }

  def resultCodeComparison (group: Int, matchIndex: Int): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    var selectedPair: Option[StudentFilePairs] = null
    if (group != 0) {
      for (plgGroup <- newInstance.plagiarismGroup) {
        if (plgGroup.groupNo == group) {
          selectedPair = plgGroup.studentPairs.find(student => student.matchIndex == matchIndex)
        }
      }
    }
    else {
      selectedPair = newInstance.unPlagiarisedPairs.find(student => student.matchIndex == matchIndex)
    }

    Ok(views.html.result_code_comparison(matchIndex, selectedPair))

  }

  def runJPlag() = Action { request =>
    //File("./public/results").deleteRecursively()
    val runStatus = newInstance.runJPlag()
    Redirect(routes.RunJPlagController.homepage(Some(runStatus)))
  }

  def upload = Action(parse.multipartFormData) { request =>
    request.body.files.foreach( file => {
      val filename = Paths.get(file.filename).getFileName.toFile
      file.ref.moveTo(Paths.get(s"./testFiles/$filename").toFile, replace = true)
    })
    newInstance.unZipUploadedFiles()

    Redirect(routes.RunJPlagController.detection())
  }

}
