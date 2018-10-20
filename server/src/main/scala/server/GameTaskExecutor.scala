package server

import org.ode4j.ode.threading.task._
import scala.concurrent.ExecutionContext

final class GameTaskExecutor(executionContext: ExecutionContext, threadCount: Int = Int.MaxValue) extends AbstractTaskExecutor {

  override def submit(task: Task): Unit = 
    executionContext.execute(task)

  override def getThreadCount: Int = threadCount

  override def flush(): Unit = ()
}
