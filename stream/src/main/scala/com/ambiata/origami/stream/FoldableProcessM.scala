package com.ambiata
package origami
package stream

import scalaz.{\/, \/-, -\/, Monad, Catchable}
import scalaz.syntax.bind._
import scalaz.concurrent.Task
import scalaz.stream.{Process1, Process}
import scalaz.stream.Process._

/**
 * Foldable instance for Process[M, O]
 */
object FoldableProcessM {

  implicit def ProcessFoldableM[M[_], A](implicit F: Monad[M], C: Catchable[M]): FoldableM[M, Process[M, A], A] = new FoldableM[M, Process[M, A], A] {
    def foldM[B](fa: Process[M, A])(fd: FoldM[M, A, B]): M[B] = {
      def go(state: fd.S): Process1[A, fd.S] =
        Process.receive1 { a: A =>
          val newState = fd.fold(state, a)
          emit(newState) fby go(newState)
        }

      fd.start.flatMap { st =>
        (fa |> go(st)).runLast.flatMap(last => fd.end(last.getOrElse(st)))
      }
    }

    def foldMBreak[B, S1](fa: Process[M, A])(fd: FoldM[M, A, B] { type S = S1 \/ S1 }): M[B] = {
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
        (fa |> go(st)).runLast.flatMap(last => fd.end(last.getOrElse(st)))
      }
    }
  }

}
