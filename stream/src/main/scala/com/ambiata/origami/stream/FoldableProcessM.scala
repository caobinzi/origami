package com.ambiata
package origami
package stream

import effect._, SafeT._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.{Process1, Process}
import scalaz.stream.Process._
import FoldProcessM._

/**
 * Foldable instance for Process[M, O]
 */
object FoldableProcessM {

  implicit def ProcessTaskFoldableM[A]: FoldableM[Task, Process[Task, A], A] =
    ProcessFoldableM[Task, Task, A](Monad[Task], Catchable[Task], Monad[Task], Catchable[Task], NaturalTransformation.refl[Task])

  implicit def ProcessSafeTTaskFoldableM[A]: FoldableM[SafeTTask, Process[Task, A], A] =
    ProcessFoldableM[SafeTTask, Task, A]

  def ProcessFoldableM[M[_], N[_], A](implicit FM: Monad[M], CM: Catchable[M],
                                               FN: Monad[N], CN: Catchable[N],
                                               nat: N ~> M): FoldableM[M, Process[N, A], A] =
    new FoldableM[M, Process[N, A], A] {
      def foldM[B](fa: Process[N, A])(fd: FoldM[M, A, B]): M[B] = {
        def go(state: fd.S): Process1[A, fd.S] =
          Process.receive1 { a: A =>
            val newState = fd.fold(state, a)
            emit(newState) fby go(newState)
          }

        fd.start.flatMap { st =>
          nat((fa |> go(st)).runLast).flatMap(last => fd.end(last.getOrElse(st)))
        }
      }

      def foldMBreak[B, S1](fa: Process[N, A])(fd: FoldM[M, A, B] { type S = S1 \/ S1 }): M[B] = {
        def go(state: fd.S): Process1[A, fd.S] =
          Process.receive1 { a: A =>
            state match {
              case \/-(_) => emit(state)
              case -\/(_) =>
                val newState = fd.fold(state, a)
                newState match {
                  case \/-(s) => emit(newState)
                  case -\/(s) => emit(newState) fby go(newState)
                }
            }
          }

        fd.start.flatMap { st =>
          nat((fa |> go(st)).runLast).flatMap(last => fd.end(last.getOrElse(st)))
        }
      }
    }

}
