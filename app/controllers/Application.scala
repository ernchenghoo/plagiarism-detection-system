package controllers

import javax.inject.Inject
import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}
import play.api.routing.JavaScriptReverseRouter
import play.twirl.api.MimeTypes

class Application @Inject()(cc: MessagesControllerComponents, assets: Assets) extends MessagesAbstractController(cc) {

  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.RunJPlagController.upload,
      )
    ).as(MimeTypes.JAVASCRIPT)
  }
}

