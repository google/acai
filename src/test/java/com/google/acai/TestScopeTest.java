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

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.inject.Inject;

import static com.google.common.truth.Truth.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class TestScopeTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Mock private Statement statement;
  @Mock private FrameworkMethod frameworkMethod;

  @Test
  public void sameInstanceInjectedWithinTest() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    new Acai(EmptyTestModule.class).apply(statement, frameworkMethod, test).evaluate();

    assertThat(test.instanceOne).isNotNull();
    assertThat(test.instanceTwo).isSameAs(test.instanceOne);
  }

  @Test
  public void differentInstanceInjectedAcrossTests() throws Throwable {
    FakeTestClass testOne = new FakeTestClass();
    FakeTestClass testTwo = new FakeTestClass();

    new Acai(EmptyTestModule.class).apply(statement, frameworkMethod, testOne).evaluate();
    new Acai(EmptyTestModule.class).apply(statement, frameworkMethod, testTwo).evaluate();

    assertThat(testOne.instanceOne).isNotSameAs(testTwo.instanceOne);
  }

  @Test
  public void servicesInstantiatedOutsideTestScope() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    thrown.expect(ProvisionException.class);
    thrown.expectMessage("@TestScoped binding outside test");
    new Acai(InvalidTestModule.class).apply(statement, frameworkMethod, test).evaluate();
  }

  @Test
  public void canCallProviderInServiceSetupAndTeardown() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    new Acai(ValidTestModule.class).apply(statement, frameworkMethod, test).evaluate();

    assertThat(ValidTestingService.valueFromBeforeTest).isNotNull();
    assertThat(ValidTestingService.valueFromAfterTest)
        .isSameAs(ValidTestingService.valueFromBeforeTest);
  }

  private static class EmptyTestModule extends AbstractModule {
    @Override protected void configure() {
      // No-op.
    }
  }

  private static class InvalidTestModule extends TestingServiceModule {
    @Override protected void configureTestingServices() {
      bindTestingService(InvalidTestingService.class);
    }
  }

  private static class ValidTestModule extends TestingServiceModule {
    @Override protected void configureTestingServices() {
      bindTestingService(ValidTestingService.class);
    }
  }

  private static class FakeTestClass {
    @Inject MyTestScopedClass instanceOne;
    @Inject MyTestScopedClass instanceTwo;
  }

  @TestScoped
  private static class MyTestScopedClass {
  }

  private static class InvalidTestingService implements TestingService {
    @Inject MyTestScopedClass testScoped;
  }

  private static class ValidTestingService implements TestingService {
    @Inject Provider<MyTestScopedClass> testScoped;
    static MyTestScopedClass valueFromBeforeTest;
    static MyTestScopedClass valueFromAfterTest;

    @BeforeTest void beforeTest() {
      valueFromBeforeTest = testScoped.get();
    }

    @AfterTest void afterTest() {
      valueFromAfterTest = testScoped.get();
    }
  }
}
