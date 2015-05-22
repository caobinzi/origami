package com.ambiata
package origami

import scalaz.{Apply, Functor, Profunctor, Monoid, Compose, Monad, Category, MonadPlus, ~>, Order, StateT}
import scalaz.effect.IO
import scalaz.Id._
import scalaz._
import scalaz.std.anyVal._
import scalaz.syntax.monad._
import scalaz.syntax.foldable._
import scalaz.std.list._
import java.security.MessageDigest
import FoldM._

/**
 * A FoldM is a "left fold" over a data structure with:
 *  - a 'start' value
 *  - a 'fold' method to accumulate state
 *  - an 'end' method to finalize the result
 *  
 * Both 'start' and 'end' have an effect which allows the whole folding to take place inside a context M. 
 * 
 * If 'M' has an 'Apply' instance then FoldM can be made Applicative to allow the folding of two values U and S at 
 * the same time.
 * 
 * If 'M' has a 'Monad' instance then FoldM can be made into a 'Compose' instance which allows to 
 * compose 2 folds into one, for example:
 * 
 *  - 'sum' computes the sum of some elements
 *  - 'list' accumulates all the elements in a list
 *  - the 'sum compose list' will accumulate the list of all the sums over some elements (this is a 'scan')
 *
 * A FoldM can be used with a 'FoldableM' which produces the elements to fold over. Examples of FoldableM include
 * 
 *  - a List
 *  - an Iterator
 *  - a scalaz Process
 *  
 * Usage example:
 * 
 *  sum.run(List(1, 2, 3)) == 6 
 */
trait FoldM[M[_], T, U] { self =>
  type S

  def start: M[S]
  def fold: (S, T) => S
  def end(s: S): M[U]

  /** map the output value */
  def map[V](f: U => V)(implicit m: Functor[M]) = new FoldM[M, T, V] {
    type S = self.S
    def start = self.start
    def fold = self.fold
    def end(s: S) = m.map(self.end(s))(f)
  }

  /** flatMap the output value */
  def mapFlatten[V](f: U => M[V])(implicit m: Bind[M]) = new FoldM[M, T, V] {
    type S = self.S
    def start = self.start
    def fold = self.fold
    def end(s: S) = m.bind(self.end(s))(f)
  }

  /** map with another fold */
  def pipe[V](f: FoldM[M, U, V])(implicit m: Bind[M]) = new FoldM[M, T, V] {
    type S = self.S
    def start = self.start
    def fold = self.fold
    def end(s: S) = m.bind(self.end(s))(f.run1)
  }

  /** parallel composition */
  def ***[V, W](f: FoldM[M, V, W])(implicit m: Bind[M]) = new FoldM[M, (T, V), (U, W)] {
    type S = (self.S, f.S)
    def start = m.tuple2(self.start, f.start)
    def fold = (s: S, tv: (T, V)) => (self.fold(s._1, tv._1), f.fold(s._2, tv._2))
    def end(s: S) = m.tuple2(self.end(s._1), f.end(s._2))
  }

  /** first operator on a MonadPlus monad */
  def first[V](implicit m: MonadPlus[M]) =
    ***(idM[M, V])

  /** second operator on a MonadPlus monad */
  def second[V](implicit m: MonadPlus[M]) =
    idM[M, V] *** self

  /** first-like operator */
  def firstOption[V](implicit m: Bind[M], nat: Id ~> M) =
    ***(FoldId.last[V].into[M])

  /** second-like operator */
  def secondOption[V](implicit m: Bind[M], nat: Id ~> M) =
    FoldId.last[V].into[M] *** self

  /** fanout = zip in the Arrow terminology */
  def &&&[V](f: FoldM[M, T, V])(implicit ap: Apply[M]) =
    zip(f)

  /** contramap the input values */
  def contramap[R](f: R => T)(implicit m: Functor[M]) = new FoldM[M, R, U] {
    type S = self.S
    def start = self.start
    def fold = (s: S, r: R) => self.fold(s, f(r))
    def end(s: S) = self.end(s)
  }

  /** zip 2 folds to return a pair of values. alias for zip */
  def <*>[V](f: FoldM[M, T, V])(implicit ap: Apply[M]) =
    zip(f)

