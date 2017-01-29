package org.ovirt.optimizer.rest;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Map;

import org.ovirt.optimizer.config.ConfigProvider;
import org.ovirt.optimizer.rest.dto.DebugSnapshot;
import org.ovirt.optimizer.rest.dto.Result;
import org.ovirt.optimizer.rest.dto.ScoreResult;
import org.ovirt.optimizer.solver.OptimizerServiceRemote;
import org.ovirt.optimizer.solver.problemspace.OptimalDistributionStepsSolution;

@Path("/debug")
public class DebugResource {
    @Inject
    private OptimizerServiceRemote optimizer;

    @Context
    org.jboss.resteasy.spi.HttpResponse response;

    @Inject
    private ConfigProvider configProvider;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, DebugSnapshot> getDebugSnapshot() throws IOException {
        response.getOutputHeaders().putSingle("Access-Control-Allow-Origin", "*");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Methods", "GET");

        if (!configProvider.load().getConfig().getProperty(ConfigProvider.DEBUG_ENDPOINT_ENABLED, "false").equals("true")) {
            response.sendError(403, "Debug url is disabled, please check your configuration.");
            return null;
        }

        return optimizer.getDebugSnapshot();
    }

    @POST
    @Path("/{cluster:[^/]*}/{vm:[^/]*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, ScoreResult> simpleSchedule(Map<String,DebugSnapshot> snapshots,
            @PathParam("cluster") String cluster,
            @PathParam("vm") String vm) throws IOException {
        response.getOutputHeaders().putSingle("Access-Control-Allow-Origin", "*");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Methods", "POST");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Headers", "Content-Type");

        if (!configProvider.load().getConfig().getProperty(ConfigProvider.DEBUG_ENDPOINT_ENABLED, "false").equals("true")) {
            response.sendError(403, "Debug url is disabled, please check your configuration.");
            return null;
        }

        OptimalDistributionStepsSolution baseSolution = null;
        Result baseResult = null;

        if (snapshots != null) {
            DebugSnapshot snapshot = snapshots.get(cluster);

            if (snapshot != null) {
                // Get the provided cluster state
                baseSolution = snapshot.getState();
                baseResult = snapshot.getResult();
            }
        }

        return optimizer.simpleSchedule(cluster, baseSolution, baseResult, vm);
    }

    @POST
    @Path("/{cluster:[^/]*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ScoreResult verifyResult(Map<String,DebugSnapshot> snapshots,
            @PathParam("cluster") String cluster) throws IOException {
        response.getOutputHeaders().putSingle("Access-Control-Allow-Origin", "*");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Methods", "POST");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Headers", "Content-Type");

        if (!configProvider.load().getConfig().getProperty(ConfigProvider.DEBUG_ENDPOINT_ENABLED, "false").equals("true")) {
            response.sendError(403, "Debug url is disabled, please check your configuration.");
            return null;
        }

        if (!snapshots.containsKey(cluster)) {
            response.sendError(404, "The debug dump does not contain the requested cluster.");
            return null;
        }

        DebugSnapshot snapshot = snapshots.get(cluster);

        return optimizer.recomputeScore(snapshot.getState(), snapshot.getResult());
    }
}
