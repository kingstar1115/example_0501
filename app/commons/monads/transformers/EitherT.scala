package commons.monads.transformers

import commons.monads.Monad

import scala.language.higherKinds

case class EitherT[M[_], L, R](inner: M[Either[L, R]])(implicit m: Monad[M]) {
  def map[B](f: R => B): EitherT[M, L, B] = EitherT {
    m.map(inner) {
      _.right.map(f)
    }
  }

  def flatMap[B <: Any](f: R => EitherT[M, L, B]): EitherT[M, L, B] = EitherT {
    m.flatMap(inner) {
      case Right(r) =>
        f(r).inner
      case Left(x) =>
        m.apply(Left(x))
    }
  }
}
