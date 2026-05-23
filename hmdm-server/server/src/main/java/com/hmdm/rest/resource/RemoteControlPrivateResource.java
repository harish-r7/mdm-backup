package com.hmdm.rest.resource;

import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.Response;
import com.hmdm.util.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.Authorization;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(tags = {"Remote Control"}, authorizations = {@Authorization("Bearer Token")})
@Singleton
@Path("/private/remote-control")
public class RemoteControlPrivateResource {
    private DeviceDAO deviceDAO;
    private PushService pushService;

    public RemoteControlPrivateResource() {
    }

    @Inject
    public RemoteControlPrivateResource(DeviceDAO deviceDAO, PushService pushService) {
        this.deviceDAO = deviceDAO;
        this.pushService = pushService;
    }

    @POST
    @Path("/{number}/start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response start(@PathParam("number") String number) {
        Device device = deviceDAO.getDeviceByNumber(number);
        if (device == null) {
            return Response.DEVICE_NOT_FOUND_ERROR();
        }

        RemoteControlSessionStore.RemoteSession session = RemoteControlSessionStore.create(number);
        String payload = "{\"token\":\"" + StringUtil.jsonEscape(session.token) + "\"}";
        pushService.send(new PushMessage(PushMessage.TYPE_REMOTE_CONTROL_START, payload, device.getId()));

        return Response.OK(session.toView());
    }

    @POST
    @Path("/{number}/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stop(@PathParam("number") String number) {
        RemoteControlSessionStore.RemoteSession session = RemoteControlSessionStore.remove(number);
        Device device = deviceDAO.getDeviceByNumber(number);
        if (device != null) {
            pushService.send(new PushMessage(PushMessage.TYPE_REMOTE_CONTROL_STOP, "{}", device.getId()));
        }
        return Response.OK(session != null ? session.toView() : null);
    }

    @GET
    @Path("/{number}/frame")
    @Produces(MediaType.APPLICATION_JSON)
    public Response frame(@PathParam("number") String number) {
        RemoteControlSessionStore.RemoteSession session = RemoteControlSessionStore.active(number);
        return Response.OK(session != null ? session.toView() : null);
    }

    @POST
    @Path("/{number}/command")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response command(@PathParam("number") String number, RemoteControlSessionStore.RemoteCommand command) {
        RemoteControlSessionStore.RemoteSession session = RemoteControlSessionStore.active(number);
        if (session == null) {
            return Response.ERROR("remote.control.session.not.active");
        }
        if (command != null) {
            command.id = System.currentTimeMillis();
            session.commands.add(command);
        }
        return Response.OK();
    }
}
