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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

final class SupplierIterator<T> implements Iterator<T> {

  private T nextElement = null;
  private final Supplier<T> supplier;

  private SupplierIterator(final Supplier<T> supplier) {
    this.supplier = supplier;
  }

  static <T> SupplierIterator<T> supplyUntilNull(final Supplier<T> supplier) {
    return new SupplierIterator<>(supplier);
  }

  @Override
  public boolean hasNext() {
    if (nextElement != null) {
      return true;
    } else {
      nextElement = supplier.get();
      return (nextElement != null);
    }
  }

  @Override
  public T next() {
    if (nextElement != null || hasNext()) {
      T element = nextElement;
      nextElement = null;
      return element;
    } else {
      throw new NoSuchElementException();
    }
  }
}
