package com.google.acai;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotates bindings which are internal to Acai.
 */
@Retention(RUNTIME)
@BindingAnnotation
@interface AcaiInternal {
}
