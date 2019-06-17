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
import java.net.URLEncoder
import java.sql.Timestamp
import java.util.Date

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
//import scala.xml.NodeSeq

import akka.util.ByteString
import akka.actor.{ActorSystem, Terminated}
import akka.event.LogSource
import org.slf4j.LoggerFactory
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
//import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.{Http, HttpExt}
//import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._

import http.OmiConfigExtension
import responses.CallbackHandler._
import types.OmiTypes._
import database.SingleStores


import http.WebSocketUtil

object CallbackHandler {

  case class CurrentConnection(identifier: Int, handler: SendHandler)

  val supportedProtocols = Vector("http", "https")
  type SendHandler = ResponseRequest => Future[Unit]

  // Base error
  sealed class CallbackFailure(msg: String, callback: Callback) extends Exception(msg)

  // Errors
  case class HttpError(status: StatusCode, callback: HTTPCallback) extends
    CallbackFailure(s"Received HTTP status $status from ${callback.uri}.", callback)

  case class ProtocolNotSupported(protocol: String, callback: Callback) extends
    CallbackFailure(s"$protocol is not supported, use one of " + supportedProtocols.mkString(", "), callback)

  case class ForbiddenLocalhostPort(callback: Callback) extends
    CallbackFailure(s"Callback address is forbidden.", callback)

  case class MissingConnection(callback: WebSocketCallback) extends
    CallbackFailure(s"CurrentConnection not found for ${
      callback match {
        case CurrentConnectionCallback(identifier) => identifier
        case WSCallback(uri) => uri
      }
    }", callback)

}

/**
  * Handles sending data to callback addresses
  */
