package responses

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import akka.util.Timeout
import http.OmiConfig
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import testHelpers.{Actorstest, SystemTestCallbackServer}
import types.OmiTypes._

import scala.concurrent.duration._


class CallbackHandlerTest(implicit ee: ExecutionEnv) extends Specification {

  sequential

  "CallbackHandler" should {

    "Send callback to the correct address" in new Actorstest {
      val port = 20003
      val probe = initCallbackServer(port)
      val ttl = Duration(2, "seconds")
      val msg = Responses.Success(ttl = ttl)

      val settings = OmiConfig(system)

      val materializer = ActorMaterializer()(system)
      val callbackHandler = new CallbackHandler(settings)(system, materializer)
      callbackHandler.sendCallback(HTTPCallback(Uri(s"http://localhost:$port")), msg)

      probe.expectMsg(ttl, Option(msg.asXML))
    }

    "Try to keep sending message until ttl is over" in skipped(new Actorstest {
      val port = 20004
      val ttl = Duration(10, "seconds")
      val msg = Responses.Success(ttl = ttl)

      val settings = OmiConfig(system)
      val materializer = ActorMaterializer()(system)
      val callbackHandler = new CallbackHandler(settings)(system, materializer)
      callbackHandler.sendCallback(HTTPCallback(Uri(s"http://localhost:$port")), msg)

      Thread.sleep(1000)
      val probe = initCallbackServer(port)

      probe.expectMsg(ttl, Option(msg.asXML))

    })
  }

  def initCallbackServer(port: Int)(implicit system: ActorSystem): TestProbe = {
    implicit val timeout = Timeout(5 seconds)
    val probe = TestProbe()
    val testServer = new SystemTestCallbackServer(probe.ref, "localhost", port)
    probe
  }
}
