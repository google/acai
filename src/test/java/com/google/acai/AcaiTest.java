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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.auto.value.AutoValue;
import com.google.common.testing.TearDownAccepter;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.Provides;
import java.lang.annotation.Retention;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AcaiTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Mock private Statement statement;
  @Mock private FrameworkMethod frameworkMethod;

  @Before
  public void cleanup() {
    Service.methodCalls = MethodCalls.create();
    DependentService.methodCalls = MethodCalls.create();
    ServiceWithFailingBeforeTest.shouldFail = true;
    ModuleUsingTearDownAccepter.tearDownCount = 0;
    Acai.testOnlyResetEnvironments();
  }

  @Test
  public void serviceMethodsRun() throws Throwable {
    Acai acai = new Acai(TestModule.class);

    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create());

    acai.apply(
            new Statement() {
              @Override
              public void evaluate() throws Throwable {
                assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 1, 0));
              }
            },
            frameworkMethod,
            new Object())
        .evaluate();

    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 1, 1));
  }

  @Test
  public void beforeClassRunOnceOnly() throws Throwable {
    new Acai(TestModule.class).apply(statement, frameworkMethod, new Object()).evaluate();
    new Acai(TestModule.class).apply(statement, frameworkMethod, new Object()).evaluate();

    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 2, 2));
  }

  @Test
  public void testCaseIsInjected() throws Throwable {
    ExampleTest test = new ExampleTest();

    new Acai(TestModule.class).apply(statement, frameworkMethod, test).evaluate();

    assertThat(test.injected).isEqualTo("injected-value");
  }

  @Test
  public void failingTestInjectionDoesNotAffectSubsequentTests() throws Throwable {
    Acai acai = new Acai(TestModule.class);
    try {
      acai.apply(statement, frameworkMethod, new TestWithUnsatisfiedBinding()).evaluate();
      assertWithMessage("Expected ConfigurationException to be thrown.").fail();
    } catch (ConfigurationException e) {
      // Expected: TestWithUnsatisfiedBinding requires binding not satisfied by TestModule.
    }

    acai.apply(statement, frameworkMethod, new ExampleTest()).evaluate();

    verify(statement, times(1)).evaluate();
  }

  @Test
  public void failingBeforeTestMethodDoesNotAffectSubsequentTests() throws Throwable {
    Acai acai = new Acai(FailingBeforeTestModule.class);
    try {
      acai.apply(statement, frameworkMethod, new ExampleTest()).evaluate();
      assertWithMessage("Expected TestException to be thrown.").fail();
    } catch (TestException e) {
      // Expected: ServiceWithFailingBeforeTest throws TestException in @Before.
    }

    ServiceWithFailingBeforeTest.shouldFail = false;
    acai.apply(statement, frameworkMethod, new ExampleTest()).evaluate();
    verify(statement, times(1)).evaluate();
  }

  @Test
  public void testsAreInjectedAfterRunningBeforeClass() throws Throwable {
    ExampleTest test = new ExampleTest();

    new Acai(TestModule.class).apply(statement, frameworkMethod, test).evaluate();

    // Check injection happened after Service.initialized was set to true
    // by @BeforeClass method.
    assertThat(test.initialized).isEqualTo(true);
  }

  @Test
  public void servicesRunInDependencyOrder() throws Throwable {
    new Acai(DependentServiceModule.class)
        .apply(statement, frameworkMethod, new Object())
        .evaluate();

    // Sanity check the services ran, the ordering assertions are
    // within DependentService itself.
    assertThat(DependentService.methodCalls).isEqualTo(MethodCalls.create(1, 1, 1));
    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 1, 1));
  }

  @Test
  public void usefulErrorMessageWhenModuleMissingZeroArgConstructor() throws Throwable {
    thrown.expectMessage("does not have zero argument constructor");
    new Acai(ModuleWithoutZeroArgumentConstructor.class)
        .apply(statement, frameworkMethod, new Object())
        .evaluate();
  }

  @Test
  public void rethrowsExceptionThrownByModuleConstructor() throws Throwable {
    thrown.expect(TestException.class);
    new Acai(ModuleWithThrowingConstructor.class)
        .apply(statement, frameworkMethod, new Object())
        .evaluate();
  }

  @Test
  public void tearDownAcceptorRunsTearDownAfterTest() throws Throwable {
    TearDownAccepterTest test = new TearDownAccepterTest();
    Acai acai = new Acai(ModuleUsingTearDownAccepter.class);

    acai.apply(
            new Statement() {
              @Override
              public void evaluate() {
                assertThat(ModuleUsingTearDownAccepter.tearDownCount).isEqualTo(0);
              }
            },
            frameworkMethod,
            test)
        .evaluate();

    assertThat(ModuleUsingTearDownAccepter.tearDownCount).isEqualTo(1);
  }

  @Test
  public void tearDownAcceptorRunsTearDownForEachTest() throws Throwable {
    TearDownAccepterTest test = new TearDownAccepterTest();
    Acai acai = new Acai(ModuleUsingTearDownAccepter.class);

    acai.apply(statement, frameworkMethod, test).evaluate();
    acai.apply(statement, frameworkMethod, test).evaluate();

    assertThat(ModuleUsingTearDownAccepter.tearDownCount).isEqualTo(2);
  }

  private static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bindConstant().annotatedWith(TestBindingAnnotation.class).to("injected-value");
      bind(Service.class).in(Singleton.class);
      install(
          new TestingServiceModule() {
            @Override
            protected void configureTestingServices() {
              bindTestingService(Service.class);
            }
          });
    }

    @Provides
    @IsInitialized
    boolean provideIsInitialized(Service service) {
      return service.initialized;
    }
  }

  private static class DependentServiceModule extends AbstractModule {
    @Override
    protected void configure() {
      install(
          new TestingServiceModule() {
            @Override
            protected void configureTestingServices() {
              bindTestingService(DependentService.class);
              bindTestingService(Service.class);
            }
          });
    }

    @Provides
    @IsInitialized
    boolean provideIsInitialized(Service service) {
      return service.initialized;
    }
  }

  private static class Service implements TestingService {
    static MethodCalls methodCalls = MethodCalls.create();
    boolean initialized = false;

    @BeforeClass
    private void beforeClass() {
      initialized = true;
      methodCalls = methodCalls.incrementBeforeClass();
    }

    @Before
    private void beforeTest() {
      methodCalls = methodCalls.incrementBeforeTest();
    }

    @After
    private void afterTest() {
      methodCalls = methodCalls.incrementAfterTest();
    }
  }

  @DependsOn(Service.class)
  private static class DependentService implements TestingService {
    static MethodCalls methodCalls = MethodCalls.create();

    @BeforeClass
    private void beforeClass() {
      assertWithMessage("DependentService should be run after Service")
          .that(Service.methodCalls.beforeClass())
          .isEqualTo(methodCalls.beforeClass() + 1);
      methodCalls = methodCalls.incrementBeforeClass();
    }

    @Before
    private void beforeTest() {
      assertWithMessage("DependentService should be run after Service")
          .that(Service.methodCalls.beforeTest())
          .isEqualTo(methodCalls.beforeTest() + 1);
      methodCalls = methodCalls.incrementBeforeTest();
    }

    @After
    private void afterTest() {
      methodCalls = methodCalls.incrementAfterTest();
      assertWithMessage("Service should be cleaned up after DependentService")
          .that(Service.methodCalls.afterTest())
          .isEqualTo(methodCalls.afterTest() - 1);
    }
  }

  @AutoValue
  abstract static class MethodCalls {
    abstract int beforeClass();

    abstract int beforeTest();

    abstract int afterTest();

    static MethodCalls create() {
      return new AutoValue_AcaiTest_MethodCalls(0, 0, 0);
    }

    static MethodCalls create(int beforeClass, int beforeTest, int afterTest) {
      return new AutoValue_AcaiTest_MethodCalls(beforeClass, beforeTest, afterTest);
    }

    MethodCalls incrementBeforeClass() {
      return create(beforeClass() + 1, beforeTest(), afterTest());
    }

    MethodCalls incrementBeforeTest() {
      return create(beforeClass(), beforeTest() + 1, afterTest());
    }

    MethodCalls incrementAfterTest() {
      return create(beforeClass(), beforeTest(), afterTest() + 1);
    }
  }

  private static class TestWithUnsatisfiedBinding {
    @Inject @TestBindingAnnotation ExampleTest unsatisfiedBinding;
  }

  private static class ExampleTest {
    @Inject @TestBindingAnnotation String injected;
    @Inject @IsInitialized boolean initialized;
  }

  private static class TestException extends RuntimeException {}

  private static class ServiceWithFailingBeforeTest implements TestingService {
    static boolean shouldFail = true;

    @Before
    void failingBeforeTest() {
      if (shouldFail) {
        throw new TestException();
      }
    }
  }

  private static class FailingBeforeTestModule extends TestingServiceModule {
    @Override
    protected void configureTestingServices() {
      install(new TestModule());
      bindTestingService(ServiceWithFailingBeforeTest.class);
    }
  }

  private static class ModuleWithoutZeroArgumentConstructor extends AbstractModule {
    ModuleWithoutZeroArgumentConstructor(String argument) {
      // No-op.
    }

    @Override
    protected void configure() {
      // No-op.
    }
  }

  private static class ModuleWithThrowingConstructor extends AbstractModule {
    ModuleWithThrowingConstructor() {
      throw new TestException();
    }

    @Override
    protected void configure() {
      // No-op.
    }
  }

  static class ModuleUsingTearDownAccepter extends AbstractModule {
    static int tearDownCount = 0;

    @Override
    protected void configure() {
      // No-op.
    }

    @Provides
    @TestBindingAnnotation
    String provideValueAndAddTearDown(TearDownAccepter tearDownAccepter) {
      tearDownAccepter.addTearDown(() -> tearDownCount++);
      return "value";
    }
  }

  private static class TearDownAccepterTest {
    @Inject @TestBindingAnnotation String injected;
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  private @interface TestBindingAnnotation {}

  @Retention(RUNTIME)
  @BindingAnnotation
  private @interface IsInitialized {}
}
