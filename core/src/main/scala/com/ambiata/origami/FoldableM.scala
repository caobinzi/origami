package com.ambiata
package origami

import scala.annotation.tailrec
import scalaz.{EphemeralStream, Bind, ~>, Foldable, \/, \/-, -\/, Monad}
import EphemeralStream._
import scalaz.syntax.monad._
import scalaz.syntax.foldable._
import scalaz.std.list._
import java.io.InputStream
import scala.io.BufferedSource
import FoldId.Bytes
import FoldM._

/**
 * A structure delivering elements of type A (variable type, like a List) and which 
 * can be folded over
 */
trait FoldableM[M[_], F, A]  { self =>
  def foldM[B](fa: F)(fd: FoldM[M, A, B]): M[B]

  def foldMBreak[B, S1](fa: F)(fd: FoldM[M, A, B] { type S = S1 \/ S1 }): M[B]


  def into[G[_], H[_]](implicit nat: G ~> H, ev: H[A] =:= F): FoldableM[M, G[A], A] = new FoldableM[M, G[A], A] {
    def foldM[B](fa: G[A])(fd: FoldM[M, A, B]): M[B] =
     self.foldM(ev(nat(fa)))(fd)

    def foldMBreak[B, S1](fa: G[A])(fd: FoldM[M, A, B] { type S = S1 \/ S1 }): M[B] =
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

    def foldMBreak[B, S1](iterator: Iterator[A])(fd: FoldM[M, A, B] { type S = S1 \/ S1 }): M[B] = {
      @tailrec
      def foldState(it: Iterator[A], state: fd.S): fd.S = {
        state match {
          case \/-(_) => state
          case -\/(_) =>
            if (it.hasNext)
              fd.fold(state, it.next) match {
                case \/-(stop)     => \/-(stop)
                case -\/(continue) => foldState(it, -\/(continue))
              }
          else state
        } 
      }

      fd.start.flatMap(st => fd.end(foldState(iterator, st)))
    }

  }

  implicit def FoldableIsFoldableM[M[_] : Bind, F[_] : Foldable, A]: FoldableM[M, F[A], A] = new FoldableM[M, F[A], A] {
    def foldM[B](fa: F[A])(fd: FoldM[M, A, B]): M[B] =
      fd.start.flatMap(st => fd.end(fa.foldLeft(st)(fd.fold)))

    def foldMBreak[B, S1](fa: F[A])(fd: FoldM[M, A, B] { type S = S1 \/ S1 }): M[B] = {
      @tailrec
      def foldState(stream: EphemeralStream[A], state: fd.S): fd.S =
        stream match {
          case head ##:: tail =>
            state match {
              case \/-(_) => state
              case -\/(_) => 
                fd.fold(state, head) match {
                  case \/-(stop)     => \/-(stop)
                  case -\/(continue) => foldState(tail, -\/(continue))
                }
          }
          case _ => state
        }

      fd.start.flatMap(st => fd.end(foldState(fa.toEphemeralStream, st)))
    }
  }

  implicit def BufferedSourceIsFoldableMS[M[_] : Monad, S <: BufferedSource]: FoldableM[M, S, String] = new FoldableM[M, S, String] {
    def foldM[B](s: S)(fd: FoldM[M, String, B]): M[B] =
      IteratorIsFoldableM[M, String].foldM(s.getLines)(fd)  <* Monad[M].point(s.close)

    def foldMBreak[B, S1](s: S)(fd: FoldM[M, String, B] {type S = S1 \/ S1 }): M[B] =
      IteratorIsFoldableM[M, String].foldMBreak(s.getLines)(fd) <* Monad[M].point(s.close)
  }

  implicit def inputStreamAsFoldableM[M[_] : Monad, IS <: InputStream]: FoldableM[M, IS, Bytes] =
    inputStreamAsFoldableMS(bufferSize = 4096)
    
  def inputStreamAsFoldableMS[M[_] : Monad, IS <: InputStream](bufferSize: Int): FoldableM[M, IS, Bytes] = new FoldableM[M, IS, Bytes] {
    def foldM[B](is: IS)(fd: FoldM[M, Bytes, B]): M[B] = 
      fd.start.flatMap { st =>
        val buffer = Array.ofDim[Byte](bufferSize)
        var length = 0    
        var state = st
        while ({ length = is.read(buffer, 0, buffer.length); length != -1 })
          state = fd.fold(state, (buffer, length))
        fd.end(state) <* Monad[M].point(is.close) 
      }

    def foldMBreak[B, S1](is: IS)(fd: FoldM[M, Bytes, B] {type S = S1 \/ S1 }): M[B] =
      fd.start.flatMap { st =>
        var state = st
        state match {
          case \/-(_) => state
          case -\/(_) =>
        
            val buffer = Array.ofDim[Byte](bufferSize)
            var length = 0
            var break = false
            while ({ length = is.read(buffer, 0, buffer.length); length != -1 && !break }) {
              state = fd.fold(state, (buffer, length))
              state match {
                case \/-(s) => break = true
                case -\/(s) => ()
              }
            }
        }
        fd.end(state)
      }
  }

  val laws = new FoldableMLaws {}

}

import scalaz.Id, Id._

/**
 * Laws for FoldableM instances
 */
trait FoldableMLaws {

  /**
   * The break law states that all the successive states
   * of a given fold must not satisfy a 
   */
  def breakLaw[F, T, U, S1](foldableM: FoldableM[Id, F, T], stopPredicate: S1 => Boolean, aFold: Fold[T, U] { type S = S1 }, f: F) = {
    val collected = new scala.collection.mutable.ListBuffer[Boolean]

    val breakableFold: Fold[T, U] { type S = aFold.S \/ aFold.S } = new Fold[T, U] {
      type S = aFold.S \/ aFold.S
      
      def start = {
        collected.append(stopPredicate(aFold.start))
        if (stopPredicate(aFold.start)) \/-(aFold.start) else -\/(aFold.start)
      }
      def fold = (s: S, t: T) => {
        val newS = s.fold(aFold.fold(_, t), aFold.fold(_, t))

        collected.append(stopPredicate(newS))
        if (stopPredicate(newS)) \/-(newS) else -\/(newS)        
      }

      def end(s: S) = s.fold(aFold.end _, aFold.end _)
    }

    implicit val fm = foldableM
    breakableFold.runBreak(f)

    collected.toList.count(identity _) <= 1   
  }
}
