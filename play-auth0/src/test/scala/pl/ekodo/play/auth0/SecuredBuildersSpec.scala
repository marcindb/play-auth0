package pl.ekodo.play.auth0

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.RSAPrivateKeySpec
import java.util.Date

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.ConfigFactory
import org.apache.commons.codec.binary.Base64
import org.scalatestplus.play.PlaySpec
import play.api.http.{DefaultFileMimeTypesProvider, FileMimeTypes, FileMimeTypesConfiguration}
import play.api.mvc.{Action, _}
import play.api.routing.sird.{GET, _}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, WsTestClient}
import play.core.server.Server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SecuredBuildersSpec extends PlaySpec {

  private val kf = KeyFactory.getInstance("RSA")
  private val modulus = "AJjTJOdmf4WxNJF7sSVkCgTYSZUsFrxO9spuocy4Gz+K3IKDILsrHFAsVSiuHf/tEZFeYPtBaQtkACPdfbzs9iTU/PQTq+JMJmTJ3J+p36XKVOopkTGRBZYuORghwkm15qdqhIhq+jc1Ra1ykGpMWaKxolSURmVqqnNcgzjLFiw3c+jtH+69civo9VSEJiLDJiXh5wrw+msf68qVyn6XElGX1LRjdHuji3Klt5sVmrfZJ6FDdigR7VykNujglZW5YxB4Mv1Eo/om9PV0Du+XYGhMMndywGD/X40YuvPQlOQowcSZQLMeDspzg92SfB7QhpTcuFdfauijyuqU/Fzbqdc="
  private val privateExponent = "KgSrud/JohWF0ZZDr3cg9hINsTENEztWyXO/kszv2PmyBUROZIfG4hg+VdABuZMR6HkdixeB7TrSewnz/1TbnGbfIbCi6rZrO/zwZwnKzwehbzx8ijdIZPFSi1sE00VrJI6539Qh9bn07Wp5YHCbmIrkigK2lD3OnadLAPt9niw3MPXUHi3T1SZGbnx8EM0BVlGGICmV1FIv1EVkzhDHsP6PAXJVlccpx8vOYEc41cz52rUg2badl409J9xwBV6v8HMuKOc/a7kNv4Y1swI8Y7c78JGmiDsb814zQgm1i86VwRRtnKxUSduqctYsCqnBK0e41RWxlEZ6wrBggl/CEQ=="
  private val n = new BigInteger(1, Base64.decodeBase64(modulus))
  private val e = new BigInteger(1, Base64.decodeBase64(privateExponent))
  private val privateKey = kf.generatePrivate(new RSAPrivateKeySpec(n, e))

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val config = new Auth0ConfigurationProvider(ConfigFactory.load())

  implicit val fileMimeTypes: FileMimeTypes = new DefaultFileMimeTypesProvider(FileMimeTypesConfiguration()).get

  def withSecuredBuilders[T](block: Auth0Secured => T): T = {

    Server.withRouter() {
      case GET(p"/.well-known/jwks.json") => Action {
        Results.Ok.sendResource("jwks.json")
      }
    } { implicit port =>
      WsTestClient.withClient { client =>
        block(new Auth0Secured(client, config))
      }
    }
  }

  class ExampleController(val secured: Secured) extends ControllerHelpers {
    def index(): Action[AnyContent] = secured {
      Ok
    }
  }

  "SecuredBuilder" should {
    "return 200 if token is valid" in {
      withSecuredBuilders { auth0Secured =>
        val parser = new BodyParsers.Default()
        val controller = new ExampleController(new Secured(auth0Secured, parser))

        val algorithm = Algorithm.RSA256(null, privateKey.asInstanceOf[RSAPrivateKey])
        val token = JWT.create()
          .withIssuer(config.get().issuer)
          .withAudience(config.get().audience)
          .withSubject("user_id")
          .withKeyId("RjM1RDgyNDkzRTQwNTI1NkNGNEIyNzY4N0VEQjcwQTg1MTc3N0MzNw")
          .withExpiresAt(new Date(new Date().getTime + 1000))
          .sign(algorithm)
        val result: Future[Result] = controller.index().apply(FakeRequest().withHeaders("Authorization" -> s"Bearer $token"))
        status(result) mustBe OK
      }
    }
  }

}
