package com.ambiata
package origami

import scala.annotation.tailrec
import scalaz.{EphemeralStream, Bind, ~>, Foldable, \/, \/-, -\/}
import EphemeralStream._
import scalaz.syntax.bind._
import scalaz.syntax.foldable._
import scalaz.std.list._
import java.io.InputStream
import scala.io.BufferedSource
import FoldId.Bytes

/**
 * A structure delivering elements of type A (variable type, like a List) and which 
 * can be folded over
 */
trait FoldableM[M[_], F, A]  { self =>
  def foldM[B](fa: F)(fd: FoldM[M, A, B]): M[B]

  def foldMBreak[B](fa: F)(fd: FoldM[M, A, B] { type S = B \/ B }): M[B]


  def into[G[_], H[_]](implicit nat: G ~> H, ev: H[A] =:= F): FoldableM[M, G[A], A] = new FoldableM[M, G[A], A] {
    def foldM[B](fa: G[A])(fd: FoldM[M, A, B]): M[B] =
     self.foldM(ev(nat(fa)))(fd)

    def foldMBreak[B](fa: G[A])(fd: FoldM[M, A, B] { type S = B \/ B }): M[B] =
      self.foldMBreak(ev(nat(fa)))(fd)
  }
}

object FoldableM {

  def apply[M[_], F, A](implicit fm: FoldableM[M, F, A]): FoldableM[M, F, A] =
    implicitly[FoldableM[M, F, A]]

  implicit def IteratorIsFoldableM[M[_] : Bind, A]: FoldableM[M, Iterator[A], A] =  new FoldableM[M, Iterator[A], A] {
    def foldM[B](iterator: Iterator[A])(fd: FoldM[M, A, B]): M[B] =
      fd.start.flatMap { st =>
        var state = st
        while (iterator.hasNext)
          state = fd.fold(state, iterator.next)
        fd.end(state)
      }

    def foldMBreak[B](iterator: Iterator[A])(fd: FoldM[M, A, B] { type S = B \/ B }): M[B] = {
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

  implicit def FoldableIsFoldableM[M[_] : Bind, F[_] : Foldable, A]: FoldableM[M, F[A], A] = new FoldableM[M, F[A], A] {
    def foldM[B](fa: F[A])(fd: FoldM[M, A, B]): M[B] =
      fd.start.flatMap(st => fd.end(fa.foldLeft(st)(fd.fold)))

    def foldMBreak[B](fa: F[A])(fd: FoldM[M, A, B] { type S = B \/ B }): M[B] = {
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

  implicit def BufferedSourceIsFoldableMS[M[_] : Bind, S <: BufferedSource]: FoldableM[M, S, String] = new FoldableM[M, S, String] {
    def foldM[B](s: S)(fd: FoldM[M, String, B]): M[B] =
      IteratorIsFoldableM[M, String].foldM(s.getLines)(fd)

    def foldMBreak[B](s: S)(fd: FoldM[M, String, B] {type S = B \/ B }): M[B] =
      IteratorIsFoldableM[M, String].foldMBreak(s.getLines)(fd)
  }

  implicit def InputStreamIsFoldableMS[M[_] : Bind, IS <: InputStream]: FoldableM[M, IS, Bytes] =
    inputStreamAsFoldableMS(bufferSize = 4096)
    
  def inputStreamAsFoldableMS[M[_] : Bind, IS <: InputStream](bufferSize: Int): FoldableM[M, IS, Bytes] = new FoldableM[M, IS, Bytes] {
    def foldM[B](is: IS)(fd: FoldM[M, Bytes, B]): M[B] = 
      fd.start.flatMap { st =>
        val buffer = Array.ofDim[Byte](bufferSize)
        var length = 0    
        var state = st
        while ({ length = is.read(buffer, 0, buffer.length); length != -1 })
          state = fd.fold(state, (buffer, length))
        fd.end(state)  
      }

    def foldMBreak[B](is: IS)(fd: FoldM[M, Bytes, B] {type S = B \/ B }): M[B] =
      fd.start.flatMap { st =>
        val buffer = Array.ofDim[Byte](bufferSize)
        var length = 0
        var state = st
        var break = false
        while ({ length = is.read(buffer, 0, buffer.length); length != -1 && !break }) {
          state = fd.fold(state, (buffer, length))
          state match {
            case \/-(s) => break = true
            case -\/(s) => ()
          }
        }
        fd.end(state)
      }
  }


}
