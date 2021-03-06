package org.crsh.shell.concurrent;

import org.crsh.AbstractTestCase;
import org.crsh.BaseProcess;
import org.crsh.BaseProcessContext;
import org.crsh.BaseProcessFactory;
import org.crsh.BaseShell;
import org.crsh.CommandQueue;
import org.crsh.shell.Shell;
import org.crsh.shell.ShellResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public class CancellationTestCase extends AbstractTestCase {


  public void testEvaluating() throws Exception {
    final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
    final AtomicInteger cancelCount = new AtomicInteger(0);
    final CountDownLatch latch1 = new CountDownLatch(1);
    final CountDownLatch latch2 = new CountDownLatch(1);

    //
    BaseProcessFactory factory = new BaseProcessFactory() {
      @Override
      public BaseProcess create(String request) {
        return new BaseProcess(request) {
          @Override
          protected ShellResponse execute(String request) {
            latch1.countDown();
            try {
              latch2.await();
            } catch (InterruptedException e) {
              failure.set(e);
            }
            return ShellResponse.ok();
          }
          @Override
          public void cancel() {
            cancelCount.getAndIncrement();
          }
        };
      }
    };

    //
    Shell shell = new BaseShell(factory);
    CommandQueue commands = new CommandQueue();
    AsyncShell  asyncShell = new AsyncShell(commands, shell);

    //
    BaseProcessContext ctx = BaseProcessContext.create(asyncShell, "foo").execute();
    assertEquals(Status.QUEUED, ((AsyncProcess)ctx.getProcess()).getStatus());
    assertEquals(0, cancelCount.get());
    assertEquals(1, commands.getSize());

    // Execute the command
    // And wait until the other thread is waiting
    Future<?> future = commands.executeAsync();
    latch1.await();
    assertEquals(Status.EVALUATING, ((AsyncProcess)ctx.getProcess()).getStatus());
    assertEquals(0, cancelCount.get());

    //
    ctx.getProcess().cancel();
    assertEquals(Status.CANCELED, ((AsyncProcess)ctx.getProcess()).getStatus());
    assertEquals(1, cancelCount.get());

    //
    ctx.getProcess().cancel();
    assertEquals(Status.CANCELED, ((AsyncProcess)ctx.getProcess()).getStatus());
    assertEquals(1, cancelCount.get());

    // Wait until it's done
    latch2.countDown();
    future.get();

    // Test we received a cancelled response even though we provided an OK result
    assertEquals(ShellResponse.Cancelled.class, ctx.getResponse().getClass());
    assertEquals(Status.TERMINATED, ((AsyncProcess)ctx.getProcess()).getStatus());
    assertEquals(1, cancelCount.get());

    //
    safeFail(failure.get());
  }

  public void testQueued() throws Exception {
    final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

    //
    BaseProcessFactory factory = new BaseProcessFactory() {
      @Override
      public BaseProcess create(String request) {
        return new BaseProcess(request) {
          @Override
          protected ShellResponse execute(String request) {
            failure.set(failure("Was not exepecting request"));
            return ShellResponse.ok();
          }
          @Override
          public void cancel() {
            failure.set(failure("Was not exepecting cancel"));
          }
        };
      }
    };

    //
    Shell shell = new BaseShell(factory);
    CommandQueue commands = new CommandQueue();
    AsyncShell  asyncShell = new AsyncShell(commands, shell);

    //
    BaseProcessContext ctx = BaseProcessContext.create(asyncShell, "foo").execute();
    assertEquals(Status.QUEUED, ((AsyncProcess)ctx.getProcess()).getStatus());
    assertEquals(1, commands.getSize());

    //
    ctx.getProcess().cancel();
    assertEquals(Status.CANCELED, ((AsyncProcess)ctx.getProcess()).getStatus());

    // Execute the command
    Future<?> future = commands.executeAsync();
    future.get();

    // Test we get terminated status and the callback was done
    assertEquals(Status.TERMINATED, ((AsyncProcess)ctx.getProcess()).getStatus());
    assertEquals(ShellResponse.Cancelled.class, ctx.getResponse().getClass());
    safeFail(failure.get());
  }
}
