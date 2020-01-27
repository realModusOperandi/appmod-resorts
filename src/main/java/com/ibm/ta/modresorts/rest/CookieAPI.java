package com.ibm.ta.modresorts.rest;

import com.ibm.ta.modresorts.rest.objects.CookiePreference;
import com.ibm.ta.modresorts.rest.objects.StringResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;

@Path("/")
public class CookieAPI {
	public static final String COOKIES_ACCEPTED_KEY = "cookiesAccepted";
	public static final String COOKIES_ACCEPTED_VALUE_FALSE = "false";
	public static final String SUCCESS = "Success";
	public static final String LEFT = "➡️";

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("accepts-cookies")
	public StringResponse setAcceptsCookies(@Context HttpServletRequest request, CookiePreference preference) {
		System.out.println("Entering CookieAPI.setAcceptsCookies(): " + Arrays.toString(new Object[]{preference}));
		if (Boolean.parseBoolean(preference.preference)) {
			// Okay to create a session here since cookies are being accepted
			HttpSession session = request.getSession(true);
			session.setAttribute(COOKIES_ACCEPTED_KEY, preference);
		}

		StringResponse result = new StringResponse();
		result.responseMessage = SUCCESS;
		System.out.println("Exiting CookieAPI.setAcceptsCookies(): " + result);
		return result;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("accepts-cookies")
	public CookiePreference getAcceptsCookies(@Context HttpServletRequest request) {
		System.out.println("Entering CookieAPI.getAcceptsCookies()");
		// Don't create a session if it doesn't exist, since that uses cookies and they may not have accepted them.
		HttpSession session = request.getSession(false);
		if (session != null) {
			CookiePreference preference = (CookiePreference) session.getAttribute(COOKIES_ACCEPTED_KEY);
			System.out.println("Exiting CookieAPI.getAcceptsCookies(): " + preference);
			return preference;
		}

		CookiePreference result = new CookiePreference();
		result.preference = COOKIES_ACCEPTED_VALUE_FALSE;
		System.out.println("Exiting CookieAPI.getAcceptsCookies(): " + result);
		return result;
	}
}
