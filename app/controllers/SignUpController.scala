package controllers

import javax.inject.Inject

import play.api.mvc.Controller
import security.{TokenProvider, TokenStorage}


class SignUpController @Inject()(tokenProvider: TokenProvider,
                                 tokenStorage: TokenStorage) extends Controller {


}

object SignUpController {

  case class SignUpDto(firstName: String,
                       lastName: String,
                       phoneNumber: Int,
                       email: String,
                       password: String)

}
