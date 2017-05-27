package commons.monads.transformers

import commons.monads.Monad

case class OptionT[M[_], A](inner: M[Option[A]])(implicit m: Monad[M]) {

  def map[B](f: A => B): OptionT[M, B] =
    OptionT {
      m.map(inner) {
        _.map(f)
      }
    }


  def flatMap[B](f: A => OptionT[M, B]): OptionT[M, B] =
    OptionT {
      m.flatMap(inner) {
        case Some(value) => f(value).inner
        case None => m.apply(None)
      }
    }
}
