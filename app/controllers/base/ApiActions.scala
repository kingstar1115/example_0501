package controllers.base

import commons.enums.NotAuthorized
import play.api.http.HeaderNames
import play.api.mvc._
import security.{AuthToken, TokenStorage}

import scala.concurrent.Future


trait ApiActions extends RestResponses {

  val tokenStorage: TokenStorage

  val transformAction = new UserAction
  val authorizedAction = new AuthCheckAction

  val authorized = transformAction andThen authorizedAction


  class UserRequest[A](val token: Option[AuthToken], request: Request[A]) extends WrappedRequest[A](request)

  class UserAction extends ActionBuilder[UserRequest] with ActionTransformer[Request, UserRequest] {
    def transform[A](request: Request[A]) = Future.successful {
      val token = request.headers.get(HeaderNames.AUTHORIZATION)
        .flatMap(key => tokenStorage.getToken(key))
      new UserRequest(token, request)
    }
  }

  class AuthCheckAction extends ActionFilter[UserRequest] {
    def filter[A](userRequest: UserRequest[A]) = Future.successful {
      userRequest.token.map(t => None).getOrElse(Some(forbidden("Provide token", NotAuthorized)))
    }
  }

}
