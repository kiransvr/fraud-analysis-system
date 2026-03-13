package com.fraud.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml.service")
public record MlServiceProperties(String baseUrl, Integer maxAttempts, Long backoffMs) {

	public int maxAttemptsOrDefault() {
		if (maxAttempts == null || maxAttempts < 1) {
			return 3;
		}
		return maxAttempts;
	}

	public long backoffMsOrDefault() {
		if (backoffMs == null || backoffMs < 0L) {
			return 50L;
		}
		return backoffMs;
	}
}
