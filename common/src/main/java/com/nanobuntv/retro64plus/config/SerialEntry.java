package com.nanobuntv.retro64plus.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A settings field that is saved, with the comment written above it in the file. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SerialEntry {
    String comment() default "";
}
