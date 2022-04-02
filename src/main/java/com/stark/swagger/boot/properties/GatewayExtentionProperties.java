package com.stark.swagger.boot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@ConfigurationProperties("spring.cloud.gateway")
@Data
public class GatewayExtentionProperties {
	
	/**
	 * A common prefix for all routes.
	 */
	private String prefix;

}
