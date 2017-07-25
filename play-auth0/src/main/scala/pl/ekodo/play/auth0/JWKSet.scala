package pl.ekodo.play.auth0

import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.{ KeyFactory, PublicKey }

import org.apache.commons.codec.binary.Base64
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

object JWKSet {

  val rsaJwkReads: Reads[RSAjwk] = (
    (JsPath \ "kid").read[String] and
    (JsPath \ "alg").read[String] and
    (JsPath \ "n").read[String] and
    (JsPath \ "e").read[String]
  ) (RSAjwk.apply _)

  implicit object JwkReads extends Reads[JWK] {
    override def reads(json: JsValue): JsResult[JWK] = json match {
      case JsObject(fields) =>
        fields.get("kty") match {
          case Some(JsString(kty)) if kty == "RSA" => rsaJwkReads.reads(json)
          case Some(JsString(kty)) => JsError(s"Unsupported Key Type: $kty")
          case _ => JsError("Missing Key Type (kty)")
        }
      case _ =>
        JsError(s"Expected object, got ${Json.prettyPrint(json)}")
    }
  }

  implicit val jwkSetReads: Reads[JWKSet] = (JsPath \ "keys").read[List[JWK]].map(k => JWKSet(k))

}

case class JWKSet(keys: List[JWK])

sealed trait JWK {
  def kid: String

  def alg: String
}

object RSAjwk {
  val kty = "RSA"
}

final case class RSAjwk(
    kid: String,
    alg: String,
    n: String,
    e: String
) extends JWK {
  def publicKey: PublicKey = {
    val kf = KeyFactory.getInstance(RSAjwk.kty)
    val modulus = new BigInteger(1, Base64.decodeBase64(n))
    val exponent = new BigInteger(1, Base64.decodeBase64(e))
    kf.generatePublic(new RSAPublicKeySpec(modulus, exponent))
  }
}
