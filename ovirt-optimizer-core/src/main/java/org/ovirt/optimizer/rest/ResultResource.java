package org.ovirt.optimizer.rest;

import org.ovirt.optimizer.common.ScoreResult;
import org.slf4j.Logger;
import org.ovirt.optimizer.common.Result;
import org.ovirt.optimizer.service.OptimizerServiceRemote;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

/**
 * This is the main endpoint for getting the optimization results.
 * It communicates with the main OptimizerServiceBean using the EJB api.
 */
@Path("/result")
public class ResultResource {
    @Inject
    private Logger log;

    @Context
    org.jboss.resteasy.spi.HttpResponse response;

    @Inject
    OptimizerServiceRemote optimizer;

    @GET
    @Path("/{cluster}")
    @Produces("application/json")
    public Result planningResult(@PathParam("cluster") String cluster) {
        response.getOutputHeaders().putSingle("Access-Control-Allow-Origin", "*");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Methods", "GET");

        return optimizer.getCurrentResult(cluster);
    }

    @POST
    @Path("/{cluster}/score")
    @Produces("application/json")
    @Consumes("application/json")
    public ScoreResult verifyResult(Result result, @PathParam("cluster") String cluster) {
        response.getOutputHeaders().putSingle("Access-Control-Allow-Origin", "*");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Methods", "POST");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Headers", "Content-Type");

        return optimizer.recomputeScore(cluster, result);
    }

    @OPTIONS
    @Path("/{cluster}/score")
    public void optionsForCors(String cluster) {
        response.getOutputHeaders().putSingle("Access-Control-Allow-Origin", "*");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Methods", "POST");
        response.getOutputHeaders().putSingle("Access-Control-Allow-Headers", "Content-Type");
    }
}
