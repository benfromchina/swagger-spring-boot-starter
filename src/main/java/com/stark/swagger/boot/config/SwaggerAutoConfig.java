package com.stark.swagger.boot.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stark.swagger.boot.properties.GatewayExtentionProperties;
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
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
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
		public RouterFunction<ServerResponse> swaggerIndexRouter() {
			return RouterFunctions.route()
					.GET("/", request -> ServerResponse.temporaryRedirect(URI.create("/swagger-ui/index.html")).build())
					.GET("/index", request -> ServerResponse.temporaryRedirect(URI.create("/swagger-ui/index.html")).build())
					.build();
		}

	}
	
	@Configuration
	@ConditionalOnWebApplication(type = Type.REACTIVE)
	@ConditionalOnClass(name = "org.springframework.cloud.gateway.config.GatewayProperties")
	@ConditionalOnProperty(prefix = "springdoc.gateway", name = "enabled", havingValue = "true")
	@EnableConfigurationProperties(GatewayExtentionProperties.class)
	protected static class GatewaySwaggerConfig {

		@Resource
		private DiscoveryClient discoveryClient;
		@Resource
		private GatewayExtentionProperties gatewayExtentionProperties;
		@Resource
		private SpringdocProperties swaggerProperties;

		@Bean
		public WebFilter gatewaySwaggerFilter() {
			return (exchange, chain) -> {
				String path = exchange.getRequest().getURI().getPath();
				String prefix = StringUtils.defaultIfBlank(gatewayExtentionProperties.getPrefix(), "");
				if (path.startsWith(prefix + "/swagger-ui")) {
					String forwardUri = "/webjars" + StringUtils.substringAfter(path, prefix);
					return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().path(forwardUri).build()).build());
				}
				return chain.filter(exchange);
			};
		}
		
		@Bean
		public RouterFunction<ServerResponse> gatewaySwaggerIndexRouter() {
			String prefix = StringUtils.defaultIfBlank(gatewayExtentionProperties.getPrefix(), "");
			return RouterFunctions.route()
					.GET(prefix, request -> ServerResponse.temporaryRedirect(URI.create(prefix + "/swagger-ui/index.html")).build())
					.GET(prefix + "/", request -> ServerResponse.temporaryRedirect(URI.create(prefix + "/swagger-ui/index.html")).build())
					.build();
		}
		
		@Bean
		@Lazy(false)
		public List<GroupedOpenApi> apis(RouteDefinitionLocator locator, SwaggerUiConfigProperties swaggerUiConfigProperties) {
			List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();
			if (CollectionUtils.isEmpty(definitions)) {
				return List.of();
			}

			Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> urls = definitions
					.stream()
					.filter(route -> StringUtils.isBlank(swaggerProperties.getGateway().getServiceIdRegex()) || route.getId().matches(swaggerProperties.getGateway().getServiceIdRegex()))
					.map(route -> {
						String serviceId = route.getId().startsWith("ReactiveCompositeDiscoveryClient_")
								? StringUtils.substringAfter(route.getId(), "ReactiveCompositeDiscoveryClient_")
								: route.getId();
						AbstractSwaggerUiConfigProperties.SwaggerUrl uri = new AbstractSwaggerUiConfigProperties.SwaggerUrl();
						uri.setName(serviceId);
						uri.setUrl("/" + serviceId + "/v3/api-docs");
						return uri;
					}).collect(Collectors.toSet());
			swaggerUiConfigProperties.setUrls(urls);
			return List.of();
		}

		@Bean
		public RouterFunction<ServerResponse> apiDocsRouter() {
			String prefix = StringUtils.defaultIfBlank(gatewayExtentionProperties.getPrefix(), "");
			ObjectMapper objectMapper = new ObjectMapper();

			return RouterFunctions.route()
					.GET("/{serviceId}/v3/api-docs", request -> {
						String serviceId = request.pathVariable("serviceId");
						String URL = StringUtils.substringBefore(request.uri().toString(), prefix);
						List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
						String url = instances.get(0).getUri().toString() + "/v3/api-docs";
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

										JsonNode paths = root.get("paths");
										Iterator<String> iter = paths.fieldNames();
										Map<String, JsonNode> map = new LinkedHashMap<>();
										while (iter.hasNext()) {
											String key = iter.next();
											map.put(key, paths.get(key));
										}
										map.forEach((key, value) -> {
											ObjectNode node = (ObjectNode) paths;
											node.remove(key);
											node.set(prefix + key, value);
										});

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
