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

import scala.util.control.NonFatal

import agentSystem.AgentResponsibilities._
import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, ActorInitializationException, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._

import http.CLICmds._
import types.Path

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class NewCLI(ip: String, cliRef: ActorRef)

object AgentEvents {

  case class AgentStarted(agentName: AgentName)

  case class AgentStopped(agentName: AgentName)

  case class NewAgent(agentName: AgentName, actorRef: ActorRef, responsibilities: Seq[AgentResponsibility])

}

object AgentSystem {
  def props(
             dbHandler: ActorRef,
             requestHandler: ActorRef,
             settings: AgentSystemConfigExtension
           ): Props = Props(
    {
      val as = new AgentSystem()(
        dbHandler,
        requestHandler,
        settings
      )
      as.start()
      as
    })
}

class AgentSystem()(
  protected val dbHandler: ActorRef,
  protected val requestHandler: ActorRef,
  protected implicit val settings: AgentSystemConfigExtension
)
  extends InternalAgentLoader
    with InternalAgentManager
    //with ResponsibleAgentManager
    //with DBPusher
{
  protected[this] val agents: MutableMap[AgentName, AgentInfo] = MutableMap.empty

  def receive: Actor.Receive = {
    case nC: NewCLI => sender ! connectCLI(nC.ip, nC.cliRef)
    case start: StartAgentCmd => handleStart(start)
    case stop: StopAgentCmd => handleStop(stop)
    case ListAgentsCmd() => sender() ! agents.values.toVector
    case Terminated(agent: ActorRef) => agentStopped(agent)
  }
}

sealed trait AgentInfoBase {
  def name: AgentName

  def classname: String

  def config: Config

  def responsibilities: Seq[AgentResponsibility]
}

case class AgentConfigEntry(
                             name: AgentName,
                             classname: String,
                             config: Config,
                             responsibilities: Seq[AgentResponsibility],
                             language: Option[Language]
                           ) extends AgentInfoBase {
  def toInfo(agentRef: ActorRef): AgentInfo = {
    AgentInfo(
      name,
      classname,
      config,
      Some(agentRef),
      running = true,
      responsibilities,
      language.get
    )
  }
}

object AgentConfigEntry {

  def apply(agentConfig: Config): AgentConfigEntry = {
    val classname: String = agentConfig.getString(s"class")
    val name: String = agentConfig.getString(s"name")
    val language: Option[Language] = Try {
      agentConfig.getString(s"language")
    }.toOption.map(Language(_))

    val responsibilities: Seq[AgentResponsibility] = Try {
      val responsibilityObj = agentConfig.getObject(s"responsible")
      val pathStrings : Iterable[String] = responsibilityObj.keySet.asScala
      val responsibilityConfig = responsibilityObj.toConfig
      pathStrings.map {
        pathStr: String =>
          AgentResponsibility(
            name,
            Path(pathStr),
            RequestFilter(responsibilityConfig.getString(pathStr))
          )
      }.toVector
    } match {
      case Success(s) => s
      case Failure(e: Missing) => Seq.empty
      case Failure(e) => throw e
    }
    val config = agentConfig
    AgentConfigEntry(name, classname, config, responsibilities, language)
  }
}

case class AgentInfo(
                      name: AgentName,
                      classname: String,
                      config: Config,
                      agent: Option[ActorRef],
                      running: Boolean,
                      responsibilities: Seq[AgentResponsibility],
                      language: Language
                    ) extends AgentInfoBase {
  def toConfigEntry: AgentConfigEntry = {
    AgentConfigEntry(
      name,
      classname,
      config,
      responsibilities,
      Some(language)
    )
  }

}

sealed trait Language {}

final case class Unknown(lang: String) extends Language

final case class Scala() extends Language

final case class Java() extends Language

object Language {
  def apply(str: String): Language = str.toLowerCase() match {
    case "java" => Java()
    case "scala" => Scala()
    case str: String => Unknown(str)
  }
}

trait BaseAgentSystem extends Actor with ActorLogging {
  /** Container for internal agents */
  protected def agents: MutableMap[AgentName, AgentInfo]

  protected implicit def settings: AgentSystemConfigExtension

  implicit val timeout: Timeout = Timeout(5 seconds)
  protected val connectedCLIs: MutableMap[String, ActorRef] = MutableMap.empty
  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case ActorInitializationException( actor, message, cause) =>
        log.warning(s"Agent ${sender().path.name} encountered exception during creation. $cause Agent is stopped.")
        SupervisorStrategy.Stop
      case fail: InternalAgentFailure =>
        log.warning(s"InternalAgent failure from ${sender().path.name}: $fail Agent is restarted.")
        SupervisorStrategy.Restart
      case NonFatal(t) => 
        log.warning(s"InternalAgent ${sender().path.name} caught unhandled non-fatal expection $t. Agent is stopped.")
        SupervisorStrategy.Stop
      case t: Throwable =>
        log.warning(s"Agent ${sender().path.name} encountered exception $t")
        super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
    }
}
