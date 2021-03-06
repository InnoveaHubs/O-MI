package types
package odf

import utils._
import java.sql.Timestamp
import akka.stream.alpakka.xml._
import types._

package object `parsing` {
  def solveTimestamp(dateTime: Option[Timestamp], unixTime: Option[Timestamp], receiveTime: Timestamp): Timestamp = {
    (dateTime,unixTime) match{
      case (Some(dTs), Some(uTs)) => dTs
      case (Some(ts),None) => ts
      case (None,Some(ts)) => ts
      case (None,None) => receiveTime
    }

  }
  def unexpectedEventHandle(msg: String, event: ParseEvent, builder: EventBuilder[_]): EventBuilder[_] ={
    event match {
      case content: TextEvent if content.text.replaceAll("\\s","").nonEmpty => throw ODFParserError(s"Unexpected text content $msg")
      case start: StartElement => throw ODFParserError(s"Unexpected start of ${start.localName} element $msg")
      case end: EndElement =>throw ODFParserError(s"Unexpected end of ${end.localName} element $msg")
      case EndDocument =>throw ODFParserError(s"Unexpected end of document $msg")
      case StartDocument =>throw ODFParserError(s"Unexpected start of document $msg")
      case other: ParseEvent => builder 
    }
  }
}
