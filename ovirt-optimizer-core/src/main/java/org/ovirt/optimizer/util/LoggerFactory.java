package org.ovirt.optimizer.util;

import org.slf4j.Logger;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Implementation of Log object producer for usage in DI environment
 * It takes the name of the injection point and creates accordingly named
 * logger.
 *
 * Example:
 *
 * package a.b.c;
 * class MyClass {
 *     @Inject Logger log;
 * }
 *
 * creates a logger a.b.c.MyClass automatically.
 *
 * Inspired by: http://rbergerpa.blogspot.cz/2011/02/dependency-injection-and-logging.html
 */
public class LoggerFactory {
    @Produces
    public Logger createLogger(InjectionPoint injectionPoint) {
        Class<?> cls = injectionPoint.getMember().getDeclaringClass();
        return org.slf4j.LoggerFactory.getLogger(cls);
    }
}
