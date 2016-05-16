
package agentSystem

import agentSystem._
import types.Path
import types.OmiTypes.WriteRequest
import types.OdfTypes._
import akka.actor.SupervisorStrategy._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.{
  Actor, 
  ActorRef, 
  ActorInitializationException, 
  ActorKilledException, 
  ActorLogging, 
  OneForOneStrategy, 
  Props, 
  SupervisorStrategy}
import akka.dispatch.{BoundedMessageQueueSemantics, RequiresMessageQueue}
import scala.concurrent.duration._
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.immutable.Map
import com.typesafe.config.ConfigException
import database.SingleStores.valueShouldBeUpdated
import java.lang.{Iterable => JavaIterable}
import scala.util.{Failure, Success, Try}
import scala.xml.XML
import database.SingleStores.valueShouldBeUpdated

import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes.MetaData
import database._
import responses.CallbackHandlers._
import responses.OmiGenerator.xmlFromResults
import responses.Results
import responses.NewDataEvent
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection

sealed trait ResponsibilityMessage
case class RegisterOwnership( agent: AgentName, paths: Seq[Path])
case class PromiseWrite( promise : Promise[Iterable[Promise[ResponsibleAgentResponse]]], write:WriteRequest )
sealed trait ResponsibilityResponse extends ResponsibilityMessage
case class FutureResult( result: Iterable[Future[Try[ResponsibleAgentResponse]]] ) extends ResponsibilityResponse

trait ResponsibleAgentManager extends BaseAgentSystem{
  def dbobject: DB
  def subHandler: ActorRef
  /*
   * TODO: Use database and Authetication for responsible agents
   */
  protected[this] val pathOwners: scala.collection.mutable.Map[Path,AgentName] = scala.collection.mutable.Map.empty
  getConfigsOwnerships()
  receiver {
    //Write can be received either from RequestHandler or from InternalAgent
    case PromiseWrite(promise: Promise[Iterable[Promise[ResponsibleAgentResponse]]], write: WriteRequest) => handleWrite(promise,write)  
    //Write received from an InternalAgent
    case ResponsibleWrite(promise: Promise[ResponsibleAgentResponse], write: WriteRequest) => handleResponsibleWrite(promise,write)  
    case registerOwnership: RegisterOwnership => sender() ! handleRegisterOwnership(registerOwnership)  
  }
  def getOwners( paths: Path*) : Map[AgentName,Seq[Path]] = {
    paths.map{
      path => pathOwners.get(path).map{ name => (name,path)}
    }.flatten.groupBy{case (name, path) => name}.mapValues{seq => seq.map{case (name, path) => path}}
  } 
  def handleRegisterOwnership(registerOwnership: RegisterOwnership ) = {}
  protected def getConfigsOwnerships(): Unit = {
    log.info(s"Setting path ownerships for Agents from config.")
    val agents = settings.internalAgents
    val names : Set[String] = asScalaSet(agents.keySet()).toSet // mutable -> immutable
    val pathsToOwner =names.map{ 
      name =>
      val agentConfig = agents.toConfig().getObject(name).toConfig()
      try{
        val ownedPaths = agentConfig.getStringList("owns")
        ownedPaths.map{ path => (Path(path), name)}
      } catch {
        case e: ConfigException.Missing   =>
          //Not a ResponsibleAgent
          List.empty
        case e: ConfigException.WrongType =>
          log.warning(s"List of owned paths for $name couldn't converted to java.util.List<String>")
          List.empty
      }
    }.flatten.toArray
    pathOwners ++= pathsToOwner
    log.info(s"Path ownerships for Agents in config set.")
  }
  private def handleResponsibleWrite( promise: Promise[ResponsibleAgentResponse], write: WriteRequest ) : Unit = {
    val senderName = sender().path.name
    log.info( s"Received WriteRequest from $senderName.")
    val odfObjects = write.odf
    val allInfoItems = getInfoItems(odfObjects)

    // Collect metadata 
    val objectsWithMetaData = getOdfNodes(odfObjects) collect {
      case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
    }
    val allNodes = allInfoItems ++ objectsWithMetaData

    val allPaths = allNodes.map( _.path )
    val ownerToPath = getOwners(allPaths:_*)

    val allOwnedPaths : Seq[Path] = ownerToPath.values.flatten.toSeq

    //Get part that isn't owned by anyone

    val writesToOwnerless:Future[SuccessfulWrite] = {
      val paths = allPaths.filter{ path => !allOwnedPaths.contains(path) }
      val objects= allNodes.collect{
        case node if paths.contains(node.path) => fromPath(node) 
      }.foldLeft(OdfObjects()){_.union(_)}
      log.info( s"$senderName writing to paths not owned by anyone: $paths")
      handleOdf( objects )
    }
    if( ownerToPath.isEmpty ){
      promise.completeWith( writesToOwnerless )
    } else if( ownerToPath.length == 1 && ownerToPath.get(senderName).nonEmpty ){
      //Get part that is owned by sender()
      val writesBySender:Future[SuccessfulWrite] ={
        val pathsO = ownerToPath.get(senderName)
        val objects= allNodes.collect{
          case node if pathsO.exists{ paths => paths.contains(node.path)} => fromPath(node) 
        }.foldLeft(OdfObjects()){_.union(_)}
        log.info( s"$senderName writing to paths owned by it: $pathsO")
        handleOdf( objects )
      } 
      val res = for{
        owns <- writesBySender
        pub  <- writesToOwnerless
      } yield SuccessfulWrite( owns.paths ++ pub.paths )
      
      //Collect all futures and return
      promise.completeWith( res )
    } else {
      val msg =s"$senderName tryed to write to paths owned by some other agent using ResponsibleWrite. Wrong message used. Use PromiseWrite. "
      log.warning(msg)
      promise.failure( new Exception(msg))

    }
  }



