package com.fsoft.training;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrainingApplication {

	public static void main(String[] args) {
		normalizeLegacyTimeZone();
		SpringApplication.run(TrainingApplication.class, args);
	}

	/**
	 * Some JDKs (e.g. on Windows) report the legacy id "Asia/Saigon", which PostgreSQL 17 rejects
	 * as a TimeZone startup parameter. Map it to its canonical id before any connection is opened.
	 */
	static void normalizeLegacyTimeZone() {
		if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		}
	}

}
