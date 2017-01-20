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
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.runAsync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Class that creates an async process
 */
public class AsyncProcess {

  private final String[] command;
  private final Optional<Consumer<String>> outConsumer;
  private final Optional<Consumer<String>> errConsumer;
  private final Function<Runnable, CompletionStage<Void>> runner;

  /**
   * C'tor
   *
   * @param cmd command to run
   * @param out consumer for std out
   * @param err consumer for std err
   * @param exe the executor to run futures on
   */
  AsyncProcess(final String[] cmd,
               final Consumer<String> out,
               final Consumer<String> err,
               final Executor exe) {

    command = cmd;
    outConsumer = Optional.ofNullable(out);
    errConsumer = Optional.ofNullable(err);
    runner = isNull(exe) ? CompletableFuture::runAsync : runnable -> runAsync(runnable, exe);

  }

  /**
   * Starts the process
   */
  public Result start() throws IOException {
    final Process exec = Runtime.getRuntime().exec(command);
    final Function<Consumer<String>, CompletionStage<Void>> out = redirect(exec.getInputStream());
    final Function<Consumer<String>, CompletionStage<Void>> err = redirect(exec.getErrorStream());
    final Result result = Result.of(exec, out, err, runner);
    outConsumer.ifPresent(result::onOut);
    errConsumer.ifPresent(result::onErr);
    return result;
  }


  /**
   * Prepares to redirect an input stream to a consumer
   *
   * @return a function that when applied will start to consume the input stream.
   */
  private Function<Consumer<String>, CompletionStage<Void>> redirect(final InputStream stream) {
    return consumer -> {
      final CompletableFuture<Void> future = new CompletableFuture<>();
      runner.apply(
          () -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
              reader.lines().forEachOrdered(consumer);
              future.complete(null);
            } catch (IOException e) {
              future.completeExceptionally(e);
            }
          }
      );
      return future;
    };
  }

  /**
   * Creates a new Builder that can be used to start an AsyncProcess
   */
  public static Builder cmd(final String... cmd) {
    return new Builder().cmd(cmd);
  }

  /**
   * Run a command
   */
  public static Result run(final String... cmd) throws IOException {
    return new Builder().cmd(cmd).start();
  }

  /**
   * Run a command
   */
  public static Result run(final Consumer<String> out, final String... cmd) throws IOException {
    return new Builder().cmd(cmd).onOut(out).start();
  }

  /**
   * Run a command
   */
  public static Result run(final Consumer<String> out,
                           final Consumer<String> err,
                           final String... cmd) throws IOException {
    return new Builder().cmd(cmd).onOut(out).onErr(err).start();
  }

  /**
   * Wrapper for a spawned process.
   */
  static class Result {

    private AtomicBoolean outAttached = new AtomicBoolean(false);
    private AtomicBoolean errAttached = new AtomicBoolean(false);

    private final Process p;
    private final Function<Consumer<String>, CompletionStage<Void>> out;
    private final Function<Consumer<String>, CompletionStage<Void>> err;
    private final Function<Runnable, CompletionStage<Void>> runner;

    /**
     * Constructor
     */
    static Result of(final Process p,
                     final Function<Consumer<String>, CompletionStage<Void>> out,
                     final Function<Consumer<String>, CompletionStage<Void>> err,
                     final Function<Runnable, CompletionStage<Void>> runner) {
      return new Result(p, out, err, runner);
    }

    private Result(final Process p,
                   final Function<Consumer<String>, CompletionStage<Void>> out,
                   final Function<Consumer<String>, CompletionStage<Void>> err,
                   final Function<Runnable, CompletionStage<Void>> runner) {

      this.p = p;
      this.out = out;
      this.err = err;
      this.runner = runner;
    }

    /**
     * Waits for process to finish.
     *
     * @return exit code for process
     */
    public CompletionStage<Integer> waitFor() {
      final CompletableFuture<Integer> future = new CompletableFuture<>();
      runner.apply(() -> {
        try {
          future.complete(p.waitFor());
        } catch (InterruptedException e) {
          future.completeExceptionally(e);
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
      runner.apply(() -> {
        try {
          future.complete(p.waitFor(duration.getSeconds(), TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          future.completeExceptionally(e);
        }
      });
      return future;
    }

    /**
     * Sets the consumer to feed data to when standard out has data.
     *
     * @param consumer called for each line in output
     * @return a result
     */
    public Result onOut(final Consumer<String> consumer) {
      if (outAttached.compareAndSet(false, true)) {
        out.apply(requireNonNull(consumer));
        return this;
      } else {
        throw new IllegalStateException("StdOut consumer already attached");
      }
    }

    /**
     * Sets the consumer to feed data to when standard error has data.
     *
     * @param consumer called for each line in output
     * @return a result
     */
    public Result onErr(final Consumer<String> consumer) {
      if (errAttached.compareAndSet(false, true)) {
        err.apply(requireNonNull(consumer));
        return this;
      } else {
        throw new IllegalStateException("StdOut consumer already attached");
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


  /**
   * Builder for async process. Shouldn't be created by the user.
   * Rather use {@link AsyncProcess#cmd(String...)}
   */
  static class Builder {

    private Consumer<String> outConsumer;
    private Consumer<String> errConsumer;
    private String[] command;
    private Executor executor;

    /**
     * Sets the consumer to feed data to when standard out has data.
     *
     * @param consumer called for each line in output
     * @return a builder
     */
    public Builder onOut(final Consumer<String> consumer) {
      outConsumer = requireNonNull(consumer);
      return this;
    }

    /**
     * Sets the consumer to feed data to when standard error has data.
     *
     * @param consumer called for each line in output
     * @return a builder
     */
    public Builder onErr(final Consumer<String> consumer) {
      errConsumer = requireNonNull(consumer);
      return this;
    }

    /**
     * Set the command to execute.
     *
     * @param cmd command to execute
     */
    public Builder cmd(final String... cmd) {
      command = requireNonNull(cmd);
      return this;
    }

    /**
     * Set the executor to run on.
     *
     * @param exec executor to run on
     */
    public Builder executor(final Executor exec) {
      executor = requireNonNull(exec);
      return this;
    }


    /**
     * Kicks off the async process.
     *
     * @return a {@link Result} object.
     */
    public Result start() throws IOException {
      return new AsyncProcess(command, outConsumer, errConsumer, executor).start();
    }

  }

}