  private def handleWrite( promise: Promise[Iterable[Promise[ResponsibleAgentResponse]]], write: WriteRequest ) : Unit={

    def askOtherAgentToHandleObjectsOwnedByThem( ttl: Duration, ownerToObjects: Map[AgentName,OdfObjects]): Iterable[Promise[ResponsibleAgentResponse]]={
      ownerToObjects.flatMap{
        case (name: AgentName, objects: OdfObjects) =>
        agents.get(name).map{
          case AgentInfo( _, _, _, agent, _) => 
          val write = WriteRequest( ttl, objects) 
          log.info( s"Asking $name to handle $write" )
          val promise = Promise[ResponsibleAgentResponse]()
          val future = agent ! ResponsibleWrite( promise, write)
          promise
        }
      }
    }
    val senderName = sender().path.name
    log.info( s"Received WriteRequest from $senderName.")
    val odfObjects = write.odf
    val allInfoItems = getInfoItems(odfObjects)

    // Collect metadata 
    val objectsWithMetaData = getOdfNodes(odfObjects) collect {
      case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
    }
    val allNodes = allInfoItems ++ objectsWithMetaData

    val allPaths = allNodes.map( _.path )
    val ownerToPath = getOwners(allPaths:_*)
    
    //Get part that is owned by sender()
    val writesBySender:Promise[ResponsibleAgentResponse] ={
      val pathsO = ownerToPath.get(senderName)
      val objects= allNodes.collect{
        case node if pathsO.exists{ paths => paths.contains(node.path)} => fromPath(node) 
      }.foldLeft(OdfObjects()){_.union(_)}
      log.info( s"$senderName writing to paths owned by it: $pathsO")
      val promise = Promise[ResponsibleAgentResponse]()
      promise.completeWith(handleOdf( objects ))
    } 
    val allOwnedPaths : Seq[Path] = ownerToPath.values.flatten.toSeq
    
    //Get part that isn't owned by anyone

    val writesToOwnerless:Promise[ResponsibleAgentResponse] = {
      val paths = allPaths.filter{ path => !allOwnedPaths.contains(path) }
      val objects= allNodes.collect{
        case node if paths.contains(node.path) => fromPath(node) 
      }.foldLeft(OdfObjects()){_.union(_)}
      log.info( s"$senderName writing to paths not owned by anyone: $paths")
      val promise = Promise[ResponsibleAgentResponse]()
      promise.completeWith(handleOdf( objects ))
    }
    //Get part that is owned by other agents than sender()
    val writesToOthers: Iterable[Promise[ResponsibleAgentResponse]]={ 
      val ownerToPaths= ownerToPath - senderName
      val ownerToObjects = ownerToPaths.mapValues{ 
        paths => 
        allNodes.collect{ 
          case node if paths.contains(node.path) => fromPath(node)
        }.foldLeft(OdfObjects()){_.union(_)} 
      }
      log.info( s"$senderName writing to paths owned other agents, ask handling: $ownerToObjects")
      askOtherAgentToHandleObjectsOwnedByThem( write.ttl, ownerToObjects)
    }
    //Collect all futures and return
    val res : Iterable[Promise[ResponsibleAgentResponse]] = Iterable(writesToOwnerless, writesBySender) ++ writesToOthers
    promise.success( Iterable(writesToOwnerless, writesBySender) ++ writesToOthers) 
  }


