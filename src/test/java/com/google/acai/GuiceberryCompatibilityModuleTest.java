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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.guiceberry.GuiceBerryEnvMain;
import com.google.guiceberry.GuiceBerryModule;
import com.google.guiceberry.TestWrapper;
import com.google.inject.AbstractModule;
import com.google.inject.testing.guiceberry.TestScopeListener;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GuiceberryCompatibilityModuleTest {
  @Mock private Statement statement;
  @Mock private FrameworkMethod frameworkMethod;

  @Before
  public void setUp() {
    ScopeListener.enteringScopeCount = 0;
    ScopeListener.exitingScopeCount = 0;
    Wrapper.runBeforeCount = 0;
  }

  @Test
  public void sameInstanceInjectedWithinTest() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    new Acai(GuiceberryCompatibilityModule.class)
        .apply(statement, frameworkMethod, test)
        .evaluate();

    assertThat(test.instanceOne).isNotNull();
    assertThat(test.instanceTwo).isSameAs(test.instanceOne);
  }

  @Test
  public void differentInstanceInjectedAcrossTests() throws Throwable {
    FakeTestClass testOne = new FakeTestClass();
    FakeTestClass testTwo = new FakeTestClass();

    new Acai(GuiceberryCompatibilityModule.class)
        .apply(statement, frameworkMethod, testOne)
        .evaluate();
    new Acai(GuiceberryCompatibilityModule.class)
        .apply(statement, frameworkMethod, testTwo)
        .evaluate();

    assertThat(testOne.instanceOne).isNotSameAs(testTwo.instanceOne);
  }

  @Test
  public void envMainIsRunOnce() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    new Acai(GuiceberryTestModule.class).apply(statement, frameworkMethod, test).evaluate();
    new Acai(GuiceberryTestModule.class).apply(statement, frameworkMethod, test).evaluate();

    assertThat(EnvMain.runCount).isEqualTo(1);
  }

  @Test
  public void testScopeListenerCalled() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    assertThat(ScopeListener.enteringScopeCount).isEqualTo(0);
    assertThat(ScopeListener.exitingScopeCount).isEqualTo(0);

    new Acai(GuiceberryTestModule.class)
        .apply(
            new Statement() {
              @Override
              public void evaluate() {
                assertThat(ScopeListener.enteringScopeCount).isEqualTo(1);
                assertThat(ScopeListener.exitingScopeCount).isEqualTo(0);
              }
            },
            frameworkMethod,
            test)
        .evaluate();

    assertThat(ScopeListener.enteringScopeCount).isEqualTo(1);
    assertThat(ScopeListener.exitingScopeCount).isEqualTo(1);
  }

  @Test
  public void testWrapperCalled() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    assertThat(Wrapper.runBeforeCount).isEqualTo(0);

    new Acai(GuiceberryTestModule.class)
        .apply(
            new Statement() {
              @Override
              public void evaluate() {
                assertThat(Wrapper.runBeforeCount).isEqualTo(1);
              }
            },
            frameworkMethod,
            test)
        .evaluate();

    assertThat(Wrapper.runBeforeCount).isEqualTo(1);
  }

  @Test
  public void testWrapperExceptionPropagated() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    try {
      new Acai(ThrowingWrapperModule.class)
          .apply(
              new Statement() {
                @Override
                public void evaluate() {
                  assertWithMessage("Test should not run when TestWrapper throws").fail();
                }
              },
              frameworkMethod,
              test)
          .evaluate();
      assertWithMessage("Exception from TestWrapper should be propagated").fail();
    } catch (TestException expected) {
    }
  }

  private static class FakeTestClass {
    @Inject MyTestScopedClass instanceOne;
    @Inject MyTestScopedClass instanceTwo;
  }

  @com.google.guiceberry.TestScoped
  private static class MyTestScopedClass {}

  static class GuiceberryTestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new GuiceBerryModule());
      install(new GuiceberryCompatibilityModule());
      bind(GuiceBerryEnvMain.class).to(EnvMain.class);
      bind(TestScopeListener.class).to(ScopeListener.class);
      bind(TestWrapper.class).to(Wrapper.class);
    }
  }

  private static class EnvMain implements GuiceBerryEnvMain {
    static int runCount = 0;

    @Override
    public void run() {
      runCount++;
    }
  }

  private static class ScopeListener implements TestScopeListener {
    static int enteringScopeCount = 0;
    static int exitingScopeCount = 0;

    @Override
    public void enteringScope() {
      enteringScopeCount++;
    }

    @Override
    public void exitingScope() {
      exitingScopeCount++;
    }
  }

  private static class Wrapper implements TestWrapper {
    static int runBeforeCount = 0;

    @Override
    public void toRunBeforeTest() {
      runBeforeCount++;
    }
  }

  static class ThrowingWrapperModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new GuiceBerryModule());
      bind(TestWrapper.class).to(ThrowingWrapper.class);
    }
  }

  private static class ThrowingWrapper implements TestWrapper {
    @Override
    public void toRunBeforeTest() throws TestException {
      throw new TestException();
    }
  }

  private static class TestException extends RuntimeException {}
}
