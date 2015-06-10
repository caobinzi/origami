package com.ambiata
package origami
package effect

import scalaz.concurrent._
import FoldM._
import SafeT._

/** type aliases for Folds using the Task monad */
object FoldTask {
  type FoldTask[T, U] = FoldM[Task, T, U]

  type FoldSafeTTask[T, U] = FoldM[SafeTTask, T, U]

  type Sink[T] = FoldSafeTTask[T, Unit]
}