  private def sendEventCallback(esub: EventSub, infoItems: Seq[OdfInfoItem]): Unit = {
    sendEventCallback(esub,
      (infoItems map fromPath).foldLeft(OdfObjects())(_ union _)
    )
  }

  private def sendEventCallback(esub: EventSub, odf: OdfObjects): Unit = {
    val id = esub.id
    val callbackAddr = esub.callback
    log.debug(s"Sending data to event sub: $id.")
    val xmlMsg = xmlFromResults(
      1.0,
      Results.poll(id.toString, odf))
    log.info(s"Sending in progress; Subscription subId:$id addr:$callbackAddr interval:-1")
    //log.debug("Send msg:\n" + xmlMsg)

    def failed(reason: String) =
      log.warning(
        s"Callback failed; subscription id:$id interval:-1  reason: $reason")


    sendCallback(
      callbackAddr,
      xmlMsg,
      Try((esub.endTime.getTime - parsing.OdfParser.currentTime().getTime).milliseconds)
        .toOption.getOrElse(Duration.Inf)
    ) onComplete {
      case Success(CallbackSuccess) =>
        log.info(s"Callback sent; subscription id:$id addr:$callbackAddr interval:-1")

      case Success(fail: CallbackFailure) =>
        failed(fail.toString)
      case Failure(e) =>
        failed(e.getMessage)
    }
  }

  private def processEvents(events: Seq[InfoItemEvent]) = {

    val esubLists: Seq[(EventSub, OdfInfoItem)] = events flatMap {
      case ChangeEvent(infoItem) =>  // note: AttachEvent extends Changeevent

        val esubs = SingleStores.eventPrevayler execute LookupEventSubs(infoItem.path)
        esubs map { (_, infoItem) }  // make tuples
    }
    // Aggregate events under same subscriptions (for optimized callbacks)
    val esubAggregation /*: Map[EventSub, Seq[(EventSub, OdfInfoItem)]]*/ =
        esubLists groupBy {_._1}

    for ((esub, infoSeq) <- esubAggregation) {

        val infoItems = infoSeq map {_._2}

        sendEventCallback(esub, infoItems)
    }

  }

  /**
   * Function for handling OdfObjects.
   *
   */
  private def handleOdf(objects: OdfObjects):  Future[SuccessfulWrite] ={
    if( objects.objects.nonEmpty ) {
    // val data = getLeafs(objects)
    // if ( data.nonEmpty ) {
    val items = getInfoItems(objects)

    // Collect metadata 
    val other = getOdfNodes(objects) collect {
      case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
    }
    val all = items ++ other

      val writeValues = handleInfoItems(items, other)
      
      writeValues.onSuccess{
        case u =>
          log.debug("Successfully saved Odfs to DB")
      }
      writeValues.map{ 
          paths => SuccessfulWrite( paths )
      }
    } else {
      Future.successful{
         SuccessfulWrite( Iterable.empty )
      }  
    }
  }

