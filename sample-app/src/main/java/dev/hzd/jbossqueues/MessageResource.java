package dev.hzd.jbossqueues;

import java.net.URI;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/messages")
public class MessageResource {
    @Inject
    KafkaProducerService producerService;

    @Inject
    ConsumedMessages consumedMessages;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response publish(String message) {
        if (message == null || message.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("message must not be blank\n").build();
        }
        producerService.send(message.strip());
        return Response.created(URI.create("/api/messages/last")).entity("sent\n").build();
    }

    @GET
    @Path("/last")
    @Produces(MediaType.TEXT_PLAIN)
    public String lastMessages() {
        List<String> latest = consumedMessages.latest();
        if (latest.isEmpty()) {
            return "no messages consumed yet\n";
        }
        return String.join("\n", latest) + "\n";
    }
}

