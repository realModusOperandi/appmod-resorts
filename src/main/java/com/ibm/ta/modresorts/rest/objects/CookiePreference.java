package com.ibm.ta.modresorts.rest.objects;

import java.io.Serializable;

public class CookiePreference implements Serializable {
	private static final long serialVersionUID = 1L;

	public String preference;

	@Override
	public String toString() {
		return "CookiePreference{" +
				"preference='" + preference + '\'' +
				'}';
	}
}