  /**
   * Creates values that are to be updated into the database for polled subscription.
   * Polling removes the related data from database, this method creates new data if the old value.
   * @param path
   * @param newValue
   * @param oldValueOpt
   * @return returns Sequence of SubValues to be added to database
   */
  private def handlePollData(path: Path, newValue: OdfValue, oldValueOpt: Option[OdfValue]): Set[SubValue] = {
    val relatedPollSubs = SingleStores.pollPrevayler execute GetSubsForPath(path)

    relatedPollSubs.collect {
      //if no old value found for path or start time of subscription is after last value timestamp
      //if new value is updated value. forall for option returns true if predicate is true or the value is None
      case sub if(oldValueOpt.forall(oldValue =>
        valueShouldBeUpdated(oldValue, newValue) && (oldValue.timestamp.before(sub.startTime) || oldValue.value != newValue.value))) => {
          SubValue(sub.id, path, newValue.timestamp, newValue.value,newValue.typeValue)
      }
    }
  }

  /**
   * Function for handling sequences of OdfInfoItem.
   * @return true if the write was accepted.
   */
  private def handleInfoItems(infoItems: Iterable[OdfInfoItem], objectMetaDatas: Vector[OdfObject] = Vector()): Future[Iterable[Path]] = Future{
    // save only changed values
    val pathValueOldValueTuples = for {
      info <- infoItems.toSeq
      path = info.path
      oldValueOpt = SingleStores.latestStore execute LookupSensorData(path)
      value <- info.values
    } yield (path, value, oldValueOpt)

    val newPollValues = pathValueOldValueTuples.flatMap{n =>
      handlePollData(n._1, n._2 ,n._3)}
      //handlePollData _ tupled n}
    if(!newPollValues.isEmpty) {
      dbobject.addNewPollData(newPollValues)
    }

    val callbackDataOptions = pathValueOldValueTuples.map(n=>SingleStores.processData _ tupled n)
    val triggeringEvents = callbackDataOptions.flatten
    
    if (triggeringEvents.nonEmpty) {  // (unnecessary if?)
      // TODO: implement responsible agent check here or processEvents method
      // return false  // command was not accepted or failed in agent or physical world but no internal server errors

      // Send all callbacks
      processEvents(triggeringEvents)
    }


    // Save new/changed stuff to transactional in-memory SingleStores and then DB

    val newItems = triggeringEvents collect {
        case AttachEvent(item) => item
    }

    val metas = infoItems filter { _.hasMetadata }
    // check syntax
    metas foreach {metaInfo =>

      checkMetaData(metaInfo.metaData) match {

        case Success(_) => // noop: exception on failure instead of filtering the valid
        case Failure(exp) =>
         log.error( exp, "InputPusher MetaData" )
         throw exp;
      }
    }

    val iiDescriptions = infoItems filter { _.hasDescription }

    val updatedStaticItems = metas ++ iiDescriptions ++ newItems ++ objectMetaDatas

    // Update our hierarchy data structures if needed
    if (updatedStaticItems.nonEmpty) {

        // aggregate all updates to single odf tree
        val updateTree: OdfObjects =
          (updatedStaticItems map fromPath).foldLeft(OdfObjects())(_ union _)

        SingleStores.hierarchyStore execute Union(updateTree)
    }

    // DB + Poll Subscriptions
    val itemValues = (triggeringEvents flatMap {event =>
      val item   = event.infoItem
      val values = item.values.toSeq
      values map {value => (item.path, value)}
    }).toSeq
    dbobject.setMany(itemValues)

    subHandler ! NewDataEvent(itemValues)
    
    infoItems.map(_.path) ++ objectMetaDatas.map(_.path)
    //log.debug("Successfully saved InfoItems to DB")
  }

  /**
   * Check metadata XML validity and O-DF validity
   */
  private def checkMetaData(metaO: Option[OdfMetaData]): Try[String] = metaO match {
    case Some(meta) => checkMetaData(meta.data)
    case None => Failure(new MatchError(None))
  }
  private def checkMetaData(metaStr: String): Try[String] = Try{
        val xml = XML.loadString(metaStr)
        val meta = xmlGen.scalaxb.fromXML[MetaData](xml)
        metaStr
      }
  
}
