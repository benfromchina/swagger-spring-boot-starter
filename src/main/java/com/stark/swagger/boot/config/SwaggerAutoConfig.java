package com.stark.swagger.boot.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stark.swagger.boot.properties.SpringdocProperties;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Swagger 自动配置。
 * @author Ben
 * @since 2023/1/10
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties(SpringdocProperties.class)
public class SwaggerAutoConfig {

	@Configuration
	protected static class SwaggerConfig {

		@Resource
		private SpringdocProperties springdocProperties;

		@ConditionalOnProperty(prefix = "springdoc.gateway", name = "enabled", havingValue = "false", matchIfMissing = true)
		@Bean
		public GroupedOpenApi groupedOpenApi() {
			return GroupedOpenApi.builder()
					.group("default")
					.pathsToMatch("/**")
					.packagesToScan(springdocProperties.getBasePackage())
					.build();
		}

		@Bean
		public OpenAPI openApi() {
			return createOpenApi(springdocProperties);
		}

	}

	@Configuration
	@ConditionalOnWebApplication(type = Type.SERVLET)
	protected static class SwaggerWebMvcConfig {

		@Bean
		@ConditionalOnProperty(prefix = "springdoc", name = "index-redirect", havingValue = "true")
		public WebMvcConfigurer starkSwaggerWebMvcConfigurer() {
			return new WebMvcConfigurer() {

				@Override
				public void addViewControllers(@Nonnull ViewControllerRegistry registry) {
					registry.addRedirectViewController("/", "/swagger-ui/index.html");
					registry.addRedirectViewController("/index", "/swagger-ui/index.html");
				}
			};
		}

	}

	@Configuration
	@ConditionalOnWebApplication(type = Type.REACTIVE)
	protected static class SwaggerWebFluxConfig {

		@Bean
		@ConditionalOnProperty(prefix = "springdoc", name = "index-redirect", havingValue = "true")
		public RouterFunction<ServerResponse> swaggerIndexRouter(WebFluxProperties webFluxProperties) {
			String basePath = StringUtils.defaultString(webFluxProperties.getBasePath());
			return RouterFunctions.route()
					.GET("", request -> ServerResponse.temporaryRedirect(URI.create(basePath + "/swagger-ui/index.html")).build())
					.GET("/", request -> ServerResponse.temporaryRedirect(URI.create(basePath + "/swagger-ui/index.html")).build())
					.GET("/index", request -> ServerResponse.temporaryRedirect(URI.create(basePath + "/swagger-ui/index.html")).build())
					.build();
		}

	}

	@Configuration
	@ConditionalOnWebApplication(type = Type.REACTIVE)
	@ConditionalOnClass(name = "org.springframework.cloud.gateway.config.GatewayProperties")
	@ConditionalOnProperty(prefix = "springdoc.gateway", name = "enabled", havingValue = "true")
	protected static class GatewaySwaggerConfig {

