/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.acai;

import static com.google.common.base.Preconditions.checkState;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scope for bindings annotated with {@link TestScoped}.
 */
class TestScope implements Scope {
  private AtomicReference<ConcurrentMap<Key<?>, Object>> values = new AtomicReference<>();

  void enter() {
    checkState(
        values.getAndSet(new ConcurrentHashMap<Key<?>, Object>()) == null,
        "TestScope is already in progress.");
  }

  void exit() {
    checkState(values.getAndSet(null) != null, "TestScope not in progress");
  }

  @Override public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscopedProvider) {
    return new Provider<T>() {
      @Override public T get() {
        Map<Key<?>, Object> scopedObjects = getScopedObjectMap(key);

        @SuppressWarnings("unchecked")
        T scopedObject = (T) scopedObjects.get(key);
        if (scopedObject == null && !scopedObjects.containsKey(key)) {
          scopedObject = unscopedProvider.get();
          scopedObjects.put(key, scopedObject);
        }
        return scopedObject;
      }
    };
  }

  private <T> Map<Key<?>, Object> getScopedObjectMap(Key<T> key) {
    Map<Key<?>, Object> scopedObjects = values.get();
    if (scopedObjects == null) {
      throw new OutOfScopeException("Attempt to inject @TestScoped binding outside test: " + key);
    }
    return scopedObjects;
  }

  static class TestScopeModule extends AbstractModule {
    @Override protected void configure() {
      TestScope testScope = new TestScope();
      bind(TestScope.class).annotatedWith(AcaiInternal.class).toInstance(testScope);
      bindScope(TestScoped.class, testScope);
    }
  }
}
