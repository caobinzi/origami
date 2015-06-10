package com.ambiata
package origami
package stream

import FoldM._

import scalaz.stream.{Process}
import scalaz.{Id, ~>}, Id._
import scalaz.concurrent.Task
import Stepper._
import effect._, SafeT._
import FoldTask._

object FoldProcessM {

  type ProcessTask[T] = Process[Task, T]

  implicit val IdTaskNaturalTransformation: Id ~> Task = new (Id ~> Task) {
    def apply[A](i: Id[A]): Task[A] = Task.now(i)
  }

  implicit val IdSafeTTaskNaturalTransformation: Id ~> SafeTTask = new (Id ~> SafeTTask) {
    def apply[A](i: Id[A]): SafeTTask[A] = SafeT.point(i)
  }

  implicit val TaskToSafeTTaskNaturalTransformation: Task ~> SafeTTask = new (Task ~> SafeTTask) {
    def apply[A](i: Task[A]): SafeTTask[A] = SafeT.point(i.run)
  }

  implicit val TaskProcessTaskNaturalTransformation: Task ~> ProcessTask = new (Task ~> ProcessTask) {
    def apply[A](t: Task[A]): Process[Task, A] = Process.eval(t)
  }

  implicit val IdProcessTaskNaturalTransformation: Id ~> ProcessTask = new (Id ~> ProcessTask) {
    def apply[A](t: Id[A]): Process[Task, A] = Process.eval(Task.now(t))
  }

  /** create an origami sink from a Scalaz sink */
  def fromSink[T](sink: scalaz.stream.Sink[Task, T]) = new Sink[T] {
    type S = Stepper[Task, T => Task[Unit]]

    def start = {
      val stepper = step(sink)
      Task.now(stepper) `finally` stepper.close
    }

    def fold = (s: S, t: T) => {
      s.next.run.flatMap(_.getOrElse(Nil).head(t)).run
      s
    }

    def end(s: S) = SafeT.point(())
  }

  def lift[T](f: T => Task[Unit]): Sink[T] =
    fromSink(Process.constant(f))

}