  /** zip 2 folds to return a pair of values. alias for <*> */
  def zip[V](f: FoldM[M, T, V])(implicit ap: Apply[M]) = new FoldM[M, T, (U, V)] {
    type S = (self.S, f.S)
    def start = ap.tuple2(self.start, f.start)
    def fold = (s, t) => (self.fold(s._1, t), f.fold(s._2, t))
    def end(s: S) = ap.tuple2(self.end(s._1), f.end(s._2))
  }

  /** zip with another fold only for its side effects */
  def <*[V](f: SinkM[M, T])(implicit ap: Apply[M]) =
    zip(f).map(_._1)

  /** alias for <* */
  def observe[V](f: SinkM[M, T])(implicit ap: Apply[M]) =
    zip(f).map(_._1)

  /** observe both the input value and the current state */
  def observeState[V](sink: SinkM[M, (S, T)])(implicit ap: Apply[M]) = new FoldM[M, T, U] {
    type S = (self.S, sink.S)
    def start = ap.tuple2(self.start , sink.start)
    def fold = (s: S, t: T) => (self.fold(s._1, t), sink.fold(s._2, (s._1, t)))
    def end(s: S) = ap.tuple2(self.end(s._1), sink.end(s._2)).map(_._1)
  }

  /** alias for observeState */
  def <<*[V](sink: SinkM[M, (S, T)])(implicit ap: Apply[M]) =
    observeState(sink)

  /** observe both the input value and the next state */
  def observeNextState[V](sink: SinkM[M, (S, T)])(implicit ap: Apply[M]) = new FoldM[M, T, U] {
    type S = (self.S, sink.S)
    def start = ap.tuple2(self.start , sink.start)
    def fold = (s: S, t: T) => { val next = self.fold(s._1, t); (next, sink.fold(s._2, (next, t))) }
    def end(s: S) = ap.tuple2(self.end(s._1), sink.end(s._2)).map(_._1)
  }

  /** alias for observeNextState */
  def <<<*[V](sink: SinkM[M, (S, T)])(implicit ap: Apply[M]) =
    observeNextState(sink)

  /** pipe the output of this fold into another fold */
  def compose[V](f2: FoldM[M, U, V])(implicit m: Monad[M]) = new FoldM[M, T, V] {
    type S = M[(self.S, f2.S)]
    def start = Monad[M].point(Apply[M].tuple2(self.start, f2.start))

    def fold = (s, t) =>
      s.flatMap { case (f1s, f2s) =>
        self.end(self.fold(f1s, t)).map((u: U) => (self.fold(f1s, t), f2.fold(f2s, u)))
      }

    def end(s: S) = s.flatMap { case (f1s, f2s) =>
      f2.end(f2s)
    }
  }
  
  /** create a fold that will run this fold repeatedly on input elements and collect all results */
  def nest[F[_], R](f: R => F[T])(implicit df: FoldableM[M, F], monoid: Monoid[U], monad: Monad[M]) = new FoldM[M, R, U] {
    type S = M[U]
    def start = monad.point(monad.point(monoid.zero)) 
    def fold = (s: S, r: R) =>
        Monad[M].apply2(self.run(f(r)): M[U], s: M[U])((s1: U, s2: U) => monoid.append(s1, s2))
        
    def end(s: S) = s
  }

  /**
   * run a FoldM with a FoldableM instance (like a List, an Iterator, a scalaz Process)
   */
   def run[F[_]](ft: F[T])(implicit foldableM: FoldableM[M, F]): M[U] =
     foldableM.foldM(ft)(this)

  /**
   * run a FoldM with a FoldableM instance (like a List, an Iterator, a scalaz Process)
   * and break early if possible
   */
  def runBreak[F[_]](ft: F[T])(implicit foldableM: FoldableM[M, F], ev: S <:< (U \/ U)): M[U] =
    foldableM.foldMBreak(ft)(self.asInstanceOf[FoldM[M, T, U] { type S = U \/ U }])

  /**
   * run a FoldM with a FoldableMS instance (like an InputStream which is specialized on producing Array[Byte])
   */
   def runS[F](f: F)(implicit foldableMS: FoldableMS[M, F, T]): M[U] =
     foldableMS.foldM(f)(this)

  /**
   * run over one element
   */
  def run1(t: T)(implicit m: Bind[M]): M[U] =
    m.bind(start)(s => end(fold(s, t)))

