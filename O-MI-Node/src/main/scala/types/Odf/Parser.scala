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
package odf

import java.io.File
import java.sql.Timestamp

import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._

import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, NodeSeq}

/** Parser for data in O-DF format */
object ODFParser extends parsing.Parser[OdfParseResult] {
  val schemaName = "odf.xsd"

  protected[this] override def schemaPath: Array[Source] = Array[Source](
    new StreamSource(getClass.getClassLoader.getResourceAsStream("odf.xsd"))
  )

  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/
  /**
    * Public method for parsing the xml file into OdfParseResults.
    *
    * @param file XML formatted file to be parsed. Should be in O-DF format.
    * @return OdfParseResults
    */
  def parse(file: File): OdfParseResult = {
    val parsed = Try(
      XMLParser.loadFile(file)
    )
    parseTry(parsed)

  }

  /**
    * Public method for parsing the xml string into OdfParseResults.
    *
    * @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
    * @return OdfParseResults
    */
  def parse(xml_msg: String): OdfParseResult = {
    val parsed = Try(
      XMLParser.loadString(xml_msg)
    )

    parseTry(parsed)
  }

  private def parseTry(parsed: Try[Elem]): OdfParseResult = {
    parsed match {
      case Success(root) => parse(root)
      case Failure(f) => Left(Vector(ScalaXMLError(f.getMessage)).asJava)
    }
  }

  /**
    * Public method for parsing the xml structure into OdfParseResults.
    *
    * @param root xml.Node to be parsed. Should be in O-DF format.
    * @return OdfParseResults
    */
  def parse(root: xml.Node): OdfParseResult = {
    schemaValidation(root) match {
      case errors: Seq[ParseError] if errors.nonEmpty =>

        Left(errors.asJava)
      case empty: Seq[ParseError] if empty.isEmpty =>

        val requestProcessTime = currentTime()

        Try {
          xmlGen.scalaxb.fromXML[ObjectsType](root)
        } match {
          case Failure(e) =>
            e.printStackTrace()
            Left(Vector(ScalaxbError(e.getMessage)).asJava)

          case Success(objects) =>
            Try {
              parseObjects(objects, requestProcessTime)
            } match {
              case Success(odf) => Right(odf)
              case Failure(e) =>
                e.printStackTrace()
                Left(Vector(ODFParserError(e.getMessage)).asJava)
            }
        }
    }
  }

  private[this] def parseAttributes(
                                     attributes: Map[String, DataRecord[Any]]
                                   ): HashMap[String, String] = {
    HashMap(
      attributes.map {
        case (key: String, dr: DataRecord[Any]) =>
          val k = if (key.startsWith("@")) {
            key.tail
          } else key
          k -> dr.value.toString
      }.toSeq: _*
    )
  }

  private[this] def parseObjects(
                                  objects: ObjectsType,
                                  requestProcessTime: Timestamp
                                ): ImmutableODF = {
    val path = new Path("Objects")
    val objs = Objects(
      objects.version,
      parseAttributes(objects.attributes - "@version")
    )
    val subtree: Vector[Node] = objects.ObjectValue.flatMap {
      obj => parseObject(requestProcessTime, obj, path)
    }.toVector
    ImmutableODF(Vector(objs) ++ subtree)
  }

  private[this] def validateId(
                                stringId: String
                              ): Option[String] = {
    stringId.trim match {
      case "" => None
      case trimmedName: String => Some(trimmedName)
    }
  }

  //private[this] def validateId( //TODO remove?, Unused
  //                              optionId: Option[String]
  //                            ): Option[String] = for {
  //  head <- optionId
  //  validated <- validateId(head)
  //} yield validated

  private[this] def parseObject(
                                 requestProcessTime: Timestamp,
                                 obj: ObjectType,
                                 path: Path
                               ): Vector[Node] = {

    val ids = obj.id.map { qlmIdType => parseQlmID(qlmIdType) }.toVector
    val npath = path / ids.headOption.map {
      id: QlmID => id.copy()
    }.getOrElse(
      throw new IllegalArgumentException("No <id> on object: " + obj.id.toString)
    )

    val descriptions = obj.description.map {
      des => new Description(des.value, des.lang)
    }.toSet
    val odfObj = Object(
      ids,
      npath,
      obj.typeValue,
      descriptions,
      parseAttributes(obj.attributes - "@type")
    )
    val iIs: Vector[InfoItem] = obj.InfoItem.map {
      item => parseInfoItem(requestProcessTime, item, npath)
    }.toVector
    val subtree: Vector[Node] = obj.ObjectValue.flatMap {
      child => parseObject(requestProcessTime, child, npath)
    }.toVector
    iIs ++ subtree ++ Vector(odfObj)
  }

