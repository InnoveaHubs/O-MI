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
package agentSystem

import akka.actor.{ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import types.omi._

import scala.concurrent.{Future, Promise}
/**
  * Commands that can be received from InternalAgentLoader.
sealed trait InternalAgentCmd

case class Start() extends InternalAgentCmd

case class Restart() extends InternalAgentCmd

case class Stop() extends InternalAgentCmd

trait InternalAgentResponse

trait InternalAgentSuccess extends InternalAgentResponse

case class CommandSuccessful() extends InternalAgentSuccess
*/

/**
 * Custom exception type for exceptions that can only be handle only restarting
 * agent. Uses restart as SupervisorStrategy. Any other non-fatal exception
 * causes agent to be stopped and fatal exceptions are escalated.
 */
class InternalAgentFailure(message: String) extends Exception(message) {
  def this(message:String, cause: Throwable){
    this(message)
    initCause(cause)
  }
  def this(cause: Throwable){
    this(Option(cause).map(_.toString).orNull,cause)
  }
  def this(){
    this(null:String)
  }
}
/*with
  InternalAgentResponse
*/
class InternalAgentConfigurationFailure(message: String) extends Exception(message) {
  def this(message:String, cause: Throwable){
    this(message)
    initCause(cause)
  }
  def this(cause: Throwable){
    this(Option(cause).map(_.toString).orNull,cause)
  }
  def this(){
    this(null:String)
  }
}

//class CommandFailed(msg: String, exp: Option[Throwable]) extends InternalAgentFailure(msg, exp)

//case class StopFailed(msg: String, exp: Option[Throwable]) extends CommandFailed(msg, exp)

//case class StartFailed(msg: String, exp: Option[Throwable]) extends InternalAgentFailure(msg, exp)

//sealed trait ResponsibleAgentMsg

case class ResponsibleWrite(promise: Promise[ResponseRequest], write: WriteRequest)

case class AgentConfigurationException(msg: String, exp: Option[Throwable] = None)
  extends Exception(msg, exp.orNull)

abstract class ScalaInternalAgentTemplate(
                                           protected val requestHandler: ActorRef,
                                           protected val dbHandler: ActorRef
                                         ) extends ScalaInternalAgent

trait ScalaInternalAgent extends InternalAgent with ActorLogging {

  import context.dispatcher

  protected def requestHandler: ActorRef

  protected def dbHandler: ActorRef

  //These need to be implemented
  /*
  override def preStart: Unit = CommandSuccessful()

  override def postStop: Unit = CommandSuccessful()
  def receive: PartialFunction[Any, Unit] = {
    case any: Any => unhandled(any)
  }
  */

  final def agentSystem: ActorRef = context.parent

  final def name: String = self.path.name

  final def writeToDB(write: WriteRequest): Future[ResponseRequest] = requestFromDB(write)

  final def readFromDB(read: ReadRequest): Future[ResponseRequest] = requestFromDB(read)

  final def requestFromDB(request: OdfRequest): Future[ResponseRequest] = {
    // timeout for the write request, which means how long this agent waits for write results
    implicit val timeout: Timeout = Timeout(request.handleTTL)
    // Execute the request, execution is asynchronous (will not block)
    val si = ActorSenderInformation(name, self)
    val requestWithSenderInfo = request.withSenderInformation(si)
    (dbHandler ? requestWithSenderInfo).mapTo[ResponseRequest]
  }

  final def requestFromNode(request: OdfRequest): Future[ResponseRequest] = {
    // timeout for the write request, which means how long this agent waits for write results
    implicit val timeout: Timeout = Timeout(request.handleTTL)
    // Execute the request, execution is asynchronous (will not block)
    val si = ActorSenderInformation(name, self)
    val requestWithSenderInfo = request.withSenderInformation(si)
    (dbHandler ? requestWithSenderInfo).mapTo[ResponseRequest]
  }

  final def respond(msg: Any): Unit = {
    val senderRef = sender()
    senderRef ! msg
  }

  final def respondFuture(msgFuture: Future[Any]): Unit = {
    val senderRef = sender()
    msgFuture.map {
      any => senderRef ! any
    }
    msgFuture.failed.foreach{
      case e: Exception =>
        log.error(e, s"RespondFuture caught: ")
    }
  }

  final def writeToNode(write: WriteRequest): Future[ResponseRequest] = writeToDB(write)

  /*
  @deprecated("Use Actor's preRestart and postRestart methods instead.", "o-mi-node-0.9.0")
  def restart: InternalAgentResponse = {
    CommandSuccessful()
  }

  @deprecated("Use Actor's preStart method instead.", "o-mi-node-0.9.0")
  def start: InternalAgentResponse = {
    CommandSuccessful()
  }

  @deprecated("Use Actor's postStop method instead.", "o-mi-node-0.9.0")
  def stop: InternalAgentResponse = {
    CommandSuccessful()
  }*/
}
