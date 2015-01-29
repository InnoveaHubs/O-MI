package parsing

import org.specs2._
import scala.io.Source
import parsing._
import parsing.OdfParser._

/*
 * Test class for testing parsing parsing package
 * tests e1   - e99 are for testing OmiParser general methods
 * tests e100 - e199 are for testing write request
 * tests e200 - e299 are for testing response messages
 * tests e300 - e399 are for testing read requests
 * tests e400 - e499 are for testing OdfParser class
 */
class ParserTest extends Specification {
  lazy val omi_read_test_file = Source.fromFile("src/test/scala/parsing/omi_read_test.xml").getLines.mkString("\n")
  lazy val omi_write_test_file = Source.fromFile("src/test/scala/parsing/omi_write_test.xml").getLines.mkString("\n")
  lazy val omi_response_test_file = Source.fromFile("src/test/scala/parsing/omi_response_test.xml").getLines.mkString("\n")

  def is = s2"""
  This is Specification to check the parsing functionality.

  OmiParser should give certain result for
    message with
      incorrect XML       $e1
      incorrect prefix    $e2
      incorrect label     $e3
      missing request     $e4
      missing ttl         $e5
      unknown omi message $e6
    write request with
      correct message     $e100
      missing msgformat   $e101
      wrong msgformat     $e102
      missing omi:msg     $e103
      missing Objects     $e104 
      no objects to parse $e105
    response message with
      correct message     $e200
      missing msgformat   $e201
      wrong msgformat     $e202
      missing Objects     $e204
      missing result node $e205
      no objects to parse $e206
    read request with
      correct message     $e300
      missing msgformat   $e301
      wrong msgformat     $e302
      missing omi:msg     $e303
      missing Objects     $e304
      no objects to parse $e305
  OdfParser should give certain result for
    message with
      incorrect XML       $e401
      incorrect label     $e402
      missing Object id   $e403
      nameless infoitem   $e404

      
    """

  def e1 = {
    val temp = OmiParser.parse("incorrect xml") 
    temp.head should be equalTo(ParseError("Invalid XML"))

  }

