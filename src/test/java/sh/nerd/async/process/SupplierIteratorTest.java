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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static sh.nerd.async.process.SupplierIterator.supplyUntilNull;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class SupplierIteratorTest {

  @Test
  void hasNext() {
    final SupplierIterator<Object> si = supplyUntilNull(() -> null);
    assertFalse(si::hasNext);
  }

  @Test
  void next() {
    final SupplierIterator<Object> si = supplyUntilNull(() -> null);
    assertThrows(NoSuchElementException.class, si::next);
  }

}