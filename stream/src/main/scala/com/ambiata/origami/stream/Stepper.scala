package com.ambiata
package origami
package stream

import FoldM._

import scalaz.stream._, Process._, Cause._
import scalaz._, Scalaz._
import scalaz.concurrent.Task



/** Helper trait to step through a Process[F, A] */
trait Stepper[F[_], A] {
  def next: OptionT[F, Seq[A]]
  def close: F[Unit]
}

object Stepper {
    /** create a Stepper for a given Process[F, A] */
  def step[A](p: Process[Task, A]): Stepper[Task, A] = new Stepper[Task, A] {
    var state = p

    def next: OptionT[Task, Seq[A]] = state.step match {

      case Halt(_) => OptionT.none

      case Step(Emit(as: Seq[A]), cont) =>
        state = cont.continue
        OptionT(as.point[Task] map some)

      case Step(Await(req: Task[_], rcv), cont) =>
        for {
          tail <- (req.attempt map { r => rcv(EarlyCause fromTaskResult r).run +: cont }).liftM[OptionT]
          _ = state = tail          // purity ftw!
          back <- next
        } yield back
    }

    def close =
      Task.suspend {
        Task.delay(state = state.kill) >>
        state.run
      }
  }
}
