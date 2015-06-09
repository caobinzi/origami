package com.ambiata
package origami
package effect

import scalaz._, Scalaz._
import FoldM._
import java.io.InputStream
import scala.io.BufferedSource
import FoldId.Bytes

/**
 * Specialized FoldableM instances for the SafeT monad
 */
object FoldableSafeTM {

  private implicit def SafeTMonadM[M[_] : Monad : Catchable] =
    SafeT.SafeTMonad[M]

  implicit def IteratorIsFoldableSafeTM[M[_] : Monad : Catchable, A]: FoldableM[SafeT[M, ?], Iterator[A], A] =
    FoldableM.IteratorIsFoldableM[SafeT[M, ?], A]

  implicit def FoldableIsFoldableSafeTM[M[_] : Monad : Catchable, F[_] : Foldable, A]: FoldableM[SafeT[M, ?], F[A], A] =
    FoldableM.FoldableIsFoldableM[SafeT[M, ?], F, A]

  implicit def BufferedSourceIsFoldableSafeTMS[M[_] : Monad : Catchable, S <: BufferedSource]: FoldableM[SafeT[M, ?], S, String] =
    FoldableM.BufferedSourceIsFoldableMS[SafeT[M, ?], S]

  implicit def inputStreamAsFoldableSafeTM[M[_] : Monad : Catchable, IS <: InputStream]: FoldableM[SafeT[M, ?], IS, Bytes] =
    FoldableM.inputStreamAsFoldableM[SafeT[M, ?], IS]

}
