package org.ovirt.optimizer.setup;

import com.sun.org.apache.xerces.internal.jaxp.datatype.XMLGregorianCalendarImpl;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.ovirt.engine.sdk.decorators.Host;
import org.ovirt.engine.sdk.decorators.Network;
import org.ovirt.engine.sdk.decorators.SchedulingPolicy;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.entities.Property;
import org.ovirt.engine.sdk.web.HttpProxy;
import org.ovirt.engine.sdk.web.HttpProxyBroker;
import org.ovirt.optimizer.solver.facts.RunningVm;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OvirtSdkReserializationTest {
    @Mock
    HttpProxy httpProxy;

    /**
     * Test whether all used entities can be properly serialized and deserialized
     * by the object mapper when wrapped with oVirt SDK decorators.
     * @throws Exception
     */
    @Test
    public void testHostSerialization() throws Exception{
        JacksonContextResolver contextResolver = new JacksonContextResolver();
        ObjectMapper mapper = contextResolver.getContext(Host.class);

        when(httpProxy.getUrl()).thenReturn(new URL("http://localhost/api/hosts"));
        HttpProxyBroker broker = new HttpProxyBroker(httpProxy);

        List<Object> objects = new ArrayList<>();

        Host h = new Host(broker);
        h.setId("test-host");
        objects.add(h);

        VM vm = new VM(broker);
        vm.setId("test-vm");
        vm.setStartTime(new XMLGregorianCalendarImpl());
        vm.setMemory(2048L);
        objects.add(vm);

        Network net = new Network(broker);
        net.setId("test-net");
        objects.add(net);

        SchedulingPolicy schedulingPolicy = new SchedulingPolicy(broker);
        schedulingPolicy.setId("test-policy");
        objects.add(schedulingPolicy);

        Property property = new Property();
        property.setName("test-prop");
        objects.add(property);

        String json = mapper.writer().writeValueAsString(objects);
        mapper.reader().withType(List.class).readValue(json);
    }

    @Test
    public void testCollectionSerialization() throws Exception {
        JacksonContextResolver contextResolver = new JacksonContextResolver();
        ObjectMapper mapper = contextResolver.getContext(Host.class);

        assertEquals("{}", mapper.writeValueAsString(new HashMap<String, VM>()));
        assertEquals("[]", mapper.writeValueAsString(new ArrayList<VM>()));
        assertEquals("[]", mapper.writeValueAsString(new HashSet<VM>()));
    }

    @Test
    public void testRunningVmSerialization() throws Exception {
        JacksonContextResolver contextResolver = new JacksonContextResolver();
        ObjectMapper mapper = contextResolver.getContext(Host.class);

        RunningVm original = new RunningVm("test");
        RunningVm other = mapper.reader().withType(RunningVm.class).readValue(mapper.writeValueAsString(original));

        assertEquals(original.getId(), other.getId());
    }
}
