package org.ovirt.optimizer.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessBean;
import java.util.HashSet;
import java.util.Set;

/**
 * Extension for the CDI mechanism that instantiates
 * beans that were annotated with Startup right at the
 * application start.
 *
 * Based on http://ovaraksin.blogspot.nl/2013/02/eager-cdi-beans.html
 */
public class AutoloadExtension implements Extension {
    private final Logger log = org.slf4j.LoggerFactory.getLogger(AutoloadExtension.class);

    private Set<Bean<?>> autoloadBeanList = new HashSet<Bean<?>>();
    private Set<Bean<?>> loaded = new HashSet<Bean<?>>();

    public <T> void collect(@Observes ProcessBean<T> event) {
        if (event.getAnnotated().isAnnotationPresent(Autoload.class) &&
                event.getAnnotated().isAnnotationPresent(ApplicationScoped.class)) {
            autoloadBeanList.add(event.getBean());
            log.info(String.format("Adding %s to the list of beans to autostart.",
                    event.getBean().getBeanClass().toString()));
        }
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void load(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        for (Bean<?> bean : autoloadBeanList) {
            if (loaded.contains(bean)) {
                continue;
            }

            // note: toString() is important to instantiate the bean
            beanManager.getReference(bean, bean.getBeanClass(), beanManager.createCreationalContext(bean)).toString();
            loaded.add(bean);
        }
    }
}