  /**
   * use a natural transformation to go from context M to context N
   * this can be used to transform a FoldM[A, Id, B] into a FoldM[A, Task, B] for example
   * (a fold with no effects to a fold with monadic effects from the Task monad)  
   */
  def into[N[_]](implicit nat: M ~> N) = new FoldM[N, T, U] {
    type S = self.S
    def start = nat(self.start)
    def fold = (s, t) => self.fold(s, t)
    def end(s: S) = nat(self.end(s))
  }

  /** equivalent of the as method for functors, added here for easier type inference */
  def as[V](v: =>V)(implicit m: Functor[M]) =
    map(_ => v)

  /** equivalent of the void method for functors, added here for easier type inference */
  def void(implicit m: Functor[M]) =
    as(())
}

/**
 * Typeclass instances and creation methods for folds
 */
object FoldM {

  /** alias for a non-effectful Fold */
  type Fold[T, U] = FoldM[Id, T, U]

  /** alias for a non-effectful Fold where the state type is U */
  type FoldState[T, U] = FoldM[Id, T, U] { type S = U }

  /** alias for a Fold sinking its last value */
  type SinkM[M[_], T] = FoldM[M, T, Unit]

  /** @return a fold which uses a Monoid to accumulate elements */
  def fromMonoidMap[T, M : Monoid](f: T => M) = new Fold[T, M] {
    type S = M
    def start = Monoid[M].zero
    def fold = (s: S, t: T) => Monoid[M].append(s, f(t))
    def end(s: S) = s
  }

  /** @return a fold from arguments of a fold left */
  def fromFoldLeft[T, U](u: U)(f: (U, T) => U) = new Fold[T, U] {
    type S = U
    def start = u
    def fold = f
    def end(s: S) = s
  }

  /** @return a fold from a Monoid */
  def fromMonoid[M : Monoid] =
    fromMonoidMap[M, M](identity _)

  /** @return a fold from a Reducer */
  def fromReducer[T, S](reducer: Reducer[T, S]) =
    fromFoldLeft(reducer.monoid.zero)((s: S, t: T) => reducer.cons(t, s))

    /** @return a fold from running a State object */
  def fromStateRun[M[_]: Monad, T, U, V](state: T => State[U, V])(init: U) = new FoldM[M, T, (U, Option[V])] {
    type S = (U, Option[V])
    def start = Monad[M].point((init, None))
    def fold = (s: S, t: T) => {
      val (st, v) = s
      val (newState, newV) = state(t).run(st)
      (newState, Some(newV))
    }
    def end(s: S) = Monad[M].point(s)
  }

  /** @return a fold for the execution of a State object */
  def fromStateExec[M[_]: Monad, T, U, V](state: T => State[U, V])(init: U) =
    fromStateRun(state)(init)(Monad[M]).map(_._1)

  /** @return a fold for the evaluation of a State object */
  def fromStateEval[M[_]: Monad, T, U, V](state: T => State[U, V])(init: U) =
    fromStateRun(state)(init)(Monad[M]).map(_._2)

  /** @return a fold with just a start action */
  def fromStart[M[_]: Monad, T, S1](action: M[S1]) = new FoldM[M, T, S1] {
    type S = S1
    def start = action
    def fold = (s: S, t: T) => s
    def end(s: S) = Monad[M].point(s)
  }

  /**
   * Typeclass instances
   */
  
  /** 
   * Apply instance
   *  
   * This means that we can write:
   *  
   *   val mean: Fold[Int, Int] = (sum |@| count)(_ / _)
   *   
   * An Apply instance is also a Functor instance so we can write:
   *  
   *   val meanTimes2 = mean.map(_ * 2)   
   */
  implicit def FoldMApply[M[_] : Apply, T]: Apply[FoldM[M, T, ?]] = new Apply[FoldM[M, T, ?]] {
    type F[U] = FoldM[M, T, U]

    def map[A, B](fa: F[A])(f: A => B): FoldM[M, T, B] = 
      fa map f

    def ap[A, B](fa: => F[A])(f: => F[A => B]): F[B] =
      map(fa zip f) { case (a, b) => b(a) }
  }

