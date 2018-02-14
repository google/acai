/*
 * Copyright 2018 Google Inc. All rights reserved.
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

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.lang.annotation.Annotation;

/**
 * Module which can be installed to provide some limited compatibility with Guiceberry.
 *
 * <p>Currently installing this module will allow you to reuse modules designed for Guiceberry which
 * make use of its @TestScoped annotation.
 */
public class GuiceberryCompatibilityModule extends AbstractModule {
  private static final String GUICEBRRY_TEST_SCOPED_ANNOTATION = "com.google.guiceberry.TestScoped";

  @Override
  protected void configure() {
    Provider<TestScope> scopeProvider = getProvider(Key.get(TestScope.class, AcaiInternal.class));
    Scope scope =
        new Scope() {
          @Override
          public <T> Provider<T> scope(Key<T> key, Provider<T> provider) {
            return scopeProvider.get().scope(key, provider);
          }
        };

    try {
      bindScope(
          Class.forName(GUICEBRRY_TEST_SCOPED_ANNOTATION).asSubclass(Annotation.class), scope);
    } catch (ClassNotFoundException | ClassCastException e) {
      throw new RuntimeException(
          "GuiceberryCompatibilityModule installed but Guiceberry is not available.", e);
    }
  }
}
