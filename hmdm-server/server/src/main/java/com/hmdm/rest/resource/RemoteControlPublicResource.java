package com.hmdm.rest.resource;

import com.hmdm.rest.json.Response;
import io.swagger.annotations.Api;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Api(tags = {"Remote Control"})
@Singleton
@Path("/public/remote-control")
public class RemoteControlPublicResource {
    @POST
    @Path("/{number}/frame")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postFrame(@PathParam("number") String number, RemoteControlSessionStore.RemoteFrame frame) {
        RemoteControlSessionStore.RemoteSession session = RemoteControlSessionStore.active(number);
        if (session == null || frame == null || !session.token.equals(frame.token)) {
            return Response.ERROR("remote.control.session.invalid");
        }
        session.frame = frame.frame;
        session.width = frame.width;
        session.height = frame.height;
        session.lastFrameTime = System.currentTimeMillis();
        return Response.OK();
    }

    @GET
    @Path("/{number}/command")
    @Produces(MediaType.APPLICATION_JSON)
    public Response pollCommand(@PathParam("number") String number, @QueryParam("token") String token) {
        RemoteControlSessionStore.RemoteSession session = RemoteControlSessionStore.active(number);
        if (session == null || !session.token.equals(token)) {
            return Response.ERROR("remote.control.session.invalid");
        }
        return Response.OK(session.commands.poll());
    }

    @POST
    @Path("/{number}/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stopFromDevice(@PathParam("number") String number, @QueryParam("token") String token) {
        RemoteControlSessionStore.RemoteSession session = RemoteControlSessionStore.active(number);
        if (session == null || !session.token.equals(token)) {
            return Response.ERROR("remote.control.session.invalid");
        }
        RemoteControlSessionStore.remove(number);
        return Response.OK();
    }
}
