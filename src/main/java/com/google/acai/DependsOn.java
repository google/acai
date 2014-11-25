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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotates a {@link com.google.acai.TestingService} as depending on another
 * testing service.
 *
 * <p>A testing service annotated as depending on another its methods will be executed
 * after the methods of the service it depends upon when executing
 * {@code BeforeSuite} or {@code BeforeTest} methods. When running {@code AfterTest}
 * methods the dependent service's methods will be run before those of the
 * service it depends upon (i.e., the setup ordering is the opposite of the setup
 * ordering).
 *
 * <p>This is useful in the case where your test environment starts multiple
 * services which depend on each other being available in order to start.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface DependsOn {
  Class<? extends TestingService>[] value();
}
