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

/**
 * Marker interface for testing services to be used with {@link Acai}.
 *
 * <p>Implementing classes should add zero argument methods annotated with one of {@link
 * org.junit.BeforeClass}, {@link org.junit.Before} or {@link org.junit.After} to have them run
 * by Acai at the relevant time during test suite execution.
 *
 * <p>Multiple {@code TestingService} instances can be bound in a single {@link
 * TestingServiceModule} and each {@code TestingService} may contain multiple or no methods with
 * each annotation type.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MyTestingService implements TestingService {
 *     {@literal @}BeforeClass
 *     public void startServer() {
 *       // Start your server here, runs once before all tests.
 *     }
 *
 *     {@literal @}After
 *     public void clearDatastore() {
 *       // Clean up test state here to isolate tests from one-another.
 *     }
 *   }
 *
 * </pre>
 */
public interface TestingService {}
