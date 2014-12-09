package com.google.acai;

import com.google.inject.ScopeAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation which can be applied to bindings to get one instance per test case.
 *
 * <p>Note that as {@link TestingService} instances are shared between multiple
 * tests if you wish to use a {@code @TestScoped} binding within a {@code TestingService}
 * you should inject a {@code Provider} and call {@link com.google.inject.Provider#get}
 * within the {@code @BeforeTest} or {@code @AfterTest} method as appropriate.
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@ScopeAnnotation
public @interface TestScoped {
}
