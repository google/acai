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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Scope;

/**
 * Annotation which can be applied to bindings to get one instance per test case.
 *
 * <p>Note that as {@link TestingService} instances are shared between multiple tests if you wish to
 * use a {@code @TestScoped} binding within a {@code TestingService} you should inject a {@code
 * Provider} and call {@link com.google.inject.Provider#get} within the {@code @Before} or
 * {@code @After} method as appropriate.
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Scope
public @interface TestScoped {}
