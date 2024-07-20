package foxxy.auth

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.{JWT, JWTVerifier}
import com.password4j.{Argon2Function, Password}
import zio.ZIO.ifZIO
import zio.{IO, Task, ZIO, ZLayer}

import java.time.{Duration, Instant}
import java.util.UUID
import scala.util.{Success, Try}

case class AuthService():
  def encryptPassword(password: String): Task[String]                    = PasswordHashing.encryptPassword(password)
  def verifyPassword(password: String, passwordHash: String): Task[Unit] = PasswordHashing.verifyPassword(password, passwordHash)
  def generateJwt(userData: String): IO[Exception, String]               = Jwt.generate(userData)
  def verifyJwt(jwtToken: String): IO[Exception, String]                 = Jwt.verify(jwtToken)

  private object PasswordHashing:
    private final val MemoryInKib          = 12
    private final val NumberOfIterations   = 20
    private final val LevelOfParallelism   = 2
    private final val LengthOfTheFinalHash = 32
    private final val Type                 = com.password4j.types.Argon2.ID
    private final val Version              = 19

    private final val Argon2: Argon2Function =
      Argon2Function.getInstance(MemoryInKib, NumberOfIterations, LevelOfParallelism, LengthOfTheFinalHash, Type, Version)

    def encryptPassword(password: String): Task[String] =
      ZIO.attempt(Password.hash(password).`with`(Argon2).getResult)

    def verifyPassword(password: String, passwordHash: String): Task[Unit] =
      ifZIO(ZIO.attempt(Password.check(password, passwordHash) `with` PasswordHashing.Argon2))
        .apply(onTrue = ZIO.succeed(()), onFalse = ZIO.fail(Throwable("Unauthorized")))

  private object Jwt:
    private final val Issuer    = "foxxy"
    private final val ClaimName = "userEmail"

    private final val algorithm: Algorithm  = Algorithm.HMAC256("secret")
    private final val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(Issuer).build()

    def generate(claimName: String): IO[Exception, String] = {
      val now: Instant = Instant.now()
      Try(
        JWT
          .create()
          .withIssuer(Issuer)
          .withClaim(ClaimName, claimName)
          .withIssuedAt(now)
          .withExpiresAt(now.plus(Duration.ofHours(1)))
          .withJWTId(UUID.randomUUID().toString)
          .sign(algorithm)
      ) match {
        case Success(createdJwt) => ZIO.succeed(createdJwt)
        case _                   => ZIO.fail(RuntimeException("Problem with JWT generation!"))
      }
    }

    def verify(jwtToken: String): IO[Exception, String] =
      Try(verifier.verify(jwtToken)) match {
        case Success(decodedJwt) => ZIO.succeed(decodedJwt.getClaim(ClaimName).asString())
        case _                   => ZIO.fail(new Exception("Unauthorized"))
      }

object AuthService:
  val live = ZLayer.derive[AuthService]
