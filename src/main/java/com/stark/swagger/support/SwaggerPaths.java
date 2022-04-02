package com.stark.swagger.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;

import com.stark.swagger.boot.properties.GatewayExtentionProperties;
import com.stark.swagger.boot.properties.SwaggerProperties;

/**
 * swagger 访问路径工具类。
 * @author Ben
 * @since 1.0.0
 * @version 1.0.0
 */
public class SwaggerPaths {
	
	/**
	 * 生成 zuul 网关 swagger 访问链接列表。
	 * @param swaggerProperties swagger 配置项。
	 * @param zuulProperties zuul 配置项。
	 * @return swagger 访问链接列表。
	 */
	public static String[] createSwaggerPatterns(SwaggerProperties swaggerProperties, ZuulProperties zuulProperties) {
		List<String> list = new ArrayList<>();
		if (swaggerProperties.isIndexRedirect()) {
			list.add(zuulProperties.getPrefix());
			list.add(zuulProperties.getPrefix() + "/");
			list.add(zuulProperties.getPrefix() + "/index");
			list.add(zuulProperties.getPrefix() + "/swagger-ui/**");
			list.add(zuulProperties.getPrefix() + "/swagger-resources/**");
			list.add(zuulProperties.getPrefix() + "/v2/api-docs");
			list.add(zuulProperties.getPrefix() + "/v3/api-docs");
		}
		zuulProperties.getRoutes().forEach((key, zuulRoute) -> {
			if (StringUtils.isBlank(swaggerProperties.getZuul().getServiceIdRegex()) || zuulRoute.getServiceId().matches(swaggerProperties.getZuul().getServiceIdRegex())) {
				String contextPath = StringUtils.substringBeforeLast(zuulRoute.getPath(), "/**");
				list.add(zuulProperties.getPrefix() + contextPath);
				list.add(zuulProperties.getPrefix() + contextPath + "/");
				list.add(zuulProperties.getPrefix() + contextPath + "/swagger-ui/index.html");
				list.add(zuulProperties.getPrefix() + contextPath + "/swagger-resources/**");
				list.add(zuulProperties.getPrefix() + contextPath + "/v2/api-docs");
				list.add(zuulProperties.getPrefix() + contextPath + "/v3/api-docs");
			}
		});
		String[] array = list.toArray(new String[list.size()]);
		return array;
	}
	
	/**
	 * 生成 gateway 网关 swagger 访问链接列表。
	 * @param swaggerProperties swagger 配置项。
	 * @param gatewayProperties gateway 配置项。
	 * @param gatewayExtentionProperties gateway 扩展配置项。
	 * @return swagger 访问链接列表。
	 */
	public static String[] createSwaggerPatterns(SwaggerProperties swaggerProperties, GatewayProperties gatewayProperties, GatewayExtentionProperties gatewayExtentionProperties) {
		List<String> list = new ArrayList<>();
		if (swaggerProperties.isIndexRedirect()) {
			String prefix = StringUtils.defaultIfBlank(gatewayExtentionProperties.getPrefix(), "");
			list.add(prefix);
			list.add(prefix + "/");
			list.add(prefix + "/index");
			list.add(prefix + "/swagger-ui/**");
			list.add(prefix + "/swagger-resources/**");
			list.add(prefix + "/v2/api-docs");
			list.add(prefix + "/v3/api-docs");
		}
		gatewayProperties.getRoutes().forEach(route -> {
			PredicateDefinition pathPredicate = route.getPredicates()
					.stream()
					.filter(predicateDefinition -> predicateDefinition.getName().equals("Path"))
					.findAny()
					.orElse(null);
			if (pathPredicate != null) {
				Collection<String> args = pathPredicate.getArgs().values();
				String path = IterableUtils.get(args, 0);
				if (StringUtils.isBlank(swaggerProperties.getGateway().getServiceIdRegex()) || route.getId().matches(swaggerProperties.getGateway().getServiceIdRegex())) {
					path = StringUtils.substringBeforeLast(path, "/**");
					list.add(path);
					list.add(path + "/");
					list.add(path + "/swagger-ui/index.html");
					list.add(path + "/swagger-resources/**");
					list.add(path + "/v2/api-docs");
					list.add(path + "/v3/api-docs");
				}
			}
		});
		String[] array = list.toArray(new String[list.size()]);
		return array;
	}

}
