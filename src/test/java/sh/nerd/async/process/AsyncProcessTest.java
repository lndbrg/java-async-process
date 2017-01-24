/*
 * -\-\-
 * Async Process
 * --
 * Copyright (C) 2017 Olle Lundberg
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package sh.nerd.async.process;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sh.nerd.async.process.AsyncProcess.Builder;
import static sh.nerd.async.process.AsyncProcess.cmd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AsyncProcessTest {

  @Test
  void start() throws IOException {
    assertAll("Consumers",
        () -> {
          final Map.Entry<CountDownLatch, Consumer<String>> latchCons = latchWithConsumer(1);
          cmd("echo", "foo").start().out(latchCons.getValue()).waitFor()
              .toCompletableFuture()
              .join();
          assertTrue(latchCons.getKey().getCount() == 0, "Stdout counsumer was never called");
        },
        () -> {
          final Map.Entry<CountDownLatch, Consumer<String>> latchCons = latchWithConsumer(2);
          cmd("echo", "-e", "foo\nbar").start().out(latchCons.getValue()).waitFor()
              .toCompletableFuture()
              .join();
          assertTrue(latchCons.getKey().getCount() == 0,
              "Stdout counsumer was not called twice (two lines)");
        }
    );
  }

  @Test
  void builderShouldThrowOnNull() {
    assertAll("Builder values can't be set to null",
        () -> assertThrows(NullPointerException.class,
            () -> new Builder().in(null)
        ),
        () -> assertThrows(NullPointerException.class,
            () -> new Builder().out(null)
        ),
        () -> assertThrows(NullPointerException.class,
            () -> new Builder().err(null)
        ),
        () -> assertThrows(NullPointerException.class,
            () -> new Builder().cmd(null)
        )
    );
  }

  @Test
  void resultShouldThrowOnMultipleConsumers() {
    Function<Consumer<String>, CompletionStage<Void>> std = cons -> completedFuture(null);
    Consumer<String> nothing = line -> {
    };
    assertAll("Result consumers can only be set once",
        () -> assertThrows(IllegalStateException.class,
            () -> Result.of(null, null, std, std, null).out(nothing).out(nothing)
        ),
        () -> assertThrows(IllegalStateException.class,
            () -> Result.of(null, null, std, std, null).err(nothing).err(nothing)
        )
    );
  }

  @Test
  void waitForShouldSetFutureToExceptionalOnInterruptedException() {
    final Process process = new Process() {

      @Override
      public OutputStream getOutputStream() {
        return null;
      }

      @Override
      public InputStream getInputStream() {
        return null;
      }

      @Override
      public InputStream getErrorStream() {
        return null;
      }

      @Override
      public int waitFor() throws InterruptedException {
        throw new InterruptedException("Woo");
      }

      @Override
      public int exitValue() {
        return 0;
      }

      @Override
      public void destroy() {

      }
    };
    final Function<Runnable, CompletionStage<Void>> runner = CompletableFuture::runAsync;
    final CompletionStage<Integer> p = Result.of(process, null, null, null, runner).waitFor();
    p.handle((result, exc) -> {
          assertNotNull(exc);
          assertTrue(exc instanceof InterruptedException);
          assertNull(result);
          return result;
        }
    ).toCompletableFuture().join();
  }

  @Test
  void waitForShouldCompleteFutureWhenDoneRunning() throws InterruptedException {
    final CountDownLatch blocker = new CountDownLatch(1);
    final Process process = new Process() {

      @Override
      public OutputStream getOutputStream() {
        return null;
      }

      @Override
      public InputStream getInputStream() {
        return null;
      }

      @Override
      public InputStream getErrorStream() {
        return null;
      }

      @Override
      public int waitFor() throws InterruptedException {
        blocker.await();
        return (int) blocker.getCount();
      }

      @Override
      public int exitValue() {
        return 0;
      }

      @Override
      public void destroy() {

      }
    };
    final Function<Runnable, CompletionStage<Void>> runner = CompletableFuture::runAsync;

    final CompletableFuture<Integer> p = Result.of(process, null, null, null, runner)
        .waitFor()
        .toCompletableFuture();
    assertFalse(p.isDone());
    blocker.countDown();
    p.thenAccept(exit -> assertEquals((int) exit, 0)).toCompletableFuture().join();
  }

  private Map.Entry<CountDownLatch, Consumer<String>> latchWithConsumer(final int count) {
    final CountDownLatch called = new CountDownLatch(count);
    final Consumer<String> outConsumer = ignore -> called.countDown();
    return new AbstractMap.SimpleEntry<>(called, outConsumer);
  }

  @Test
  void cmdShouldReturnBuilder() {
    assertTrue(cmd("a", "b") instanceof Builder);
  }


  @Test
  void shouldBeAbleToFeedDataOnStdIn() throws IOException, InterruptedException {
    final String something = "Something";
    final Result start =
        AsyncProcess.cmd("/bin/bash", "-c", "cat -")
            .start();

    final AtomicBoolean sent = new AtomicBoolean(false);
    final CountDownLatch called = new CountDownLatch(1);

    final Supplier<String> in = () -> {
      if (sent.compareAndSet(false, true)) {
        return something;
      }
      return null;
    };

    final Consumer<String> out = o -> {
      System.out.println("Got value from stdin: " + o);
      start.destroy().toCompletableFuture().join();
      called.countDown();
    };

    start.in(in).out(out);
    called.await();
    start.waitFor(Duration.ofSeconds(5))
        .thenAccept(Assertions::assertTrue)
        .toCompletableFuture()
        .join();
  }
}