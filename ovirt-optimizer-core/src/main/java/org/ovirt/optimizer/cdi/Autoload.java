package org.ovirt.optimizer.cdi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * To mark a CDI bean for autoloading
 * Based on http://ovaraksin.blogspot.nl/2013/02/eager-cdi-beans.html
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Autoload {
}
