/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
package responses

import java.net.InetAddress
import java.sql.Timestamp
import java.util.Date

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Try,Success,Failure}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, Materializer}

/**
 * Handles sending data to callback addresses 
 */
object CallbackHandlers {
  val supportedProtocols = Vector("http", "https")

  // Base error
  sealed class CallbackFailure(msg: String, callback: Uri) extends Exception(msg)

  // Errors
  case class   HttpError(status: StatusCode, callback: Uri ) extends
    CallbackFailure(s"Received HTTP status $status from $callback.", callback)

  case class  ProtocolNotSupported(protocol: String, callback: Uri) extends
    CallbackFailure(s"$protocol is not supported, use one of " + supportedProtocols.mkString(", "), callback)

  case class  ForbiddenLocalhostPort( callback: Uri)  extends
    CallbackFailure(s"Callback address is forbidden.", callback)

  protected def currentTimestamp =  new Timestamp( new Date().getTime )
  implicit val system = http.Boot.system
  import system.dispatcher // execution context for futures
  val settings = http.Boot.settings
  val httpExt = Http(system)
  implicit val mat: Materializer = ActorMaterializer()

  private def log(implicit system: ActorSystem) = system.log
  private[this] def sendHttp( address: Uri,
                              data: xml.NodeSeq,
                              ttl: Duration): Future[Unit] = {
    val tryUntil =  new Timestamp( new Date().getTime + (ttl match {
      case ttl: FiniteDuration => ttl.toMillis
      case _ => http.Boot.settings.callbackTimeout.toMillis
    }))

    def newTTL = Duration(tryUntil.getTime - currentTimestamp.getTime, MILLISECONDS )

    val request = RequestBuilding.Post(address, data)

    system.log.info(
      s"Trying to send POST request to $address, will keep trying until $tryUntil."
    )

    val check : HttpResponse => Future[Unit] = { response =>
        if (response.status.isSuccess){
          //TODO: Handle content of response, possible piggypacking
          system.log.info(
            s"Successful send POST request to $address."
          )
          Future.successful(())
        } else Future failed HttpError(response.status, address)
    }

    def trySend = httpExt.singleRequest(request)//httpHandler(request)

    val retry = retryUntilWithCheck[HttpResponse, Unit](
            settings.callbackDelay,
            tryUntil
          )(check)(trySend)

    retry.onFailure{
      case e : Throwable=>
        system.log.warning(
          s"Failed to send POST request to $address after trying until ttl ended."
        )
    }

    retry

  }

  private def retryUntilWithCheck[T,U]( delay: FiniteDuration, tryUntil: Timestamp, attempt: Int = 1 )( check: T => Future[U])( creator: => Future[T] ) : Future[U] = {
    val future = creator
    future.flatMap{ check }.recoverWith{
      case e if tryUntil.after( currentTimestamp ) && !system.isTerminated => 
        system.log.debug(
          s"Retrying after $delay. Will keep trying until $tryUntil. Attempt $attempt."
        )
        Thread.sleep(delay.toMillis)
        retryUntilWithCheck[T,U](delay,tryUntil, attempt + 1 )(check )(creator )
    }
  }
  /**
   * Send callback O-MI message to `address`
   * @param address Uri that tells the protocol and address for the callback
   * @param data xml data to send as a callback
   * @return future for the result of the callback is returned without blocking the calling thread
   */
  def sendCallback( address: String, omiMessage: types.OmiTypes.OmiRequest): Future[Unit] =
    sendCallback(address, omiMessage.asXML, omiMessage.ttl)

  /**
   * Send callback xml message containing `data` to `address`
   * @param address Uri that tells the protocol and address for the callback
   * @param data xml data to send as a callback
   * @return future for the result of the callback is returned without blocking the calling thread
   */
  def sendCallback( address: String,
                    data: xml.NodeSeq,
                    ttl: Duration
                    ): Future[Unit] = {

    checkCallbackUri(address).flatMap{ uri =>
      sendHttp(uri, data, ttl)
    }
  }

  def checkCallbackUri( callback: String ): Future[Uri] = for { 
    uri <- Future fromTry Try{Uri(callback)}
    hostAddress = uri.authority.host.address

    // Test address validity (throws exceptions when invalid)
    ipAddress <- Future fromTry Try{InetAddress.getByName(hostAddress)}

    portsUsedByNode = settings.ports.values.toSeq
    validScheme = supportedProtocols.contains(uri.scheme)
    validPort = hostAddress != "localhost" || !portsUsedByNode.contains(uri.effectivePort)

    result <- (validScheme, validPort) match {
      case ( true, true ) =>
        Future successful uri

      case ( false, _ ) =>
        Future failed ProtocolNotSupported(uri.scheme,uri)

      case ( true, false ) =>
        Future failed ForbiddenLocalhostPort(uri)
    }

  } yield result
}
