package remotely

import java.util.concurrent.atomic.AtomicReference

import scalaz.concurrent.Task

/** An atomically updatable reference, guarded by the `Task` monad. */
sealed abstract class IORef[A] {
  def read: Task[A]
  def write(value: A): Task[Unit]
  def atomicModify[B](f: A => (A, B)): Task[B]
  def compareAndSet(oldVal: A, newVal: A): Task[Boolean]
  def modify(f: A => A): Task[Unit] =
    atomicModify(a => (f(a), ()))
}

object IORef {
  def apply[A](value: => A): Task[IORef[A]] = Task(new IORef[A] {
    val ref = new AtomicReference(value)
    def read = Task(ref.get)
    def write(value: A) = Task(ref.set(value))
    def compareAndSet(oldVal: A, newVal: A) =
      Task(ref.compareAndSet(oldVal, newVal))
    def atomicModify[B](f: A => (A, B)) = for {
      a <- read
      (a2, b) = f(a)
      p <- compareAndSet(a, a2)
      r <- if (p) Task(b) else atomicModify(f)
    } yield r
  })
}


