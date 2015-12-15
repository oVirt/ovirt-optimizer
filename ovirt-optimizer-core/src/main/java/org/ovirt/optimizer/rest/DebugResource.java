package org.ovirt.optimizer.rest;

import org.ovirt.optimizer.common.DebugSnapshot;
import org.ovirt.optimizer.common.Result;
import org.ovirt.optimizer.common.ScoreResult;
import org.ovirt.optimizer.service.OptimizerServiceRemote;
import org.ovirt.optimizer.service.problemspace.Migration;
import org.ovirt.optimizer.service.problemspace.OptimalDistributionStepsSolution;
import org.ovirt.optimizer.util.ConfigProvider;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Path("/debug")
public class DebugResource {
    @Inject
    OptimizerServiceRemote optimizer;

    @Context
    org.jboss.resteasy.spi.HttpResponse response;

    @Inject
    ConfigProvider configProvider;

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
        List<Map<String, String>> preSteps = Collections.emptyList();

        if (snapshots != null) {
            DebugSnapshot snapshot = snapshots.get(cluster);

            if (snapshot != null) {
                // Get the provided cluster state
                baseSolution = snapshot.getState();

                // Get the provided pending migrations
                if (snapshot.getResult() != null
                        && snapshot.getResult().getMigrations() != null) {
                    preSteps = snapshot.getResult().getMigrations();
                }
            }
        }

        return optimizer.simpleSchedule(cluster, baseSolution, preSteps, vm);
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
