package com.ambiata
package origami
package akka

import Origami._

import scala.concurrent.{Future, ExecutionContext}
import scalaz.Scalaz._
import scalaz._

object FoldSourceM {

  implicit def IdFutureNaturalTransformation(implicit ec: ExecutionContext): Id ~> Future = new (Id ~> Future) {
    def apply[A](t: Id[A]): Future[A] = Future(t)
  }

  implicit def IdemNaturalTransformation[M[_]]: M ~> M = new (M ~> M) {
    def apply[A](t: M[A]) = t
  }
}