  /** 
   *  Profunctor instance 
   *  
   *  This is especially useful because we can "map" on the input element
   *  
   *  val doubleSum = fromMonoid[Double] // sum all elements
   *  val roundedDoubleSum = doubleSum.mapfst(_.round)
   */
  implicit def FoldMProfunctor[M[_] : Functor]: Profunctor[FoldM[M, ?, ?]] = new Profunctor[FoldM[M, ?, ?]] {
    type =>:[T, U] = FoldM[M, T, U]

    /** Contramap on `A`. */
    def mapfst[A, B, C](fab: (A =>: B))(f: C => A): (C =>: B) =
      fab.contramap(f)

    /** Functor map on `B`. */
    def mapsnd[A, B, C](fab: (A =>: B))(f: B => C): (A =>: C) =
      fab map f
  }

  /**
   * A FoldM can be turned into a Category if M has a MonadPlus instance
   */
  def FoldMCategory[M[_] : MonadPlus]: Category[FoldM[M, ?, ?]] = new Category[FoldM[M, ?, ?]] {
    type F[A,B] = FoldM[M, A, B]

    def id[A] = idM[M, A]
    def compose[A, B, C](f: F[B, C], g: F[A, B]): F[A, C] =
      FoldMCompose[M].compose(f, g)
  }

  /** monoid to append sinks effects */
  implicit def SinkMMonoid[M[_] : Monad, T]: Monoid[SinkM[M, T]] = new Monoid[SinkM[M, T]] {
    def zero = unitSink

    def append(a1: SinkM[M, T], a2: => SinkM[M, T]): SinkM[M, T] =
      (a1 zip a2).void
  }

  /** sink doing nothing */
  def unitSink[M[_]: Monad, T] = new FoldM[M, T, Unit] {
    type S = Unit
    def start = Monad[M].point(())
    def fold = (s: S, t: T) => s
    def end(s: S) = Monad[M].point(())
  }

  /** identity fold for a MonadPlus monad */
  def idM[M[_] : MonadPlus, A] = new FoldM[M, A, A] {
    type S = M[A]
    def start: M[M[A]] = Monad[M].point(MonadPlus[M].empty[A])
    def fold = (s: S, a: A) => Monad[M].point(a)
    def end(a: M[A]) = a
  }

  /**
   * A FoldM can be turned into a Compose if M has a Monad instance
   * 
   * This allows us to write:
   * 
   * val scans = sum compose list
   * 
   */
  implicit def FoldMCompose[M[_] : Monad]: Compose[FoldM[M, ?, ?]] = new Compose[FoldM[M, ?, ?]] {
    type F[A,B] = FoldM[M, A, B]

    def compose[A, B, C](f: F[B, C], g: F[A, B]): F[A, C] = 
      g compose f
  }

  /** 
   * Cobind instance
   */
  def FoldMCobind[M[_] : Monad, T]: Cobind[FoldM[M, T, ?]] = new Cobind[FoldM[M, T, ?]] {
    type F[U] = FoldM[M, T, U]

    def cobind[A, B](fa: F[A])(f: F[A] => B): F[B] = new F[B] {
      type S = fa.S
      def start = fa.start
      def fold = fa.fold
      def end(s: S) = Monad[M].point(f(fa))
    }

    def map[A, B](fa: F[A])(f: A => B): FoldM[M, T, B] = 
      fa map f
  }

  /** 
   * Comonad instance for Fold
   */
  implicit def FoldComonad[T]: Comonad[Fold[T, ?]] = new Comonad[Fold[T, ?]] {
    type F[U] = Fold[T, U]

    def copoint[A](fa: F[A]): A = fa.end(fa.start)
    
    def cobind[A, B](fa: F[A])(f: F[A] => B): F[B] = new F[B] {
      type S = fa.S
      def start = fa.start
      def fold = fa.fold
      def end(s: S) = f(fa)
    }

    def map[A, B](fa: F[A])(f: A => B): Fold[T, B] = 
      fa map f
  }

  /** Natural transformation from Id to IO */
  implicit val IdIONaturalTransformation: Id ~> IO = new (Id ~> IO) {
    def apply[A](i: Id[A]): IO[A] = IO(i)
  }

  /** Natural transformation from a List to an Iterator */
  implicit val ListIteratorNaturalTransformation: List ~> Iterator = new (List ~> Iterator) {
    def apply[A](i: List[A]): Iterator[A] = i.iterator
  }

}
