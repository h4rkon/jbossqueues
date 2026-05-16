package dev.hzd.jbossqueues;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/messages")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MessageResource {
    private static final Logger LOGGER = Logger.getLogger(MessageResource.class.getName());

    @GET
    public Response list() {
        return Response.ok("[]").build();
    }

    @POST
    public Response create(MessagePayload payload) {
        LOGGER.info(() -> "Received message payload: {\"key\":\"%s\",\"value\":\"%s\"}"
                .formatted(payload.key(), payload.value()));
        return Response.ok(payload).build();
    }
}
