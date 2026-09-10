package com.pawtrack.backend.support;

import org.springframework.context.annotation.Import;
import java.lang.annotation.*;

@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
@Import(StaffMvcTestConfiguration.class)
public @interface StaffRegression {}
