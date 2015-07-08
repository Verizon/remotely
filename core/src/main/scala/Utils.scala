//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package remotely

import java.util.NoSuchElementException

import remotely.codecs.DecodingFailure
import scodec.Attempt.{Successful, Failure}
import scodec.{Attempt, Err}

import scalaz.stream.Cause.{End, EarlyCause}
import scalaz.stream.Process.{Halt, Await, Emit, Step}
import scalaz.{\/-, -\/, \/}
import scalaz.concurrent.Task
import scalaz.stream.{Sink, Process}

package object utils {
  implicit class AugmentedEither[E,A](a: E \/ A) {
    def toTask(implicit conv: E => Throwable): Task[A] = a match {
      case -\/(e) => Task.fail(conv(e))
      case \/-(a) => Task.now(a)
    }
    def toProcess(implicit conv: E => Throwable): Process[Task, A] = a match {
      case -\/(e) => Process.fail(conv(e))
      case \/-(a) => Process.emit(a)
    }
    def getLeft = a match {
      case -\/(e) => e
      case _ => throw new NoSuchElementException
    }
  }

  implicit class AugmentedTask[A](t: Task[A]) {
    def onComplete(f: Throwable \/ A => Unit): Task[A] =
      t.attempt.flatMap{a => f(a); t}
  }

  implicit class AugmentedProcess[A](p: Process[Task, A]) {
    def flatten[B](implicit conv: A => Process[Task,B]): Process[Task,B] =
      p.flatMap(conv)
    def uncons: Task[(A, Process[Task, A])] = p.step match {
      case Step(head, next) => head match {
        case Emit(as) => as.headOption.map(x =>
          Task.now((x, Process.emitAll(as drop 1) +: next))) getOrElse
          Task.fail(new scala.NoSuchElementException)
        case Await(task, k) => for {
          e <- task.attempt
          as <- Task.delay(k(EarlyCause.fromTaskResult(e)).run)
          //as <- new Task(toFuture[Process[Task, A]](k(EarlyCause.fromTaskResult(e))))
          u <- as.uncons
        } yield u
      }
      case Halt(cause) => cause match {
        case End => Task.fail(new scala.NoSuchElementException)
        case _ : EarlyCause => Task.fail(cause.asThrowable)
      }
    }
    def observeAll(sink: Sink[Task, Throwable \/ A]): Process[Task, A] = {
      p.attempt().observe(sink).flatten
    }
  }
  implicit def errToE(err: Err) = new DecodingFailure(err)
  implicit def eitherToProcess[A](either: Throwable \/ A): Process[Task, A] = either.toProcess
  implicit class AugmentedAttempt[A](a: Attempt[A]) {
    def toTask(implicit conv: Err => Throwable): Task[A] = a match {
      case Failure(err) => Task.fail(conv(err))
      case Successful(a) => Task.now(a)
    }
    def toProcess(implicit conv: Err => Throwable): Process[Task,A] = a match {
      case Failure(err) => Process.fail(conv(err))
      case Successful(a) => Process.emit(a)
    }
  }
}
