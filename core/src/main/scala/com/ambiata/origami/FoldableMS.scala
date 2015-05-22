package com.ambiata
package origami

import scala.io.BufferedSource
import scalaz.{\/, \/-, -\/, Bind, ~>}
import scalaz.syntax.bind._
import java.io.InputStream
import FoldId.Bytes

/**
 * A structure delivering elements of type A (fixed type, like an InputStream) and which 
 * can be folded over
 */
trait FoldableMS[M[_], F, A]  { self =>
  def foldM[B](fa: F)(fd: FoldM[M, A, B]): M[B]
  def foldMBreak[B](fa: F)(fd: FoldM[M, A, B] {type S = B \/ B }): M[B]
}

object FoldableMS {

  implicit def BufferedSourceIsFoldableMS[M[_] : Bind, S <: BufferedSource]: FoldableMS[M, S, String] = new FoldableMS[M, S, String] {
    def foldM[B](s: S)(fd: FoldM[M, String, B]): M[B] =
      FoldableM.IteratorIsFoldableM.foldM(s.getLines)(fd)

    def foldMBreak[B](s: S)(fd: FoldM[M, String, B] {type S = B \/ B }): M[B] =
      FoldableM.IteratorIsFoldableM.foldMBreak(s.getLines)(fd)
  }

  implicit def InputStreamIsFoldableMS[M[_] : Bind, IS <: InputStream]: FoldableMS[M, IS, Bytes] =
    inputStreamAsFoldableMS(bufferSize = 4096)
    
  def inputStreamAsFoldableMS[M[_] : Bind, IS <: InputStream](bufferSize: Int): FoldableMS[M, IS, Bytes] = new FoldableMS[M, IS, Bytes] {
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
