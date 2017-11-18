package org.lendingclub.trident.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;


import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerDateFormatter {
	
	private static Logger logger = LoggerFactory.getLogger(DockerDateFormatter.class);

	public static final String DOCKER_DATE_PATTERN="yyyy-MM-dd'T'HH:mm:ss.nX";
	public static final DateTimeFormatter DOCKER_DATE_TIME_FORMATTER=DateTimeFormatter.ofPattern(DOCKER_DATE_PATTERN).withZone(ZoneId.of("UTC"));
	public static String prettyFormat(String date) {
		if (date == null) {
			return "";
		}
		String prettyTime = date;
		try {
			Optional<Instant> instant = parse(prettyTime);
			if (instant.isPresent()) {
				prettyTime = new PrettyTime().format(new Date(instant.get().toEpochMilli()));
			}
		} catch (RuntimeException e) {
			logger.warn("unexpected exception", e);
		}
		return prettyTime;
	}
	
	public static String prettyFormat(long date) {
	    PrettyTime pt = new org.ocpsoft.prettytime.PrettyTime();
	    return pt.format(new Date(date));
	}

	public static Optional<Instant> parse(String date) {

		try {
		
			ZonedDateTime d = ZonedDateTime.parse(date, DOCKER_DATE_TIME_FORMATTER);
			return Optional.of(d.toInstant());
		} catch (RuntimeException e) {
			logger.warn("", e);
		}
		return Optional.empty();
	}
}
