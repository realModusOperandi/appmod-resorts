package com.ibm.ta.modresorts.rest;

import com.ibm.ta.modresorts.rest.objects.HostInformation;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/")
public class HostAPI {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("host-info")
	public HostInformation getHostInformation(@Context HttpServletRequest request) {
		HostInformation result = new HostInformation();
		result.serverHost = request.getServerName() + ":" + request.getServerPort();
		result.localHost = request.getLocalName() + ":" + request.getLocalPort();

		return result;
	}
}
