package com.ambiata
package origami
package effect

import scalaz.concurrent._
import FoldM._
import SafeT._
import scalaz._, Scalaz._

/** type aliases for Folds using the Task monad */
trait FoldTask extends FoldTaskTypes with FoldTaskImplicits

trait FoldTaskTypes {
  type FoldTask[T, U] = FoldM[Task, T, U]

  type FoldSafeTTask[T, U] = FoldM[SafeTTask, T, U]

  type Sink[T] = FoldSafeTTask[T, Unit]
}

object FoldTask extends FoldTask

object FoldTaskTypes extends FoldTaskTypes

trait FoldTaskImplicits {
  /** Natural transformation from Id to Task */
  implicit def IdTaskNaturalTransformation: Id ~> Task =
    IdMonadNaturalTransformation[Task]

}

object FoldTaskImplicits extends FoldTaskImplicits
