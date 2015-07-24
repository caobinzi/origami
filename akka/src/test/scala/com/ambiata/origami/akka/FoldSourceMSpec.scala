package com.ambiata
package origami
package akka

import Origami._
import scalaz._, Scalaz._
import org.scalacheck._, Prop._
import Arbitraries._
import FoldSourceM._
import FoldableSourceM._

import _root_.akka.actor.ActorSystem
import _root_.akka.stream.ActorFlowMaterializer
import _root_.akka.stream.scaladsl.{Sink => AkkaSink, Source}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global

class FoldSourceMSpec extends Properties("Akka-stream folds") {

  property("run a fold on an akka stream") = runFold

  implicit val system = ActorSystem("test")
  import system.dispatcher
  implicit val materializer = ActorFlowMaterializer()

  def runFold = forAll { list: List[Line] =>

    val source = Source(() => list.map(_.value).toList.iterator)
    await(count[String].into[Future].run(source)) === list.size

  }

  def await[T](t: =>Future[T]) = Await.result(t, 10.seconds)

}
