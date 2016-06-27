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
package types
package OmiTypes

import java.lang.Iterable
import java.sql.Timestamp
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.concurrent.duration._
import scala.language.existentials
import scala.xml.NodeSeq

import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import parsing.xmlGen.xmlTypes.{ObjectsType, OmiEnvelope}
import responses.OmiGenerator.odfMsg
import types.OdfTypes._


/**
  * Trait that represents any Omi request. Provides some data that are common
  * for all omi requests.
  */
sealed trait OmiRequest {
  def ttl: Duration
  def callback: Option[String]
  def hasCallback: Boolean = callback.isDefined && callback.getOrElse("").nonEmpty
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope 
  implicit def asXML : NodeSeq= omiEnvelopeToXML(asOmiEnvelope)
}
sealed trait PermissiveRequest
sealed trait OdfRequest {
  def odf : OdfObjects
}

/**
  * Trait for subscription like classes. Offers a common interface for subscription types.
  */
trait SubLike {
  // Note: defs can be implemented also as val and lazy val
  def interval: Duration
  def ttl: Duration
  def isIntervalBased : Boolean  = interval >= 0.milliseconds
  def isEventBased: Boolean = interval == -1.seconds
  def ttlToMillis: Long = ttl.toMillis
  def intervalToMillis: Long = interval.toMillis
  def isImmortal: Boolean  = ! ttl.isFinite
  require(interval == -1.seconds || interval >= 0.seconds, s"Invalid interval: $interval")
  require(ttl >= 0.seconds, s"Invalid ttl, should be positive (or +infinite): $interval")
}

/** One-time-read request
  *
  **/
case class ReadRequest(
  ttl: Duration,
  odf: OdfObjects ,
  begin: Option[Timestamp ] = None,
  end: Option[Timestamp ] = None,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[String ] = None
) extends OmiRequest with OdfRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = {
    xmlTypes.ReadRequest(
      None,
      Nil,
      Some(
        scalaxb.DataRecord(
          Some("omi.xsd"),
          Some("msg"),
          odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope))
        )
      ),
      callback.map{ 
        addr => 
          new java.net.URI(addr)
      },
      Some("odf"),
      xmlTypes.Node,
      None,
      oldest,
      begin.map{
        timestamp: Timestamp => 
        timestampToXML(timestamp)
      },
      end.map{
        timestamp : Timestamp => 
        timestampToXML(timestamp)
      },
      newest
    )
  }
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttl.toSeconds)
}

/** Poll request
  *
  **/
case class PollRequest(
  ttl: Duration,
  callback: Option[String ] = None,
  requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty
) extends OmiRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    requestIDs.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq,
    None,
    callback.map{ addr => new java.net.URI(addr)},
    None,
    xmlTypes.Node,
    None
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttl.toSeconds)
}

/** Subscription request for starting subscription
  *
  **/
case class SubscriptionRequest(
  ttl: Duration,
  interval: Duration,
  odf: OdfObjects,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[String ] = None
) extends OmiRequest with SubLike with OdfRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callback.map{ addr => new java.net.URI(addr)},
    Some("odf"),
    xmlTypes.Node,
    Some(interval.toSeconds)
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttl.toSeconds)
}


/** Write request
  *
  **/
case class WriteRequest(
  ttl: Duration,
  odf: OdfObjects,
  callback: Option[String ] = None
) extends OmiRequest with OdfRequest with PermissiveRequest{
  implicit def asWriteRequest : xmlTypes.WriteRequest = xmlTypes.WriteRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callback.map{ addr => new java.net.URI(addr)},
    Some("odf")
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asWriteRequest, ttl.toSeconds)
}


/** Response request, contains result for other requests
  *
  **/
case class ResponseRequest(
  results: OdfTreeCollection[OmiResult],
  ttl: Duration = Duration.Inf
) extends OmiRequest with OdfRequest with PermissiveRequest{
  val callback : Option[String] = None
  def odf : OdfObjects= results.foldLeft(OdfObjects()){
    _ union _.odf.getOrElse(OdfObjects())
  }
  implicit def asResponseListType : xmlTypes.ResponseListType = xmlTypes.ResponseListType(results.map{ result => result.asRequestResultType}.toVector.toSeq: _*)
   
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asResponseListType, ttl.toSeconds)
} 

/** Cancel request, for cancelling subscription.
  *
  **/
case class CancelRequest(
  ttl: Duration,
  requestID: OdfTreeCollection[Long ] = OdfTreeCollection.empty
) extends OmiRequest {
  implicit def asCancelRequest : xmlTypes.CancelRequest = xmlTypes.CancelRequest(
    None,
    requestID.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq
  )
  def callback : Option[String] = None
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asCancelRequest, ttl.toSeconds)
}

/** Result of a O-MI request
  *
  **/
case class OmiResult(
  returnValue: OmiReturn,
  requestID: OdfTreeCollection[Long ] = OdfTreeCollection.empty,
  odf: Option[OdfTypes.OdfObjects] = None
){
    
  implicit def asRequestResultType : xmlTypes.RequestResultType = xmlTypes.RequestResultType(
    xmlTypes.ReturnType(
      returnValue.value,
      returnValue.returnCode,
      returnValue.description,
      Map.empty
    ),
    requestID.headOption.map{
      id => xmlTypes.IdType(id.toString)
    },
    odf.map{ 
      objects =>
        scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( objects.asObjectsType , None, Some("Objects"), defaultScope ) ) ) 
    },
    None,
    None,
    odf.map{ objs => "odf" }
  )
} 

case class OmiReturn(
  value: String,
  returnCode: String,
  description: Option[String] = None
)