  /*
   * case ParseError("Incorrect prefix :: _ ) matches to list that has that parse error in the head position    
   */
  def e2 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("omi:omiEnvelope", "pmi:omiEnvelope"))
    temp.head should be equalTo(ParseError("Incorrect prefix"))

  }

  def e3 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("omi:omiEnvelope", "omi:Envelope")) 
    temp.head should be equalTo(ParseError("XML's root isn't omi:omiEnvelope"))

  }

  def e4 = {
    val temp = OmiParser.parse(
      """<omi:omiEnvelope ttl="10" version="1.0" xsi:schemaLocation="omi.xsd omi.xsd" xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
         </omi:omiEnvelope>
      """) 
      temp.head should be equalTo(ParseError("omi:omiEnvelope doesn't contain request"))

  }

  def e5 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("""ttl="10"""", """ttl=""""")) 
    temp.head should be equalTo(ParseError("No ttl present in O-MI Envelope"))

  }

  def e6 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("omi:response", "omi:respnse")) 
    temp.head should be equalTo(ParseError("Unknown node."))

  }

  def e100 = {
    OmiParser.parse(omi_write_test_file) should be equalTo(List(
      Write("10", List(
        OdfObject(
          List("Objects", "SmartHouse"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartFridge"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartFridge",
                    "PowerConsumption"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "56")),
                  "")),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartOven"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartOven",
                    "PowerOn"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "1")),
                  "")),
              "")),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "PowerConsumption"),
              List(
                TimedValue("2014-12-186T15:34:52", "180")),
              ""),
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "Moisture"),
              List(
                TimedValue("2014-12-186T15:34:52", "0.20")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCar"),
          List(),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartCar",
                "Fuel"),
              List(
                TimedValue("2014-12-186T15:34:52", "30")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCottage"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Heater"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Sauna"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Weather"),
              List(),
              List(),
              "")),
          List(),
          "")),
        "",
        List())))
    //      List(
    //      Write("10", List(
    //        OdfObject(Seq("Objects","SmartHouse","SmartFridge","PowerConsumption"), InfoItem, Some("56"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartHouse/SmartOven/PowerOn", InfoItem, Some("1"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartHouse/PowerConsumption", InfoItem, Some("180"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartHouse/Moisture", InfoItem, Some("0.20"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartCar/Fuel", InfoItem, Some("30"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartCottage/Heater", NodeObject, None, None, None),
    //        ODFNode("/Objects/SmartCottage/Sauna", NodeObject, None, None, None),
    //        ODFNode("/Objects/SmartCottage/Weather", NodeObject, None, None, None)),
    //        "test",
    //        Seq()))
  }
  def e101 = {
    val temp = OmiParser.parse(omi_write_test_file.replace("""omi:write msgformat="odf"""", "omi:write"))
    temp.head should be equalTo(ParseError("No msgformat parameter found in write"))

  }

  def e102 = {
    val temp = OmiParser.parse(omi_write_test_file.replace("""msgformat="odf"""", """msgformat="pdf""""))
    temp.head should be equalTo(ParseError("Unknown message format."))
  }

  def e103 = {
    val temp = OmiParser.parse(omi_write_test_file.replace("omi:msg", "omi:msn"))
    temp.head should be equalTo(ParseError("No message node found in write node."))
  }

  def e104 = {
    val temp = OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:write msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
  </omi:write>
</omi:omiEnvelope>
""") 
temp.head should be equalTo(ParseError("No Objects node found in msg node."))

  }

  def e105 = {
    val temp = OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:write msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
  </omi:write>
</omi:omiEnvelope>
""") 
temp.head should be equalTo(ParseError("No Objects to parse"))

  }

  def e200 = {
    OmiParser.parse(omi_response_test_file) should be equalTo(List(
      Write("10", List(
        OdfObject(
          List("Objects", "SmartHouse"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartFridge"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartFridge",
                    "PowerConsumption"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "56")),
                  "")),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartOven"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartOven",
                    "PowerOn"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "1")),
                  "")),
              "")),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "PowerConsumption"),
              List(
                TimedValue("2014-12-186T15:34:52", "180")),
              ""),
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "Moisture"),
              List(
                TimedValue("2014-12-186T15:34:52", "0.20")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCar"),
          List(),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartCar",
                "Fuel"),
              List(
                TimedValue("2014-12-186T15:34:52", "30")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCottage"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Heater"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Sauna"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Weather"),
              List(),
              List(),
              "")),
          List(),
          "")),
        "",
        List())))
  }

  def e201 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("""omi:result msgformat="odf"""", "omi:result")) 
    temp.head should be equalTo(ParseError("No msgformat in result message"))

  }

  def e202 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("""msgformat="odf"""", """msgformat="pdf"""")) 
    temp.head should be equalTo(ParseError("Unknown message format."))

  }

  //  def e203 = {
  //    OmiParser.parse(omi_response_test_file.replace("omi:msg", "omi:msn")) match {
  //      case ParseError("No message node found in response node.") :: _ => true
  //      case _ => false
  //    }
  //  }

  def e204 = {
    val temp = OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:response>
      <omi:result msgformat="odf" > 
      <omi:return></omi:return> 
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
      </omi:result> 
  </omi:response>
</omi:omiEnvelope>
""") 
temp.head should be equalTo(ParseError("No Objects node found in msg node."))

  }

  def e205 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("<omi:return></omi:return>", "")) 
    temp.head should be equalTo(ParseError("No return node in result node"))

  }

  def e206 = {
    val temp = OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:response>
      <omi:result msgformat="odf" > 
      <omi:return></omi:return> 
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
      </omi:result> 
  </omi:response>
</omi:omiEnvelope>
""") 
temp.head should be equalTo(ParseError("No Objects to parse"))

  }

  def e300 = {
    OmiParser.parse(omi_read_test_file) should be equalTo(List(
      Write("10", List(
        OdfObject(
          List("Objects", "SmartHouse"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartFridge"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartFridge",
                    "PowerConsumption"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "56")),
                  "")),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartOven"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartOven",
                    "PowerOn"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "1")),
                  "")),
              "")),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "PowerConsumption"),
              List(
                TimedValue("2014-12-186T15:34:52", "180")),
              ""),
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "Moisture"),
              List(
                TimedValue("2014-12-186T15:34:52", "0.20")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCar"),
          List(),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartCar",
                "Fuel"),
              List(
                TimedValue("2014-12-186T15:34:52", "30")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCottage"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Heater"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Sauna"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Weather"),
              List(),
              List(),
              "")),
          List(),
          "")),
        "",
        List())))
  }

  def e301 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("""omi:read msgformat="odf"""", "omi:read")) 
    temp.head should be equalTo(ParseError("No msgformat in read request"))

  }

  def e302 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("""msgformat="odf"""", """msgformat="pdf"""")) 
    temp.head should be equalTo(ParseError("Unknown message format."))

  }

  def e303 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("omi:msg", "omi:msn")) 
    temp.head should be equalTo(ParseError("No message node found in read node."))

  }

  def e304 = {
    val temp = OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:read msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
  </omi:read>
</omi:omiEnvelope>
""") 
temp.head should be equalTo(ParseError("No Objects node found in msg node."))

  }

  def e305 = {
    val temp = OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:read msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
  </omi:read>
</omi:omiEnvelope>
""") 
temp.head should be equalTo(ParseError("No Objects to parse"))

  }

  def e401 = {
    val temp = OdfParser.parse("incorrect xml") 
    temp.head should be equalTo(Left(ParseError("Invalid XML")))


  }
  def e402 = {
    val temp = OdfParser.parse("""<Object>
        <Object>
      <id>SmartHouse</id>
      <InfoItem name="PowerConsumption">
      </InfoItem>
      <InfoItem name="Moisture">
      </InfoItem>
        </Object>
        <Object>
      <id>SmartCar</id>
      <InfoItem name="Fuel">
      </InfoItem>
        </Object>
        <Object>
      <id>SmartCottage</id>
        </Object>
    </Object>
""") 
temp.head should be equalTo(Left(ParseError("ODF doesn't have Objects as root.")))

  }
  def e403 = {
    val temp = OdfParser.parse("""
    <Objects>
        <Object>
        <id></id>
        </Object>
    </Objects>
""") 
temp.head should be equalTo(Left(ParseError("No id for Object.")))

  }
  def e404 = {
    val temp = OdfParser.parse("""
    <Objects>
        <Object>
        <id>SmartHouse</id>
        <InfoItem name="">
        </InfoItem>
        </Object>
    </Objects>
""") 
temp.head should be equalTo(Left(ParseError("No name for InfoItem.")))

  }
  //  def e405 = false

}



