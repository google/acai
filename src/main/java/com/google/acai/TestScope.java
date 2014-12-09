package com.google.acai;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * Scope for bindings annotated with {@link TestScoped}.
 */
class TestScope implements Scope {
  private final ThreadLocal<Map<Key<?>, Object>> values = new ThreadLocal<>();

  void enter() {
    checkState(values.get() == null, "TestScope is already in progress.");
    values.set(new HashMap<Key<?>, Object>());
  }

  void exit() {
    checkState(values.get() != null, "TestScope not in progress");
    values.remove();
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
