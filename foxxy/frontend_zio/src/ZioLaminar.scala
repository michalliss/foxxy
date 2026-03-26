package foxxy.frontend_zio

import com.raquo.laminar.api.L.*
import com.raquo.laminar.inserters.{ChildInserter, ChildrenInserter}
import com.raquo.laminar.modifiers.{RenderableNode, RenderableSeq}
import com.raquo.laminar.nodes.{ChildNode, ReactiveElement, ReactiveHtmlElement}
import com.raquo.laminar.receivers.{ChildReceiver, ChildrenReceiver}
import org.scalajs.dom
import zio.*
import zio.stream.*

import scala.util.Try

object ZioLaminar {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  extension [E, A](effect: ZIO[Any, E, A])
    def toFutureUnsafe =
      Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.runToFuture(effect.mapError(x => new RuntimeException(x.toString))))

    def runUnsafe =
      Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(effect.mapError(x => new RuntimeException(x.toString))))

    def toEventStream =
      EventStream.fromFuture(effect.toFutureUnsafe)

  extension [E](effect: ZIO[Any, E, HtmlElement])
    def asChild                            = child <-- EventStream.fromFuture(effect.toFutureUnsafe)
    def asChildWithLoading(x: HtmlElement) = child <-- EventStream.fromFuture(effect.toFutureUnsafe).toSignal(x)

  extension [E, A](stream: ZStream[Any, E, A])
    def toEventStream = {
      val endStreamPromise = scala.concurrent.Promise[Unit]()
      EventStream.fromCustomSource[A](
        start = (fireEvent, fireError, _, _) => {
          stream
            .interruptWhen(ZIO.fromPromiseScala(endStreamPromise))
            .runForeachChunk(x => ZIO.succeed(x.foreach(elem => fireEvent(elem))))
            .tapError(x => ZIO.succeed(fireError(Throwable(x.toString()))))
            .toFutureUnsafe
        },
        stop = _ => {
          println("stopped")
          endStreamPromise.trySuccess(())
        }
      )
    }

  class ZioObserver[A](fn: A => ZIO[Any, Throwable, Unit]) extends Observer[A] {

    override def onNext(nextValue: A): Unit = { fn(nextValue).toFutureUnsafe }

    override def onError(err: Throwable): Unit = ()

    override def onTry(nextValue: Try[A]): Unit = nextValue.map(onNext)

  }

  given zioSource[R, A](using r: zio.Runtime[R]): scala.Conversion[ZIO[R, Throwable, A], Source[A]] with {
    def apply(z: ZIO[R, Throwable, A]): Source[A] =
      val s = zio.Unsafe.unsafe(implicit u => r.unsafe.runToFuture(z)).future
      new com.raquo.airstream.core.Source[A] {
        override def toObservable: Observable[A] = EventStream.fromFuture(s)
      }
  }

  given zioSourceS[R, A](using r: zio.Runtime[R]): scala.Conversion[Signal[List[ZIO[R, Throwable, A]]], Source[List[A]]] with {
    def apply(z: Signal[List[ZIO[R, Throwable, A]]]): Source[List[A]] =
      val res = z.map(x => {
        val s = zio.Unsafe
          .unsafe(implicit u =>
            r.unsafe.runToFuture(
              ZIO.foreach(x)(y => y)
            )
          )
          .future
        s
      })
      res.flatMapSwitch(x => EventStream.fromFuture(x))
  }

  given zioModifiers[R, El <: ReactiveElement.Base](using
      r: zio.Runtime[R]
  ): scala.Conversion[ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]], Modifier[El]] with {
    def apply(z: ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]]): Modifier[El] =
      val s = zio.Unsafe.unsafe(implicit u => r.unsafe.runToFuture(z)).future
      child <-- EventStream.fromFuture(s)
  }

  import scala.scalajs.js

  extension (e: ChildReceiver.type) {
    def <---[R](childSource: ZIO[R, Throwable, ChildNode.Base])(using r: zio.Runtime[R]): DynamicInserter = {
      val fut = zio.Unsafe.unsafe(implicit u => r.unsafe.runToFuture(childSource)).future
      ChildInserter(EventStream.fromFuture(fut).distinct.toObservable, RenderableNode.nodeRenderable, initialHooks = js.undefined)
    }
  }

  extension (e: ChildReceiver.type) {
    def <---[R](childSource: ZIOChild[R])(using r: zio.Runtime[R]): DynamicInserter = {
      val fut = zio.Unsafe.unsafe(implicit u => r.unsafe.runToFuture(childSource())).future
      ChildInserter(EventStream.fromFuture(fut).distinct.toObservable, RenderableNode.nodeRenderable, initialHooks = js.undefined)
    }
  }

  extension (e: ChildReceiver.type) {
    def <---[R](childSource: Observable[ZIOChild[R]])(using r: zio.Runtime[R]): DynamicInserter = {
      val sig = childSource.distinct.flatMapSwitch(x =>
        val fut = zio.Unsafe.unsafe(implicit u => r.unsafe.runToFuture(x())).future
        EventStream.fromFuture(fut)
      )
      ChildInserter(sig.distinct, RenderableNode.nodeRenderable, initialHooks = js.undefined)
    }
  }

  extension (e: ChildrenReceiver.type) {
    def <---[R](childrenSource: Observable[Seq[ZIOChild[R]]])(using r: zio.Runtime[R]): DynamicInserter = {
      val sig = childrenSource.distinct
        .map(x => {
          val fut = zio.Unsafe.unsafe(implicit u => r.unsafe.runToFuture(ZIO.foreach(x)(y => y()))).future
          fut
        })
        .flatMapSwitch(x => EventStream.fromFuture(x))
      ChildrenInserter(
        sig.toObservable,
        RenderableSeq.collectionSeqRenderable,
        RenderableNode.nodeRenderable,
        initialHooks = js.undefined
      )
    }
  }

  def zioComponent[R](renderFn: zio.Runtime[R] ?=> ZIO[R, Throwable, HtmlElement]) = new ZIOComponent[R] {
    def render: zio.Runtime[R] ?=> ZIO[R, Throwable, HtmlElement] = renderFn
  }

  def zioPropsComponent[R, Props](
      renderFn: zio.Runtime[R] ?=> Signal[Props] => ZIO[R, Throwable, HtmlElement]
  ): ZIOComponent2[R, Props] = new ZIOComponent2[R, Props] {
    def render: zio.Runtime[R] ?=> Signal[Props] => ZIO[R, Throwable, HtmlElement] = renderFn
  }

  def zioComponent3[R, Args, Props](
      renderFn: zio.Runtime[R] ?=> Args => Signal[Props] => ZIO[R, Throwable, HtmlElement]
  ): ZIOComponent3[R, Args, Props] = new ZIOComponent3[R, Args, Props] {
    def render: zio.Runtime[R] ?=> Args => Signal[Props] => ZIO[R, Throwable, HtmlElement] = renderFn
  }

  def zioVDiv[R](renderFn: zio.Runtime[R] ?=> ZIO[R, Throwable, HtmlElement]) = new ZIOComponent[R] {
    def render: zio.Runtime[R] ?=> ZIO[R, Throwable, HtmlElement] = renderFn
  }

  def zioChild[R](
      f: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]]
  ): ZIOChild[R] =
    new ZIOChild[R] {
      def render: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]] = f
    }

  def zioChildPure[R](
      f: zio.Runtime[R] ?=> ChildNode[org.scalajs.dom.Node]
  ): ZIOChild[R] =
    new ZIOChild[R] {
      def render: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]] = ZIO.attempt(f)
    }

  def zioChildAP[R, Args, Props](
      f: zio.Runtime[R] ?=> Args => Signal[Props] => ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]]
  ): Args => Signal[Props] => ZIOChild[R] = (args: Args) =>
    (props: Signal[Props]) =>
      new ZIOChild[R] {
        def render: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]] = f(args)(props)
      }

  given childNodeToZIO[R]: Conversion[ChildNode[dom.Node], ZIO[R, Throwable, ChildNode[dom.Node]]] with
    def apply(c: ChildNode[dom.Node]) = ZIO.succeed(c) // beware: evaluation happens before this conversion

  def zchild[R](
      f: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]]
  ): ZIOChild[R] =
    new ZIOChild[R] {
      def render: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]] = f
    }

  def zchildP[R, Props](
      f: zio.Runtime[R] ?=> Signal[Props] => ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]]
  ): Signal[Props] => ZIOChild[R] = (props: Signal[Props]) =>
    new ZIOChild[R] {
      def render: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]] = f(props)
    }

  def zchildA[R, Args](
      f: zio.Runtime[R] ?=> Args => ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]]
  ): Args => ZIOChild[R] = (args: Args) =>
    new ZIOChild[R] {
      def render: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]] = f(args)
    }

  def zchildAP[R, Args, Props](
      f: zio.Runtime[R] ?=> (Args, Signal[Props]) => ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]]
  ): (Args, Signal[Props]) => ZIOChild[R] = (args: Args, props: Signal[Props]) =>
    new ZIOChild[R] {
      def render: zio.Runtime[R] ?=> ZIO[R, Throwable, ChildNode[org.scalajs.dom.Node]] = f(args, props)
    }

  def zioDivAP[R, Args, Props](
      renderFn: zio.Runtime[R] ?=> Args => Signal[Props] => ZIO[R, Throwable, HtmlElement]
  ): ZIOComponent3[R, Args, Props] = new ZIOComponent3[R, Args, Props] {
    def render: zio.Runtime[R] ?=> Args => Signal[Props] => ZIO[R, Throwable, HtmlElement] = renderFn
  }

  trait ZIOComponent[-Env] {
    def render: zio.Runtime[Env] ?=> ZIO[Env, Throwable, HtmlElement]
    def apply()(using r: zio.Runtime[Env]) = render
  }

  trait ZIOComponent2[-Env, Props] {
    def render: zio.Runtime[Env] ?=> Signal[Props] => ZIO[Env, Throwable, HtmlElement]
    def apply(props: Signal[Props])(using r: zio.Runtime[Env]) = render(props)
  }

  trait ZIOComponent3[-Env, -Args, Props] {
    def render: zio.Runtime[Env] ?=> Args => Signal[Props] => ZIO[Env, Throwable, HtmlElement]
    def apply(args: Args)(props: Signal[Props])(using r: zio.Runtime[Env]) = render(args)(props)
  }

  trait ZIOChild[-Env] {
    def render: zio.Runtime[Env] ?=> ZIO[Env, Throwable, ChildNode[org.scalajs.dom.Node]]
    def apply()(using r: zio.Runtime[Env]) = render
  }

  class StateManagerImpl[-R, S, E](initial: S, reducer: (S, E) => ReducerResult[S, E])(using r: zio.Runtime[R]) {
    private val stateVar = Var(initial)

    val qqq = zio.Unsafe.unsafe(implicit u =>
      r.unsafe
        .run(
          for {
            q <- Queue.unbounded[E]
            _ <- ZStream
                   .fromQueue(q)
                   .mapZIO { f =>
                     for {
                       _                 <- Console.printLine(s"[${java.time.Instant.now()}] Processing event: $f")
                       currentState      <- ZIO.succeed(stateVar.now())
                       (newState, effect) = reducer(currentState, f) match
                                              case ReducerResult.Pure(s)       => (s, ZIO.none)
                                              case ReducerResult.Eff(effect)   => (currentState, effect)
                                              case ReducerResult.Impure(s, ef) => (s, ef)
                       _                 <- effect
                                              .flatMap(_ match
                                                case Some(value) => q.offer(value)
                                                case None        => ZIO.unit
                                              )
                                              .forkDaemon
                     } yield newState
                   }
                   .map(newState => stateVar.set(newState))
                   .runDrain
                   .forkDaemon
          } yield q
        )
        .getOrThrowFiberFailure()
    )

    def updateSync(event: E)(using r: zio.Runtime[R]): Unit =
      zio.Unsafe.unsafe(implicit u =>
        r.unsafe.runToFuture(
          qqq.offer(event).forkDaemon
        )
      )

    def update(event: E)(using r: zio.Runtime[R]): Unit =
      zio.Unsafe.unsafe(implicit u =>
        r.unsafe.runToFuture(
          qqq.offer(event).forkDaemon
        )
      )

    def signal: Signal[S] = stateVar.signal

  }

  enum ReducerResult[S, E] {
    case Pure(state: S)
    case Eff(effect: ZIO[Any, Throwable, Option[E]])
    case Impure(state: S, effect: ZIO[Any, Throwable, Option[E]])
  }

  final class StateManager[S](initial: S) {
    def apply[E, R](reducer: (S, E) => ReducerResult[S, E])(using r: zio.Runtime[R]): StateManagerImpl[R, S, E] =
      new StateManagerImpl[R, S, E](initial, reducer)
  }

  def stateManager[S](initial: S)[E](reducer: (S, E) => ReducerResult[S, E])[R](using r: zio.Runtime[R]) = {
    new StateManagerImpl[R, S, E](initial, reducer)
  }
}
