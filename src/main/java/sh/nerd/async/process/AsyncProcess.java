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
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.stream.StreamSupport.stream;
import static sh.nerd.async.process.NamedThreadFactory.withPrefix;
import static sh.nerd.async.process.SupplierIterator.supplyUntilNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class that creates an async process
 */
public class AsyncProcess {

  private final String[] command;
  private final Optional<Supplier<String>> inSupplier;
  private final Optional<Consumer<String>> outConsumer;
  private final Optional<Consumer<String>> errConsumer;
  private final Map<String, String> environment;
  private final File path;
  private final Function<Runnable, CompletionStage<Void>> runner;
  private final static String THREAD_PREFIX = "async-process";

  /**
   * C'tor
   *
   * @param cmd command to run
   * @param in  supplier fro std in
   * @param out consumer for std out
   * @param err consumer for std err
   * @param exe the executor to run futures on
   */
  AsyncProcess(final String[] cmd,
               final Supplier<String> in,
               final Consumer<String> out,
               final Consumer<String> err,
               final Map<String, String> env,
               final File cwd,
               final Executor exe) {

    command = cmd;
    inSupplier = Optional.ofNullable(in);
    outConsumer = Optional.ofNullable(out);
    errConsumer = Optional.ofNullable(err);
    environment = env;
    path = cwd;
    final Executor executor = isNull(exe)
                              ? newCachedThreadPool(withPrefix(THREAD_PREFIX))
                              : exe;
    runner = runnable -> runAsync(runnable, executor);
  }

  /**
   * Starts the process
   */
  public Result start() throws IOException {
    final Process exec = Runtime.getRuntime().exec(command, env(environment), path);
    final Function<Supplier<String>, CompletionStage<Void>> in = redirect(exec.getOutputStream());
    final Function<Consumer<String>, CompletionStage<Void>> out = redirect(exec.getInputStream());
    final Function<Consumer<String>, CompletionStage<Void>> err = redirect(exec.getErrorStream());
    final Result result = Result.of(exec, in, out, err, runner);
    inSupplier.ifPresent(result::in);
    outConsumer.ifPresent(result::out);
    errConsumer.ifPresent(result::err);
    return result;
  }

  private String[] env(final Map<String, String> env) {
    return Optional.ofNullable(env)
        .map(e -> e.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .toArray(String[]::new)
        ).orElse(null);
  }

  private Function<Supplier<String>, CompletionStage<Void>> redirect(final OutputStream stream) {
    return supplier -> {
      final CompletableFuture<Void> future = new CompletableFuture<>();
      runner.apply(
          () -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream))) {
              produce(supplier, writer);
              future.complete(null);
            } catch (IOException e) {
              future.completeExceptionally(e);
            } catch (UncheckedIOException e) {
              future.completeExceptionally(e.getCause());
            }
          }
      );
      return future;
    };
  }

  private void produce(final Supplier<String> supplier, final BufferedWriter writer) {
    stream(spliteratorUnknownSize(supplyUntilNull(supplier), ORDERED | NONNULL), false)
        .forEachOrdered(
            string -> {
              try {
                writer.write(string);
                writer.newLine();
                writer.flush();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            }
        );
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
    return new Builder().cmd(cmd).out(out).start();
  }

  /**
   * Run a command
   */
  public static Result run(final Consumer<String> out,
                           final Consumer<String> err,
                           final String... cmd) throws IOException {
    return new Builder().cmd(cmd).out(out).err(err).start();
  }

  /**
   * Run a command
   */
  public static Result run(final Supplier<String> in,
                           final Consumer<String> out,
                           final Consumer<String> err,
                           final String... cmd) throws IOException {
    return new Builder().cmd(cmd).in(in).out(out).err(err).start();
  }


  /**
   * Builder for async process. Shouldn't be created by the user.
   * Rather use {@link AsyncProcess#cmd(String...)}
   */
  public static class Builder implements Communicable<Builder>, ProcessEnvironment<Builder> {

    private Supplier<String> inSupplier;
    private Consumer<String> outConsumer;
    private Consumer<String> errConsumer;
    private String[] command;
    private Executor executor;
    private Map<String, String> environment;
    private File cwd;

    /**
     * {@inheritDoc}
     */
    @Override
    public Builder out(final Consumer<String> consumer) {
      outConsumer = requireNonNull(consumer);
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Builder err(final Consumer<String> consumer) {
      errConsumer = requireNonNull(consumer);
      return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Builder in(final Supplier<String> supplier) {
      inSupplier = requireNonNull(supplier);
      return this;
    }

    /**
     * Set the command to execute.
     *
     * @param cmd the command to execute
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

    @Override
    public Builder env(final Map<String, String> env) {
      environment = requireNonNull(env);
      return this;
    }

    @Override
    public Builder cwd(final String path) {
      return cwd(new File(requireNonNull(path, "Path can't be null")));
    }

    @Override
    public Builder cwd(final File path) {
      cwd = requireNonNull(path, "Path can't be null");
      return this;
    }


    /**
     * Kicks off the async process.
     *
     * @return a {@link Result} object.
     */
    public Result start() throws IOException {
      return new AsyncProcess(
          command,
          inSupplier,
          outConsumer,
          errConsumer,
          environment,
          cwd,
          executor
      ).start();
    }

  }


}
