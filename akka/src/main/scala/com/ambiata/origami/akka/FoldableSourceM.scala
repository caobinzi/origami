package com.ambiata
package origami
package akka

import Origami._
import _root_.akka.stream.scaladsl.{Sink => AkkaSink, _}
import _root_.akka.stream.FlowMaterializer
import scala.concurrent.{ExecutionContext, Future}
import scalaz._, Scalaz._

/**
 * Foldable instance for akka streams
 */
object FoldableSourceM {

  implicit def SourceFoldableM[M[_], Mat, A](implicit M: Monad[M],
                                             nat: Future ~> M,
                                             materializer: FlowMaterializer): FoldableM[M, Source[A, Mat], A] =
    new FoldableM[M, Source[A, Mat], A] {
      def foldM[B](fa: Source[A, Mat])(fd: FoldM[M, A, B]): M[B] = {
        fd.start.flatMap { st =>
          nat(fa.runWith(AkkaSink.fold(st)(fd.fold))).flatMap(fd.end)
        }
      }

      /** breaking processes is not implemented yet */
      def foldMBreak[B, S1](fa: Source[A, Mat])(fd: FoldM[M, A, B] { type S = S1 \/ S1 }) =
        foldM(fa)(fd)
    }

}
