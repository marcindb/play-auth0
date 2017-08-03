package pl.ekodo.play.auth0

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.RSAPrivateKeySpec
import java.time.Clock
import java.util.Date

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.ConfigFactory
import org.apache.commons.codec.binary.Base64
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec
import play.api.http.{ DefaultFileMimeTypesProvider, FileMimeTypes, FileMimeTypesConfiguration }
import play.api.mvc.{ Action, _ }
import play.api.routing.sird.{ GET, _ }
import play.api.test.Helpers._
import play.api.test.{ FakeRequest, WsTestClient }
import play.core.server.Server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SecuredSpec extends PlaySpec with BeforeAndAfterAll {

  private val kf = KeyFactory.getInstance("RSA")
  private val modulus = "AJjTJOdmf4WxNJF7sSVkCgTYSZUsFrxO9spuocy4Gz+K3IKDILsrHFAsVSiuHf/tEZFeYPtBaQtkACPdfbzs9iTU/PQTq+JMJmTJ3J+p36XKVOopkTGRBZYuORghwkm15qdqhIhq+jc1Ra1ykGpMWaKxolSURmVqqnNcgzjLFiw3c+jtH+69civo9VSEJiLDJiXh5wrw+msf68qVyn6XElGX1LRjdHuji3Klt5sVmrfZJ6FDdigR7VykNujglZW5YxB4Mv1Eo/om9PV0Du+XYGhMMndywGD/X40YuvPQlOQowcSZQLMeDspzg92SfB7QhpTcuFdfauijyuqU/Fzbqdc="
  private val privateExponent = "KgSrud/JohWF0ZZDr3cg9hINsTENEztWyXO/kszv2PmyBUROZIfG4hg+VdABuZMR6HkdixeB7TrSewnz/1TbnGbfIbCi6rZrO/zwZwnKzwehbzx8ijdIZPFSi1sE00VrJI6539Qh9bn07Wp5YHCbmIrkigK2lD3OnadLAPt9niw3MPXUHi3T1SZGbnx8EM0BVlGGICmV1FIv1EVkzhDHsP6PAXJVlccpx8vOYEc41cz52rUg2badl409J9xwBV6v8HMuKOc/a7kNv4Y1swI8Y7c78JGmiDsb814zQgm1i86VwRRtnKxUSduqctYsCqnBK0e41RWxlEZ6wrBggl/CEQ=="
  private val n = new BigInteger(1, Base64.decodeBase64(modulus))
  private val e = new BigInteger(1, Base64.decodeBase64(privateExponent))
  private val privateKey = kf.generatePrivate(new RSAPrivateKeySpec(n, e))

  private val subject = "user_id"
  private val keyId = "RjM1RDgyNDkzRTQwNTI1NkNGNEIyNzY4N0VEQjcwQTg1MTc3N0MzNw"

  private val algorithm = Algorithm.RSA256(null, privateKey.asInstanceOf[RSAPrivateKey])

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  private def validToken = JWT.create()
    .withIssuer(config.get().issuer)
    .withAudience(config.get().audience)
    .withSubject(subject)
    .withKeyId(keyId)
    .withExpiresAt(new Date(Clock.systemUTC().instant().plusSeconds(30).toEpochMilli))
    .sign(algorithm)

  private def outdatedToken = JWT.create()
    .withIssuer(config.get().issuer)
    .withAudience(config.get().audience)
    .withSubject(subject)
    .withKeyId(keyId)
    .withExpiresAt(new Date(Clock.systemUTC().instant().minusSeconds(30).toEpochMilli))
    .sign(algorithm)

  private def invalidIssuerToken = JWT.create()
    .withIssuer("invalid")
    .withAudience(config.get().audience)
    .withSubject(subject)
    .withKeyId(keyId)
    .withExpiresAt(new Date(Clock.systemUTC().instant().plusSeconds(30).toEpochMilli))
    .sign(algorithm)

  private def invalidKeyIdToken = JWT.create()
    .withIssuer(config.get().issuer)
    .withAudience(config.get().audience)
    .withSubject(subject)
    .withKeyId("key")
    .withExpiresAt(new Date(Clock.systemUTC().instant().plusSeconds(30).toEpochMilli))
    .sign(algorithm)

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  val config = new Auth0ConfigurationProvider(ConfigFactory.load())

  implicit val fileMimeTypes: FileMimeTypes = new DefaultFileMimeTypesProvider(FileMimeTypesConfiguration()).get

  def withSecuredBuilders[T](block: Auth0Secured => T): T = {

    Server.withRouter() {
      case GET(p"/") => Action {
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

    def subject(): Action[AnyContent] = secured { req: VerifiedRequest[AnyContent] =>
      Ok(req.subject)
    }
  }

  "Secured endpoint" should {
    "return 200 if token is valid" in {
      withSecuredBuilders { auth0Secured =>
        val parser = new BodyParsers.Default()
        val controller = new ExampleController(new Secured(auth0Secured, parser))

        val result: Future[Result] = controller.index()
          .apply(FakeRequest().withHeaders("Authorization" -> s"Bearer $validToken"))
        status(result) mustBe OK
      }
    }

    "pass subject if token is valid" in {
      withSecuredBuilders { auth0Secured =>
        val parser = new BodyParsers.Default()
        val controller = new ExampleController(new Secured(auth0Secured, parser))

        val result: Future[Result] = controller.subject()
          .apply(FakeRequest().withHeaders("Authorization" -> s"Bearer $validToken"))
        contentAsString(result) mustEqual "user_id"
      }
    }

    "return 403 if token is outdated" in {
      withSecuredBuilders { auth0Secured =>
        val parser = new BodyParsers.Default()
        val controller = new ExampleController(new Secured(auth0Secured, parser))

        val result: Future[Result] = controller.index()
          .apply(FakeRequest().withHeaders("Authorization" -> s"Bearer $outdatedToken"))
        status(result) mustBe FORBIDDEN
      }
    }

    "return 403 if issuer is invalid" in {
      withSecuredBuilders { auth0Secured =>
        val parser = new BodyParsers.Default()
        val controller = new ExampleController(new Secured(auth0Secured, parser))

        val result: Future[Result] = controller.index()
          .apply(FakeRequest().withHeaders("Authorization" -> s"Bearer $invalidIssuerToken"))
        status(result) mustBe FORBIDDEN
      }
    }

    "return 403 if key id is invalid" in {
      withSecuredBuilders { auth0Secured =>
        val parser = new BodyParsers.Default()
        val controller = new ExampleController(new Secured(auth0Secured, parser))

        val result: Future[Result] = controller.index()
          .apply(FakeRequest().withHeaders("Authorization" -> s"Bearer $invalidKeyIdToken"))
        status(result) mustBe FORBIDDEN
      }
    }

  }

}
