package agents

import agentSystem._
import parsing.Types._
import parsing.Types.OdfTypes._
import parsing.OdfParser
import parsing.OmiParser
import database._

import scala.io.Source
import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.io.{ IO, Tcp }
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import scala.language.postfixOps

import java.sql.Timestamp
import java.io.File

/* JSON4s */
import org.json4s._
import org.json4s.native.JsonMethods._

// HTTP related imports
import spray.can.Http
import spray.http._
import HttpMethods._
import spray.client.pipelining._

// Futures related imports

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{ Success, Failure }

import parsing.Types._
import parsing.Types.Path._

// Scala XML
import scala.xml
import scala.xml._

// Mutable map for sensordata
import scala.collection.mutable.Map
import scala.util.Random
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable


/** Agent for the SmartHouse
  * 
  */
class SmartHouseAgent(configPath : String) extends InternalAgent(configPath) {
  // Used to inform that database might be busy
  
  private var odf : Option[Iterable[OdfInfoItem]] = None   
  
  def getSensors(o:Iterable[OdfObject]) : Iterable[OdfInfoItem] = {
    o.flatten{ o =>
      o.infoItems ++ getSensors(o.objects)
    }
  }
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath was empty or didn't exist, SmartHouseAgent shutting down.")
      shutdown
      return
    }
    val lines = scala.io.Source.fromFile(configPath).getLines().toArray
    if(lines.isEmpty){
      InternalAgent.log.warning("Config file was empty, SmartHouseAgent shutting down.")
      shutdown
      return
    }
    val file =  new File(lines.head)
    if(!file.exists() || !file.canRead){
      InternalAgent.log.warning("File "+ lines.head + " doesn't exist or can't be read, SmartHouseAgent shutting down.")
      shutdown
      return
    }
      
    val xml = XML.loadFile(file)
    val tmp_odf = OdfParser.parse( xml)
    val errors = getErrors(tmp_odf)
    if(errors.nonEmpty) {
      InternalAgent.log.warning("Odf has errors, SmartHouseAgent shutting down.")
      InternalAgent.log.warning("SmartHouse: "+errors.mkString("\n"))
      shutdown
      return
    }
    odf = Some(
      getObjects(tmp_odf).flatten{o =>
        o.infoItems ++ getSensors(o.objects)
      }
    )
    if(odf.isEmpty){
      InternalAgent.log.warning("Odf was empty, SmartHouseAgent shutting down.")
      shutdown
      return
    }
    InputPusher.handlePathMetaDataPairs( 
      odf.get.filter{info => info.metaData.nonEmpty }.map{
        info  => (info.path, info.metaData.get.data)
      }
    )
  }
  
  def loopOnce(): Unit = {
    val date = new java.util.Date()
    odf = Some( 
      odf.get.map{ info => 
        OdfInfoItem( info.path, Iterable( OdfValue(  Random.nextDouble.toString, "" , Some( new Timestamp( date.getTime) ) )))
      } 
    )
    InternalAgent.log.info("SmartHouseAgent pushed data to DB.")
    InputPusher.handleInfoItems(odf.get)
    Thread.sleep(10000)
  }

  def finish(): Unit = {
	    println("SmartHouseAgent has died.")
  }
}