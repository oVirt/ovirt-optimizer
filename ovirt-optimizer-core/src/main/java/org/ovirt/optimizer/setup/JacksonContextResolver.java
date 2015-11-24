package org.ovirt.optimizer.setup;

import org.codehaus.jackson.Version;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.module.SimpleModule;
import org.codehaus.jackson.type.JavaType;
import org.ovirt.engine.sdk.common.Decorator;
import org.ovirt.engine.sdk.decorators.Cluster;
import org.ovirt.engine.sdk.decorators.ClusterNetwork;
import org.ovirt.engine.sdk.decorators.Host;
import org.ovirt.engine.sdk.decorators.Network;
import org.ovirt.engine.sdk.decorators.SchedulingPolicy;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.entities.BaseResource;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonContextResolver implements ContextResolver<ObjectMapper> {
    private ObjectMapper mapper;

    public JacksonContextResolver() throws Exception {
        this.mapper = new ObjectMapper()
                .setDefaultTyping(new OvirtTypeResolverBuilder())

                .configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false)
                .configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false)

                .configure(DeserializationConfig.Feature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        mapper.setVisibilityChecker(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.NON_PRIVATE)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.DEFAULT));

        mapper.registerModule(new OvirtSdkEnhancer());

    }

    public ObjectMapper getContext(Class<?> objectType) {
        return mapper;
    }

    public static class OvirtSdkEnhancer extends SimpleModule
    {
        public OvirtSdkEnhancer() {
            super("OvirtSdkEnhancer", new Version(0,0,1,null));
        }

        @Override
        public void setupModule(SetupContext context)
        {
            context.setMixInAnnotations(Decorator.class, OvirtSdkMixin.class);
            context.setMixInAnnotations(BaseResource.class, OvirtSdkMixin.class);
            context.setMixInAnnotations(Host.class, OvirtSdkMixin.class);
            context.setMixInAnnotations(VM.class, OvirtSdkMixin.class);
            context.setMixInAnnotations(Network.class, OvirtSdkMixin.class);
            context.setMixInAnnotations(SchedulingPolicy.class, OvirtSdkMixin.class);
            context.setMixInAnnotations(ClusterNetwork.class, OvirtSdkMixin.class);
            context.setMixInAnnotations(Cluster.class, OvirtSdkMixin.class);
        }
    }

    /**
     * This type resolver disables @class fields for Date types because they are handled
     * specially.
     *
     * It also disables type fields for the primitive collections.
     */
    public static class OvirtTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {
        public OvirtTypeResolverBuilder() {
            super(ObjectMapper.DefaultTyping.NON_FINAL);

            init(JsonTypeInfo.Id.CLASS, null);
            inclusion(JsonTypeInfo.As.PROPERTY);
        }

        @Override
        public boolean useForType(JavaType t) {
            if (XMLGregorianCalendar.class.isAssignableFrom(t.getRawClass())
                    || Date.class.isAssignableFrom(t.getRawClass())) {
                return false;
            } else if (List.class.isAssignableFrom(t.getRawClass())
                    || Map.class.isAssignableFrom(t.getRawClass())
                    || Set.class.isAssignableFrom(t.getRawClass())) {
                return false;
            }

            return super.useForType(t);
        }
    }
}