  private[this] def parseInfoItem(
                                   requestProcessTime: Timestamp,
                                   item: InfoItemType,
                                   path: Path
                                 ): InfoItem = {

    // TODO: support many names from item.otherName
    val nameAttribute = item.name
    val npath = path / validateId(nameAttribute).getOrElse(
      throw new IllegalArgumentException("No name on infoItem")
    )
    val typeAttribute = item.typeValue
    val names = item.iname.map {
      qlmIdType => parseQlmID(qlmIdType)
    }.filterNot {
      id =>
        id.id == item.name &&
          id.idType.isEmpty &&
          id.tagType.isEmpty &&
          id.startDate.isEmpty &&
          id.endDate.isEmpty &&
          id.attributes.isEmpty
    }.toVector
    val descriptions = item.description.map { des =>
      Description(des.value, des.lang)
    }.toSet

    val values = item.value.map {
      valueType =>
        parseValue(requestProcessTime, valueType)
    }.toVector
    val metaData = item.MetaData.map {
      md =>
        MetaData(
          md.InfoItem.map {
            mItem => parseInfoItem(requestProcessTime, mItem, npath / "MetaData")
          }.toVector
        )
    }.headOption
    val attributes = parseAttributes(item.attributes -- Seq("@type", "@name"))
    val ii = new InfoItem(
      nameAttribute,
      npath,
      typeAttribute,
      names,
      descriptions,
      values,
      metaData,
      attributes
    )
    ii
  }

  private[this] def parseValue(requestProcessTime: Timestamp, valueType: ValueType) = {
    val typeValue = valueType.typeValue

    def parseODFValue = {
      val objectsTypes = valueType.mixed.filter {
        dr: scalaxb.DataRecord[Any] =>
          dr.value match {
            case objectsType: xmlTypes.ObjectsType =>
              true
            case _ => false
          }
      }.map(_.as[xmlTypes.ObjectsType]).head //XXX: head used should not have multiple Objects
      ODFValue(
        parseObjects(objectsTypes, requestProcessTime), //.asXML.toString,
        timeSolver(valueType, requestProcessTime)
      )
    }

    typeValue.toLowerCase match {
      case "odf:objects" => parseODFValue
      case "objects" => parseODFValue
      case "odf" => parseODFValue
      /*
      objectsTypes.map{
        case odf: xmlTypes.ObjectsType =>
          val odfXml = xmlGen.scalaxb.toXML[xmlTypes.ObjectsType](odf,None,Some("Objects"),xmlGen.odfDefaultScope)
          odfXml.toString
          }.foldLeft("")( _ + _)
          */
      case str: String =>
        val xmlValue = valueType.mixed.map {
          dr: xmlGen.scalaxb.DataRecord[_] =>
            xmlGen.scalaxb.DataRecord.toXML(dr, None, None, xmlGen.odfDefaultScope, typeAttribute = false)
        }.foldLeft(NodeSeq.Empty) {
          case (res: NodeSeq, ns: NodeSeq) => res ++ ns
        }
        Value(
          xmlValue.toString,
          typeValue,
          timeSolver(valueType, requestProcessTime)
        )
    }

  }

  /**
    * Add timestamp values to metadata values as the name suggests.
    *
    * @param meta
    * @param reqTime
    * @return
    */
  private[this] def addTimeStampToMetaDataValues(meta: Option[MetaDataType],
                                                 reqTime: Timestamp): Option[MetaDataType] = {
    meta.map(m =>
      MetaDataType(m.InfoItem.map(ii =>
        ii.copy(
          value = ii.value.map(value =>
            value.copy(
              attributes = value.attributes.-("@dateTime")
                .updated("@unixTime", DataRecord(timeSolver(value, reqTime).getTime)))),

          MetaData = addTimeStampToMetaDataValues(ii.MetaData.headOption, reqTime).toSeq)
      ))
    )
  }

  /** Resolves time used in the value (unixtime in seconds or datetime): prefers datetime if both present
    */
  private[this] def timeSolver(value: ValueType, requestProcessTime: Timestamp) = value.dateTime match {
    case None => value.unixTime match {
      case None => requestProcessTime
      case Some(seconds) => new Timestamp(seconds.toLong * 1000)
    }
    case Some(cal) =>
      new Timestamp(cal.toGregorianCalendar().getTimeInMillis)
  }

  private[this] def parseQlmID(qlmIdType: QlmIDType): QlmID = {
    validateId(qlmIdType.value).map {
      id: String =>
        QlmID(
          id,
          qlmIdType.idType,
          qlmIdType.tagType,
          qlmIdType.startDate.map {
            cal => new Timestamp(cal.toGregorianCalendar().getTimeInMillis)
          },
          qlmIdType.endDate.map {
            cal => new Timestamp(cal.toGregorianCalendar().getTimeInMillis)
          },
          parseAttributes(
            qlmIdType.attributes - ("@idType", "@tagType", "@startDate", "@endDate")
          )
        )
    }.getOrElse {
      throw new IllegalArgumentException("<id> is empty or contains only spaces.")
    }
  }
}