class CallbackHandler(
                       protected val settings: OmiConfigExtension,
                       protected val singleStores: SingleStores
                     )(
                       protected implicit val system: ActorSystem,
                       protected implicit val materializer: ActorMaterializer
                     ) extends WebSocketUtil {

  import system.dispatcher

  protected val httpExtension: HttpExt = Http(system)
  val portsUsedByNode: Seq[Int] = settings.ports.values.toSeq
  val whenTerminated: Future[Terminated] = system.whenTerminated

  protected def currentTimestamp = new Timestamp(new Date().getTime)

  implicit val logSource: LogSource[CallbackHandler] = (requestHandler: CallbackHandler) => requestHandler.toString

  //protected val log: LoggingAdapter = Logging(system, this)
  protected val log = LoggerFactory.getLogger(classOf[CallbackHandler])

  val webSocketConnections: MutableMap[String, SendHandler] = MutableMap.empty
  val currentConnections: MutableMap[Int, CurrentConnection] = MutableMap.empty

  private[this] def sendHttp(callback: HTTPCallback,
                             request: OmiRequest,
                             ttl: Duration): Future[Unit] = {
    val tryUntil = new Timestamp(new Date().getTime + (ttl match {
      case ttl: FiniteDuration => ttl.toMillis
      case _ => settings.callbackTimeout.toMillis
    }))


    val address = callback.uri
    val encodedSource = (request match {
        case response: ResponseRequest =>
          Source.fromFutureSource(
            singleStores.getRequestInfo(response).map(versions =>
              response.asXMLSourceWithVersion(versions)))
        case r => r.asXMLSource

      }).map{str: String => URLEncoder.encode(str,"UTF-8")}

    val formSource = Source.single("msg=").concat(encodedSource)
      .map{str: String => ByteString(str,"UTF-8")}
      .map(HttpEntity.ChunkStreamPart.apply)

    val httpEntity = HttpEntity.Chunked(ContentTypes.`application/x-www-form-urlencoded`,formSource)
    //val httpEntity = FormData(("msg", request.asXML.toString)).toEntity(HttpCharsets.`UTF-8`)

    val httpRequest = RequestBuilding.Post(address, httpEntity)

    log.debug(
      s"Trying to send POST request to $address, will keep trying until $tryUntil."
    )

    val check: HttpResponse => Future[Unit] = { response =>
      if (response.status.isSuccess) {
        //TODO: Handle content of response, possible piggybacking
        log.debug(
          s"Successful send POST request to $address with ${response.status}"
        )
        response.discardEntityBytes()
        Future.successful(())
      } else {
        response.discardEntityBytes()
        log.warn(s"Http status: ${response.status}")
        Future failed HttpError(response.status, callback)
      }
    }

    def trySend = httpExtension.singleRequest(httpRequest) //httpHandler(request)

    val retry = retryUntilWithCheck[HttpResponse, Unit](
      settings.callbackDelay,
      tryUntil
    )(check)(trySend)

    retry.failed.foreach {
      _: Throwable =>
        system.log.warning(
                            s"Failed to send POST request to $address after trying until ttl ended."
                          )
    }
    retry

  }

  private def retryUntilWithCheck[T, U](delay: FiniteDuration, tryUntil: Timestamp, attempt: Int = 1)
                                       (check: T => Future[U])
                                       (creator: => Future[T]): Future[U] = {
    import system.dispatcher // execution context for futures
    val future = creator
    future.flatMap {
      check
    }.recoverWith {
      case e if tryUntil.after(currentTimestamp) && !whenTerminated.isCompleted =>
        log.debug(
          s"Retrying after $delay. Will keep trying until $tryUntil. Attempt $attempt."
        )
        Thread.sleep(delay.toMillis)
        retryUntilWithCheck[T, U](delay, tryUntil, attempt + 1)(check)(creator)
    }
  }

  /**
    * Send callback O-MI message to `address`
    *
    * @param callback    Callback defining were to send request.
    * @param omiResponse O-MI response to be send.
    * @return future for the result of the callback is returned without blocking the calling thread
    */
  def sendCallback(callback: DefinedCallback, omiResponse: ResponseRequest): Future[Unit] = callback match {
    case cb: HTTPCallback =>
      sendHttp(cb, omiResponse, omiResponse.ttl)
    case cb: CurrentConnectionCallback =>
      sendCurrentConnection(cb, omiResponse, omiResponse.ttl)
    case cb: WSCallback =>
      sendWS(cb, omiResponse, omiResponse.ttl)
    case _ => Future.failed(new CallbackFailure("Unknown callback type", callback))

  }

  private[this] def sendWS(callback: WSCallback, request: ResponseRequest, ttl: Duration): Future[Unit] = {
    webSocketConnections.get(callback.address).map {
      handler: SendHandler =>

        log.debug(
          s"Trying to send response to WebSocket connection ${callback.address}"
        )
        val f = handler(request)
        f.onComplete {
          case Success(_) =>
            log.debug(
              s"Response  send successfully to WebSocket connection ${callback.address}"
            )
          case Failure(t: Throwable) =>
            log.debug(
              s"Response send  to WebSocket connection ${callback.address} failed, ${
                t.getMessage
              }. Connection removed."
            )
            webSocketConnections -= callback.address
        }
        f
    }.getOrElse(
      Future failed MissingConnection(callback)
    )
  }

  private[this] def sendCurrentConnection(callback: CurrentConnectionCallback,
                                          request: ResponseRequest,
                                          ttl: Duration): Future[Unit] = {
    currentConnections.get(callback.identifier).map {
      wsConnection: CurrentConnection =>

        log.debug(
          s"Trying to send response to current connection ${callback.identifier}"
        )
        val f = wsConnection.handler(request)
        f.onComplete {
          case Success(_) =>
            log.debug(
              s"Response  send successfully to current connection ${callback.identifier}"
            )
          case Failure(t: Throwable) =>
            log.debug(
              s"Response send  to current connection ${callback.identifier} failed, ${
                t.getMessage
              }. Connection removed."
            )
            currentConnections -= callback.identifier
        }
        f
    }.getOrElse(
      Future failed MissingConnection(callback)
    )
  }

  /** TODO: Test needs NodeSeq messaging
    * Send callback xml message containing `data` to `address`
    *
    * @param address           Uri that tells the protocol and address for the callback
    * @param currentConnection Optional value that maybe contains a active connection
    * @return future for the result of the callback is returned without blocking the calling thread
    *         def sendCallback( callback: DefinedCallback,
    *         omiMessage: OmiRequest,
    *         ttl: Duration
    *         ): Future[Unit] = {
    *         *
    *         sendHttp(callback, omiMessage, ttl)
    *         }
    */

  def createCallbackAddress(
                             address: String,
                             currentConnection: Option[CurrentConnection] = None
                           ): Try[Callback] = {
    address match {
      case "0" if currentConnection.nonEmpty =>
        Try {
          val cc = currentConnection.getOrElse(
            throw new Exception("Impossible empty CurrentConnection Option")
          )
          if (currentConnections.get(cc.identifier).isEmpty)
            currentConnections += cc.identifier -> cc
          CurrentConnectionCallback(cc.identifier)
        }
      case "0" if currentConnection.isEmpty =>
        Try {
          throw new Exception("Callback 0 not supported with http/https try using ws(websocket) instead")
        }
      case other: String =>
        Try {
          val uri = Uri(address)
          val hostAddress = uri.authority.host.address
          // Test address validity (throws exceptions when invalid)
          InetAddress.getByName(hostAddress)
          val scheme = uri.scheme
          webSocketConnections.get(uri.toString).map {
            wsConnection => WSCallback(uri)
          }.getOrElse {
            scheme match {
              case "http" | "https" => HTTPCallback(uri)
              case "ws" | "wss" =>
                val handler = createWebsocketConnectionHandler(uri)
                webSocketConnections += uri.toString -> handler
                WSCallback(uri)
            }
          }

        }
    }
  }

  private def createWebsocketConnectionHandler(uri: Uri) = {
    val queueSize = settings.websocketQueueSize
    val (outSource, futureQueue) =
      peekMatValue(Source.queue[ws.Message](queueSize, OverflowStrategy.fail))

    // keepalive? http://doc.akka.io/docs/akka/2.4.8/scala/stream/stream-cookbook.html#Injecting_keep-alive_messages_into_a_stream_of_ByteStrings
    
    val queue = WSQueue(futureQueue)

    val wsSink: Sink[Message, Future[akka.Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          log.warn(s"Received WS message from $uri. Message is ignored. ${message.text}")
        case _ =>
      }

    //def sendHandler = (response: ResponseRequest) => queue.send(Future(response.asXMLSource)) map { _ => () }

    val wsFlow = httpExtension.webSocketClientFlow(WebSocketRequest(uri))
    val (upgradeResponse, closed) = outSource.viaMat(wsFlow)(Keep.right)
      .toMat(wsSink)(Keep.both)
      .run()

    upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.OK) {
        log.info(s"Successfully connected WebSocket callback to $uri")
        Future.successful(akka.Done)
      } else {
        val msg = s"connection to  WebSocket callback: $uri failed: ${upgrade.response.status}"
        log.warn(msg)
        Future.failed(new Exception(msg))
      }
    }

    queue.sendHandler
  }
}
