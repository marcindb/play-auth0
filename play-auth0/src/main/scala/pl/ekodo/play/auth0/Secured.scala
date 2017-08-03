package pl.ekodo.play.auth0

import javax.inject.Inject

import play.api.mvc
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

class Secured @Inject() (auth0Secured: Auth0Secured, val parser: BodyParsers.Default)
                        (implicit val executionContext: ExecutionContext)
    extends ActionBuilder[VerifiedRequest, AnyContent] with ActionRefiner[Request, VerifiedRequest] {
  override def refine[A](input: Request[A]): Future[Either[mvc.Results.Status, VerifiedRequest[A]]] =
    input.headers.get("Authorization").map { auth =>
      auth0Secured.validate(auth).map { subject =>
        Right(new VerifiedRequest(subject, input))
      }.recover {
        case NonFatal(_) => Left(Results.Forbidden)
      }
    }.getOrElse(Future.successful(Left(Results.Forbidden)))
}
