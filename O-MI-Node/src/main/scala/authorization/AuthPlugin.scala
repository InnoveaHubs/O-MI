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

package authorization

import java.lang.{Iterable => JavaIterable}

import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives.extract
import authorization.Authorization.{AuthorizationExtension, CombinedTest, UnauthorizedEx}
import database._
import types.omi._
import types.Path

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

sealed trait AuthorizationResult {
  def user: UserInfo
}

case class Authorized(user: UserInfo) extends AuthorizationResult {
  def instance: Authorized = this
}

case class Unauthorized(user: UserInfo = UserInfo()) extends AuthorizationResult {
  def instance: Unauthorized = this
}

case class Partial(authorized: JavaIterable[Path], user: UserInfo) extends AuthorizationResult

/**
  * Wraps a new O-MI request that is potentially modified from the original to pass authorization.
  * Can be used instead of [[Partial]] to define partial authorization.
  */
case class Changed(authorizedRequest: RequestWrapper, user: UserInfo) extends AuthorizationResult

/**
  * Implement one method of this interface and register the class through AuthApiProvider.
  */
trait AuthApi {


  /**
    * This can be overridden or isAuthorizedForType can be overridden instead. Has scala default implementation for
    * calling [[isAuthorizedForType]] function correctly.
    *
    * @param httpRequest http headers and other data as they were received to O-MI Node.
    */
  def isAuthorizedForRequest(httpRequest: HttpRequest, omiRequest: OmiRequest): AuthorizationResult = {
    omiRequest match {
      case odfRequest: OdfRequest =>

        val paths = odfRequest.odf.getLeafPaths // todo: refactor getLeafs to member lazy to re-use later

        odfRequest match {
          case r: PermissiveRequest => // Write or Response
            isAuthorizedForType(httpRequest, isWrite = true, paths.asJava)

          case r: OdfRequest => // All else odf containing requests (reads)
            isAuthorizedForType(httpRequest, isWrite = false, paths.asJava)

        }
      case _ => Unauthorized()
    }
  }


  /** This can be overridden or isAuthorizedForType can be overridden instead.
    *
    * @param httpRequest http headers and other data as they were received to O-MI Node.
    * @param isWrite     True if the request requires write permissions, false if it's read only.
    * @param paths       O-DF paths to all of the requested or to be written InfoItems.
    */
  def isAuthorizedForType(httpRequest: HttpRequest,
                          isWrite: Boolean,
                          paths: JavaIterable[Path]): AuthorizationResult = {
    Unauthorized()
  }

  /**
    * This is used if the parser is wanted to be skipped, e.g. for forwarding to the
    * original request to some authentication/authorization service.
    *
    * @param httpRequest http headers and other data as they were received to O-MI Node.
    * @param omiRequestXml contains the original request as received by the server.
    */
  def isAuthorizedForRawRequest(httpRequest: HttpRequest, rawSource: Source[String,_]): AuthorizationResult = {
    Unauthorized()
  }
}

trait AuthApiProvider extends AuthorizationExtension {
  val singleStores: SingleStores

  private[this] val authorizationSystems: mutable.Buffer[AuthApi] = mutable.Buffer()

  /**
    * Register authorization system that tells if the request is authorized.
    * Registration should be done once.
    */
  def registerApi(newAuthSystem: AuthApi): mutable.Buffer[AuthApi] = authorizationSystems += newAuthSystem


  // AuthorizationExtension implementation
  abstract override def makePermissionTestFunction: CombinedTest = combineWithPrevious(
    super.makePermissionTestFunction,
    extract { context => context.request } map { (httpRequest: HttpRequest) =>
      (orgOmiRequest: RequestWrapper) =>
        // for checking if path is infoitem or object
        val currentTree = Await.result(singleStores.getHierarchyTree(), orgOmiRequest.handleTTL)

        // helper function
        def convertToWrapper: Try[AuthorizationResult] => Try[RequestWrapper] = {
          case Success(Unauthorized(user0)) => Failure(UnauthorizedEx())
          case Success(Authorized(user0)) => {
            orgOmiRequest.user = user0.copy(remoteAddress = orgOmiRequest.user.remoteAddress)
            Success(orgOmiRequest)
          }
          case Success(Changed(reqWrapper, user0)) => {
            reqWrapper.user = user0
            orgOmiRequest.user = user0
            Success(reqWrapper)
          }
          case Success(Partial(maybePaths, user0)) => {
            orgOmiRequest.user = user0
            val newOdfOpt = for {
              paths: JavaIterable[Path] <- Option(maybePaths) // paths might be null
              // Rebuild the request having only `paths`
              pathTrees = currentTree.selectSubTree(paths.asScala.toSet)
              /*paths collect {
                case path: Path =>              // filter nulls out
                  currentTree.getSubTreeAsODF(paths) match { // figure out is it InfoItem or Object
                    case Some(nodeType) => createAncestors(nodeType)
                    case None => OdfObjects()
                  }
              }*/

            } yield pathTrees //.fold(OdfObjects())(_ union _)

            newOdfOpt match {
              case Some(newOdf) if newOdf.getObjects.nonEmpty =>
                val nODF = newOdf
                orgOmiRequest.unwrapped flatMap {
                  case r: ReadRequest => Success(r.copy(odf = nODF))
                  case r: SubscriptionRequest => Success(r.copy(odf = nODF))
                  case r: WriteRequest => Success(r.copy(odf = nODF))
                  case r: ResponseRequest => Success(r.copy(results =
                    r.results.headOption.map{ res => res.copy(odf = Some(nODF))}.toVector // TODO: make better copy logic?
                  ))
                  case r: AnyRef => Failure(new NotImplementedError(
                    s"Partial authorization granted for ${maybePaths.asScala.toSeq.mkString(", ")}, BUT request '${
                      r.getClass
                        .getSimpleName
                    }' not yet implemented in O-MI node."))
                }
              case _ => Failure(UnauthorizedEx())
            }
          }
          case f@Failure(exception) =>
            log.debug("Error while running AuthPlugins. => Unauthorized, trying next plugin", exception)
            Failure(UnauthorizedEx())
        }


        authorizationSystems.foldLeft[Try[(RequestWrapper, UserInfo)]](Failure(UnauthorizedEx())) { (lastTest,
                                                                                                     nextAuthApi) =>
          lastTest orElse {

            
            lazy val rawReqResult = convertToWrapper(
              Try {
                nextAuthApi.isAuthorizedForRawRequest(httpRequest, orgOmiRequest.rawSource)
              }
            )

            lazy val reqResult =
              orgOmiRequest.unwrapped flatMap { omiReq =>
                convertToWrapper(
                  Try {
                    nextAuthApi.isAuthorizedForRequest(httpRequest, omiReq)
                  }
                )
              }


            // Choose optimal test order
            orgOmiRequest match {
              case raw: RawRequestWrapper => (rawReqResult orElse reqResult).map(t => (t, t.user))
              case other: RequestWrapper => (reqResult orElse rawReqResult).map(t => (t, t.user))

            }
          }
        }
    }
  )
}

