package com.ambiata
package origami

import scala.annotation.tailrec
import scalaz.{EphemeralStream, Bind, ~>, Foldable, \/, \/-, -\/}
import EphemeralStream._
import scalaz.syntax.bind._
import scalaz.syntax.foldable._
import scalaz.std.list._

/**
 * A structure delivering elements of type A (variable type, like a List) and which 
 * can be folded over
 */
trait FoldableM[M[_], F[_]]  { self =>
  def foldM[A, B](fa: F[A])(fd: FoldM[M, A, B]): M[B]

  def foldMBreak[A, B](fa: F[A])(fd: FoldM[M, A, B] { type S = B \/ B }): M[B]

  def into[G[_]](implicit nat: G ~> F): FoldableM[M, G] = new FoldableM[M, G] {
    def foldM[A, B](fa: G[A])(fd: FoldM[M, A, B]): M[B] =
     self.foldM(nat(fa))(fd)

    def foldMBreak[A, B](fa: G[A])(fd: FoldM[M, A, B] { type S = B \/ B }): M[B] =
      self.foldMBreak(nat(fa))(fd)
  }
}

object FoldableM {

  def apply[M[_], F[_]](implicit fm: FoldableM[M, F]): FoldableM[M, F] =
    implicitly[FoldableM[M, F]]

  implicit def IteratorIsFoldableM[M[_] : Bind]: FoldableM[M, Iterator] =  new FoldableM[M, Iterator] {
    def foldM[A, B](iterator: Iterator[A])(fd: FoldM[M, A, B]): M[B] =
      fd.start.flatMap { st =>
        var state = st
        while (iterator.hasNext)
          state = fd.fold(state, iterator.next)
        fd.end(state)
      }

    def foldMBreak[A, B](iterator: Iterator[A])(fd: FoldM[M, A, B] { type S = B \/ B }): M[B] = {
      @tailrec
      def foldState(it: Iterator[A], state: fd.S): fd.S =
        if (it.hasNext)
            fd.fold(state, it.next) match {
              case \/-(stop)     => \/-(stop)
              case -\/(continue) => foldState(it, -\/(continue))
            }
        else state

      fd.start.flatMap(st => fd.end(foldState(iterator, st)))
    }

  }

  implicit def FoldableIsFoldableM[M[_] : Bind, F[_] : Foldable]: FoldableM[M, F] = new FoldableM[M, F] {
    def foldM[A, B](fa: F[A])(fd: FoldM[M, A, B]): M[B] =
      fd.start.flatMap(st => fd.end(fa.foldLeft(st)(fd.fold)))

    def foldMBreak[A, B](fa: F[A])(fd: FoldM[M, A, B] { type S = B \/ B }): M[B] = {
      @tailrec
      def foldState(stream: EphemeralStream[A], state: fd.S): fd.S =
        stream match {
          case head ##:: tail =>
            fd.fold(state, head) match {
              case \/-(stop)     => \/-(stop)
              case -\/(continue) => foldState(tail, -\/(continue))
            }
          case _ => state
        }

      fd.start.flatMap(st => fd.end(foldState(fa.toEphemeralStream, st)))
    }
  }

}
