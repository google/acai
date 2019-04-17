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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 * Internal Acai class which manages the execution of annotated methods on a {@link TestingService}.
 */
class TestingServiceManager {
  private final TestingService testingService;

  TestingServiceManager(TestingService testingService) {
    this.testingService = checkNotNull(testingService);
  }

  /** Runs all methods annotated with {@code BeforeClass}. */
  void beforeClass() {
    invokeMethodsAnnotated(BeforeClass.class);
  }

  /** Runs all methods annotated with {@code Before}. */
  void beforeTest() {
    invokeMethodsAnnotated(Before.class);
  }

  /** Runs all methods annotated with {@code After}. */
  void afterTest() {
    invokeMethodsAnnotated(After.class);
  }

  private void invokeMethodsAnnotated(Class<? extends Annotation> annotation) {
    for (Method method : methodsAnnotated(annotation)) {
      try {
        method.setAccessible(true);
        method.invoke(testingService);
      } catch (InvocationTargetException e) {
        if (e.getCause() != null) {
          Throwables.throwIfUnchecked(e.getCause());
          throw new RuntimeException(e.getCause());
        }
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private ImmutableList<Method> methodsAnnotated(Class<? extends Annotation> annotation) {
    ImmutableList.Builder<Method> methods = ImmutableList.builder();
    for (Class<?> clazz = testingService.getClass();
        TestingService.class.isAssignableFrom(clazz);
        clazz = clazz.getSuperclass()) {
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.isAnnotationPresent(annotation) && isUsable(method)) {
          methods.add(method);
        }
      }
    }
    return methods.build();
  }

  /** Returns true if a method is suitable to be invoked by Acai. */
  private static boolean isUsable(Method method) {
    return !Modifier.isStatic(method.getModifiers()) && method.getParameterTypes().length == 0;
  }
}
