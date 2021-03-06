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

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Wrapper for a spawned process.
 */
public class Result implements Communicable<Result> {

  private final static Consumer<String> NO_OP_CONUMER = ignored -> {
  };
  private final static Supplier<String> NULL_SUPPLIER = () -> null;

  private final AtomicBoolean inAttached = new AtomicBoolean(false);
  private final AtomicBoolean outAttached = new AtomicBoolean(false);
  private final AtomicBoolean errAttached = new AtomicBoolean(false);

  private final Process p;

  private CompletionStage<Void> inRunner;
  private CompletionStage<Void> outRunner;
  private CompletionStage<Void> errRunner;
  private final Function<Supplier<String>, CompletionStage<Void>> in;
  private final Function<Consumer<String>, CompletionStage<Void>> out;
  private final Function<Consumer<String>, CompletionStage<Void>> err;
  private final Function<Runnable, CompletionStage<Void>> runner;

  /**
   * Constructor
   */
  static Result of(final Process p,
                   final Function<Supplier<String>, CompletionStage<Void>> in,
                   final Function<Consumer<String>, CompletionStage<Void>> out,
                   final Function<Consumer<String>, CompletionStage<Void>> err,
                   final Function<Runnable, CompletionStage<Void>> runner) {
    return new Result(p, in, out, err, runner);
  }

  private Result(final Process p,
                 final Function<Supplier<String>, CompletionStage<Void>> in,
                 final Function<Consumer<String>, CompletionStage<Void>> out,
                 final Function<Consumer<String>, CompletionStage<Void>> err,
                 final Function<Runnable, CompletionStage<Void>> runner) {

    this.p = p;
    this.out = out;
    this.err = err;
    this.in = in;
    this.runner = runner;
  }

  /**
   * Waits for process to finish.
   *
   * @return exit code for process
   */
  public CompletionStage<Integer> waitFor() {
    final CompletableFuture<Integer> future = new CompletableFuture<>();
    final CompletableFuture<Void> runners = collectRunners();
    runner.apply(() -> {
      try {
        future.complete(p.waitFor());
      } catch (InterruptedException e) {
        future.completeExceptionally(e);
      } finally {
        runners.join();
      }
    });
    return future;
  }

  /**
   * Wait for process finish for duration
   *
   * @param duration time to wait for
   * @return true or false depending on if process terminated by itself or not.
   */
  public CompletionStage<Boolean> waitFor(final Duration duration) {
    final CompletableFuture<Boolean> future = new CompletableFuture<>();
    final CompletableFuture<Void> runners = collectRunners();
    runner.apply(() -> {
      try {
        future.complete(p.waitFor(duration.getSeconds(), TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        future.completeExceptionally(e);
      } finally {
        runners.join();
      }
    });
    return future;
  }

  private CompletableFuture<Void> collectRunners() {
    if (isNull(inRunner)) {
      in(NULL_SUPPLIER);
    }

    if (isNull(outRunner)) {
      out(NO_OP_CONUMER);
    }

    if (isNull(errRunner)) {
      err(NO_OP_CONUMER);
    }

    return allOf(
        Stream.of(inRunner, outRunner, errRunner)
            .map(CompletionStage::toCompletableFuture)
            .toArray(CompletableFuture[]::new)
    );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Result out(final Consumer<String> consumer) {
    requireNonNull(consumer);
    if (outAttached.compareAndSet(false, true)) {
      outRunner = out.apply(consumer);
      return this;
    } else {
      throw new IllegalStateException("StdOut consumer already attached");
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Result err(final Consumer<String> consumer) {
    requireNonNull(consumer);
    if (errAttached.compareAndSet(false, true)) {
      errRunner = err.apply(consumer);
      return this;
    } else {
      throw new IllegalStateException("StdErr consumer already attached");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Result in(final Supplier<String> supplier) {
    requireNonNull(supplier);
    if (inAttached.compareAndSet(false, true)) {
      inRunner = in.apply(supplier);
      return this;
    } else {
      throw new IllegalStateException("StdIn producer already attached");
    }
  }

  /**
   * Underlying access to the process wrapped in a completionstage.
   * Note that all operations done on the process are not asynchronous unless you yourself
   * wrap it in async operations
   */
  public CompletionStage<Process> process() {
    return completedFuture(p);
  }

  /**
   * Async destroy process.
   */
  public CompletionStage<Void> destroy() {
    return runner.apply(p::destroy);
  }
}