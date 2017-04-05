package commons.monads

import scala.concurrent.Future
import scala.language.higherKinds


trait Monad[M[_]] {

  def map[A, B](ma: M[A])(f: A => B): M[B]

  def flatMap[A, B](ma: M[A])(f: A => M[B]): M[B]

  def apply[A](a: A): M[A]

}

object Monad {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit object FutureMonad extends Monad[Future] {
    override def map[A, B](ma: Future[A])(f: (A) => B): Future[B] = ma.map(f)

    override def flatMap[A, B](ma: Future[A])(f: (A) => Future[B]): Future[B] = ma.flatMap(f)

    override def apply[A](a: A): Future[A] = Future.successful(a)
  }

}