		@Bean
		public WebFilter gatewaySwaggerFilter(WebFluxProperties webFluxProperties) {
			return (exchange, chain) -> {
				String path = exchange.getRequest().getURI().getPath();
				String basePath = StringUtils.defaultString(webFluxProperties.getBasePath());
				if (path.startsWith(basePath + "/swagger-ui")) {
					String forwardUri = basePath + "/webjars" + StringUtils.substringAfter(path, basePath);
					return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().path(forwardUri).build()).build());
				}
				return chain.filter(exchange);
			};
		}

		@Bean
		public RouterFunction<ServerResponse> gatewaySwaggerIndexRouter(WebFluxProperties webFluxProperties) {
			String basePath = StringUtils.defaultString(webFluxProperties.getBasePath());
			return RouterFunctions.route()
					.GET("", request -> ServerResponse.temporaryRedirect(URI.create(basePath + "/swagger-ui/index.html")).build())
					.GET("/", request -> ServerResponse.temporaryRedirect(URI.create(basePath + "/swagger-ui/index.html")).build())
					.GET("/index", request -> ServerResponse.temporaryRedirect(URI.create(basePath + "/swagger-ui/index.html")).build())
					.build();
		}

		@Bean
		@Lazy(false)
		public List<GroupedOpenApi> apis(GatewayProperties gatewayProperties, SpringdocProperties swaggerProperties, SwaggerUiConfigProperties swaggerUiConfigProperties) {
			Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> urls = gatewayProperties.getRoutes()
					.stream()
					.filter(route -> StringUtils.isBlank(swaggerProperties.getGateway().getServiceIdRegex()) || route.getId().matches(swaggerProperties.getGateway().getServiceIdRegex()))
					.map(route -> {
						AbstractSwaggerUiConfigProperties.SwaggerUrl uri = new AbstractSwaggerUiConfigProperties.SwaggerUrl();
						uri.setName(route.getId());
						uri.setUrl(route.getId() + "/v3/api-docs");
						return uri;
					})
					.collect(Collectors.toSet());
			swaggerUiConfigProperties.setUrls(urls);
			return List.of();
		}

		@Bean
		public RouterFunction<ServerResponse> apiDocsRouter(WebFluxProperties webFluxProperties, GatewayProperties gatewayProperties, SpringdocProperties swaggerProperties, DiscoveryClient discoveryClient) {
			ObjectMapper objectMapper = new ObjectMapper();

			Map<String, String> pathMap = gatewayProperties.getRoutes()
					.stream()
					.filter(route -> StringUtils.isBlank(swaggerProperties.getGateway().getServiceIdRegex()) || route.getId().matches(swaggerProperties.getGateway().getServiceIdRegex()))
					.collect(Collectors.toMap(RouteDefinition::getId, this::getRoutePath));

			return RouterFunctions.route()
					.GET("/{serviceId}/v3/api-docs", request -> {
						String basePath = StringUtils.defaultString(webFluxProperties.getBasePath());
						String host = StringUtils.defaultString(request.headers().firstHeader("Host"));
						if (host.endsWith(":80")) {
							host = StringUtils.substringBefore(host, ":80");
						}
						String serviceId = request.pathVariable("serviceId");
						String path = pathMap.get(serviceId);
						String URL = StringUtils.isNotBlank(host)
								? StringUtils.substringBefore(request.uri().toString(), "://") + "://" + host + basePath + path
								: StringUtils.substringBefore(request.uri().toString(), "/" + serviceId) + path;

						List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
						String url = instances.get(0).getUri().toString() + "/v3/api-docs/default";
						Mono<String> result = WebClient.create().get()
								.uri(url)
								.retrieve()
								.bodyToMono(String.class)
								.map(json -> {
									if (StringUtils.isNotBlank(json)) {
										JsonNode root;
										try {
											root = objectMapper.readTree(json);
										} catch (JsonProcessingException e) {
											throw new RuntimeException(e);
										}

										JsonNode servers = root.get("servers");
										servers.forEach(server -> ((ObjectNode) server).put("url", URL));

										try {
											json = objectMapper.writeValueAsString(root);
										} catch (JsonProcessingException e) {
											throw new RuntimeException(e);
										}
									}
									return json;
								});
						return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result, String.class);
					})
					.build();
		}

		private String getRoutePath(RouteDefinition route) {
			PredicateDefinition pathPredicate = route.getPredicates()
					.stream()
					.filter(predicateDefinition -> predicateDefinition.getName().equals("Path"))
					.findAny()
					.orElse(null);
			if (pathPredicate != null) {
				Collection<String> args = pathPredicate.getArgs().values();
				String path = IterableUtils.get(args, 0);
				path = StringUtils.substringBeforeLast(path, "/**");

				FilterDefinition stripBasePathFiter = route.getFilters()
						.stream()
						.filter(filter -> "StripBasePath".equals(filter.getName()))
						.findFirst()
						.orElse(null);
				if (stripBasePathFiter != null) {
					int index = path.indexOf("/", 1);
					path = path.substring(index);
				}

				return path;
			}
			throw new NotFoundException("PathPatternPredicate not found");
		}

	}

	@SuppressWarnings("unchecked")
	private static OpenAPI createOpenApi(SpringdocProperties springdocProperties) {
		OpenAPI openApi = new OpenAPI()
				.info(createInfo(springdocProperties))
				.externalDocs(createExternalDocs(springdocProperties));
		Components components = null;
		if (StringUtils.isNotBlank(springdocProperties.getReferer())) {
			String key = StringUtils.defaultIfBlank(springdocProperties.getRefererName(), HttpHeaders.REFERER);

			Parameter refererHeader = new Parameter()
					.in(ParameterIn.HEADER.toString())
					.required(true)
					.schema(new StringSchema().name(key)._default(springdocProperties.getReferer()));
			components = new Components().addParameters(key, refererHeader);
		}
		if (springdocProperties.getSwaggerUi().getOauth().isEnabled()) {
			SpringdocProperties.SwaggerOAuthProperties oauth = springdocProperties.getSwaggerUi().getOauth();
			if (components == null) {
				components = new Components();
			}
			SpringdocProperties.AuthorizationGrantType grantType = oauth.getGrantType() != null
					? oauth.getGrantType()
					: SpringdocProperties.AuthorizationGrantType.PASSWORD;
			Scopes scopes = new Scopes();
			oauth.getScopes().forEach(scope -> scopes.addString(scope, ""));
			if (SpringdocProperties.AuthorizationGrantType.PASSWORD.equals(grantType)) {
				components.addSecuritySchemes("password", new SecurityScheme()
						.type(SecurityScheme.Type.OAUTH2)
						.flows(new OAuthFlows().password(new OAuthFlow()
								.tokenUrl(oauth.getTokenUrl())
								.authorizationUrl(oauth.getAuthorizationUrl())
								.scopes(scopes))));
				openApi.addSecurityItem(new SecurityRequirement().addList("password"));
			} else if (SpringdocProperties.AuthorizationGrantType.AUTHORIZATION_CODE.equals(grantType)) {
				components.addSecuritySchemes("authorization_code", new SecurityScheme()
						.type(SecurityScheme.Type.OAUTH2)
						.flows(new OAuthFlows().authorizationCode(new OAuthFlow()
								.tokenUrl(oauth.getTokenUrl())
								.authorizationUrl(oauth.getAuthorizationUrl())
								.scopes(scopes))));
				openApi.addSecurityItem(new SecurityRequirement().addList("authorization_code"));
			}
		}
		if (components != null) {
			openApi.components(components);
		}
		return openApi;
	}

	private static Info createInfo(SpringdocProperties springdocProperties) {
		Contact contact = new Contact();
		BeanUtils.copyProperties(springdocProperties.getInfo().getContact(), contact);

		License license = new License();
		BeanUtils.copyProperties(springdocProperties.getInfo().getLicense(), license);

		Info info = new Info();
		BeanUtils.copyProperties(springdocProperties.getInfo(), info);
		info.setContact(contact);
		info.setLicense(license);
		return info;
	}

	private static ExternalDocumentation createExternalDocs(SpringdocProperties springdocProperties) {
		ExternalDocumentation externalDocs = new ExternalDocumentation();
		BeanUtils.copyProperties(springdocProperties.getExternalDocs(), externalDocs);
		return externalDocs;
	}

}
