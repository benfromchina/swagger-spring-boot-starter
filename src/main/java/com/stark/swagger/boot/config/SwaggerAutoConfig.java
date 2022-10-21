package com.stark.swagger.boot.config;

import com.stark.swagger.boot.properties.GatewayExtentionProperties;
import com.stark.swagger.boot.properties.SwaggerProperties;
import com.stark.swagger.support.CustomModelAttributeParameterExpander;
import com.stark.swagger.support.oauth2.OperationSelectors;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.*;
import springfox.documentation.schema.ScalarType;
import springfox.documentation.schema.property.bean.AccessorsProvider;
import springfox.documentation.schema.property.field.FieldProvider;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.EnumTypeDeterminer;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.plugins.DocumentationPluginsManager;
import springfox.documentation.swagger.web.SecurityConfiguration;
import springfox.documentation.swagger.web.SecurityConfigurationBuilder;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;

import javax.annotation.Nonnull;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Swagger 自动配置。
 * @author Ben
 * @since 1.0.0
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties(SwaggerProperties.class)
public class SwaggerAutoConfig {
	
	@Configuration
	@ConditionalOnProperty(prefix = "swagger.oauth2", name = "enabled", havingValue = "false", matchIfMissing = true)
	protected static class SwaggerConfig {
		
		@Autowired
		private SwaggerProperties swaggerProperties;
		
		@Bean
		public Docket createRestApi() {
			Docket docket = new Docket(DocumentationType.SWAGGER_2)
					.apiInfo(apiInfo(swaggerProperties))
					.select()
					.apis(RequestHandlerSelectors.basePackage(swaggerProperties.getBasePackage()))
					.paths(PathSelectors.any())
					.build();
			return wrapDocket(docket, swaggerProperties);
		}
		
	}
	
	@Configuration
	@ConditionalOnProperty(prefix = "swagger.oauth2", name = "enabled", havingValue = "true")
	protected static class SwaggerOAuth2Config {
		
		@Autowired
		private SwaggerProperties swaggerProperties;
		@Autowired(required = false)
		private OperationSelectors operationSelectors;
		
		private static final String TOKEN_NAME = "token";
		private static final String CLIENT_ID_NAME = "client_id";
		private static final String CLIENT_SECRET_NAME = "client_secret";
		
		@Bean
		public Docket createRestApi() {
			Docket docket = new Docket(DocumentationType.SWAGGER_2)
					.apiInfo(apiInfo(swaggerProperties))
					.select()
					.apis(RequestHandlerSelectors.basePackage(swaggerProperties.getBasePackage()))
					.paths(PathSelectors.any())
					.build()
					.securitySchemes(Collections.singletonList(oauth()))
					.securityContexts(Collections.singletonList(securityContext()));
			return wrapDocket(docket, swaggerProperties);
		}
		
		@Bean
		public SecurityConfiguration securityConfiguration() {
			return SecurityConfigurationBuilder
					.builder()
					.clientId(swaggerProperties.getOauth2().getClientId())
					.clientSecret(swaggerProperties.getOauth2().getClientSecret())
					.scopeSeparator(",")
					.build();
		}
		
		private SecurityScheme oauth() {
			GrantType grantType = null;
			if ("authorization_code".equals(swaggerProperties.getOauth2().getType())) {
				grantType = new AuthorizationCodeGrantBuilder()
						.tokenEndpoint(builder -> builder
								.url(swaggerProperties.getOauth2().getAccessTokenUrl())
								.tokenName(TOKEN_NAME))
						.tokenRequestEndpoint(builder -> builder
								.url(swaggerProperties.getOauth2().getAuthorizeUrl())
								.clientIdName(CLIENT_ID_NAME)
								.clientSecretName(CLIENT_SECRET_NAME))
						.build();
			}
			if ("password".equals(swaggerProperties.getOauth2().getType())) {
				grantType = new ResourceOwnerPasswordCredentialsGrant(swaggerProperties.getOauth2().getAccessTokenUrl());
			}
			return new OAuthBuilder()
					.name("oauth2")
					.grantTypes(Collections.singletonList(grantType))
					.scopes(oauth2Scopes(swaggerProperties))
					.build();
		}
		
		private SecurityContext securityContext() {
			List<AuthorizationScope> scopes = oauth2Scopes(swaggerProperties);
			SecurityReference securityReference = SecurityReference
	                .builder()
	                .reference("oauth2")
	                .scopes(scopes.toArray(new AuthorizationScope[0]))
	                .build();
			return SecurityContext
					.builder()
					.securityReferences(Collections.singletonList(securityReference))
					.operationSelector(context -> operationSelectors == null || operationSelectors.match(context))
					.build();
		}
		
		private List<AuthorizationScope> oauth2Scopes(SwaggerProperties swaggerProperties) {
			return swaggerProperties.getOauth2().getScopes()
					.stream()
					.map(scope -> new AuthorizationScope(scope.getScope(), scope.getDescription()))
					.collect(Collectors.toList());
		}
		
	}
	
	@Configuration
	protected static class ModelAttributeParameterExpanderConfig {
		
		@Bean
		@Primary
		public CustomModelAttributeParameterExpander customModelAttributeParameterExpander(FieldProvider fields, AccessorsProvider accessors,
				EnumTypeDeterminer enumTypeDeterminer, DocumentationPluginsManager pluginsManager) {
			return new CustomModelAttributeParameterExpander(fields, accessors, enumTypeDeterminer, pluginsManager);
		}
		
	}
	
	@Configuration
	@ConditionalOnWebApplication(type = Type.SERVLET)
	protected static class SwaggerWebMvcConfig {
		
		@Bean
		@ConditionalOnProperty(prefix = "swagger", name = "index-redirect", havingValue = "true")
		public WebMvcConfigurer swaggerWebMvcConfigurer() {
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
		@ConditionalOnProperty(prefix = "swagger", name = "index-redirect", havingValue = "true")
		public RouterFunction<ServerResponse> swaggerIndexRouter() {
			return RouterFunctions.route()
					.GET("/", request -> ServerResponse.temporaryRedirect(URI.create("/swagger-ui/index.html")).build())
					.GET("/index", request -> ServerResponse.temporaryRedirect(URI.create("/swagger-ui/index.html")).build())
					.build();
		}

	}
	
	@Configuration
	@ConditionalOnWebApplication(type = Type.SERVLET)
	@ConditionalOnClass(name = "org.springframework.cloud.netflix.zuul.filters.ZuulProperties")
	@ConditionalOnProperty(prefix = "swagger.zuul", name = "enabled", havingValue = "true")
	protected static class ZuulSwaggerConfig {
		
		@Autowired
		private SwaggerProperties swaggerProperties;
		@Autowired
		private ZuulProperties zuulProperties;
		
		@Bean
		public WebMvcConfigurer zuulSwaggerConfigure() {
			
			return new WebMvcConfigurer() {
				
				@Override
				public void addResourceHandlers(@Nonnull org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
					registry
						.addResourceHandler(zuulProperties.getPrefix() + "/swagger-ui/**")
						.addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
						.resourceChain(false);
				}
				
				@Override
				public void addViewControllers(@Nonnull ViewControllerRegistry registry) {
					registry.addViewController(zuulProperties.getPrefix() + "/swagger-ui/index.html").setViewName("forward:/swagger-ui/index.html");
					registry.addViewController(zuulProperties.getPrefix() + "/swagger-resources").setViewName("forward:/swagger-resources");
					registry.addViewController(zuulProperties.getPrefix() + "/swagger-resources/configuration/ui").setViewName("forward:/swagger-resources/configuration/ui");
					registry.addViewController(zuulProperties.getPrefix() + "/swagger-resources/configuration/security").setViewName("forward:/swagger-resources/configuration/security");
					registry.addViewController(zuulProperties.getPrefix() + "/v2/api-docs").setViewName("forward:/v2/api-docs");
					registry.addViewController(zuulProperties.getPrefix() + "/v3/api-docs").setViewName("forward:/v3/api-docs");
					registry.addRedirectViewController(zuulProperties.getPrefix(), zuulProperties.getPrefix() + "/swagger-ui/index.html");
					registry.addRedirectViewController(zuulProperties.getPrefix() + "/", zuulProperties.getPrefix() + "/swagger-ui/index.html");
				}
			};
		}
		
		@Bean
		@Primary
		public SwaggerResourcesProvider swaggerResourcesProvider() {
			return () -> {
				List<String> serviceIdList = new ArrayList<>();
				return zuulProperties.getRoutes().values().stream()
						.filter(route -> !serviceIdList.contains(route.getServiceId())
								&& (StringUtils.isBlank(swaggerProperties.getZuul().getServiceIdRegex()) || route.getServiceId().matches(swaggerProperties.getZuul().getServiceIdRegex())))
						.map(route -> {
							serviceIdList.add(route.getServiceId());
							String path = StringUtils.substringBefore(route.getPath(), "/**");
							SwaggerResource swaggerResource = new SwaggerResource();
							swaggerResource.setName(route.getServiceId());
							swaggerResource.setLocation(path + "/v2/api-docs");
							swaggerResource.setSwaggerVersion(DocumentationType.SWAGGER_2.getVersion());
							return swaggerResource;
						})
						.collect(Collectors.toList());
			};
		}
		
	}
	
	@Configuration
	@ConditionalOnWebApplication(type = Type.REACTIVE)
	@ConditionalOnClass(name = "org.springframework.cloud.gateway.config.GatewayProperties")
	@ConditionalOnProperty(prefix = "swagger.gateway", name = "enabled", havingValue = "true")
	@EnableConfigurationProperties(GatewayExtentionProperties.class)
	protected static class GatewaySwaggerConfig {
		
		@Autowired
		private GatewayProperties gatewayProperties;
		@Autowired
		private GatewayExtentionProperties gatewayExtentionProperties;
		@Autowired
		private SwaggerProperties swaggerProperties;
		
		@Bean
		public WebFluxConfigurer gatewaySwaggerConfigurer() {
			return new WebFluxConfigurer() {
				
				@Override
				public void addResourceHandlers(@Nonnull org.springframework.web.reactive.config.ResourceHandlerRegistry registry) {
					registry
						.addResourceHandler(StringUtils.defaultIfBlank(gatewayExtentionProperties.getPrefix(), "") + "/swagger-ui/**")
						.addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
						.resourceChain(false);
				}
			};
		}
		
		@Bean
		public WebFilter gatewaySwaggerFilter() {
			return (exchange, chain) -> {
				String path = exchange.getRequest().getURI().getPath();
				String[] swaggerUris = new String[] {
						"/swagger-ui/index.html",
						"/swagger-resources",
						"/swagger-resources/configuration/ui",
						"/swagger-resources/configuration/security",
						"/v2/api-docs",
						"/v3/api-docs"
				};

				String forwardUri = null;
				String prefix = StringUtils.defaultIfBlank(gatewayExtentionProperties.getPrefix(), "");
				for (String swaggerUri : swaggerUris) {
					if (path.equals(prefix + swaggerUri)) {
						forwardUri = swaggerUri;
						break;
					}
				}
				if (forwardUri != null) {
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
		@Primary
		public SwaggerResourcesProvider swaggerResourcesProvider() {
			return () -> {
				List<String> serviceIdList = new ArrayList<>();
				return gatewayProperties.getRoutes().stream()
						.filter(route -> !serviceIdList.contains(route.getId())
								&& (StringUtils.isBlank(swaggerProperties.getGateway().getServiceIdRegex()) || route.getId().matches(swaggerProperties.getGateway().getServiceIdRegex())))
						.map(route -> {
							serviceIdList.add(route.getId());
							String path = "";
							PredicateDefinition pathPredicate = route.getPredicates()
									.stream()
									.filter(predicateDefinition -> predicateDefinition.getName().equals("Path"))
									.findAny()
									.orElse(null);
							if (pathPredicate != null) {
								Collection<String> args = pathPredicate.getArgs().values();
								path = IterableUtils.get(args, 0);
							}
							String location = StringUtils.substringBefore(path, "/**") + "/v2/api-docs";
							if (StringUtils.isNotBlank(gatewayExtentionProperties.getPrefix())) {
								location = StringUtils.substringAfter(location, gatewayExtentionProperties.getPrefix());
							}
							
							SwaggerResource swaggerResource = new SwaggerResource();
							swaggerResource.setName(route.getId());
							swaggerResource.setLocation(location);
							swaggerResource.setSwaggerVersion(DocumentationType.SWAGGER_2.getVersion());
							return swaggerResource;
						})
						.collect(Collectors.toList());
			};
		}
		
	}
	
	private static ApiInfo apiInfo(SwaggerProperties swaggerProperties) {
		return new ApiInfoBuilder()
				.title(swaggerProperties.getTitle())
				.description(swaggerProperties.getDescription())
				.version(swaggerProperties.getVersion())
				.termsOfServiceUrl(swaggerProperties.getTermsOfServiceUrl())
				.contact(contact(swaggerProperties))
				.license(swaggerProperties.getLicense())
				.licenseUrl(swaggerProperties.getLicenseUrl())
				.build();
	}
	
	private static Contact contact(SwaggerProperties swaggerProperties) {
		return new Contact(
				swaggerProperties.getContactName(),
				swaggerProperties.getContactUrl(),
				swaggerProperties.getContactEmail());
	}

	private static Docket wrapDocket(Docket docket, SwaggerProperties swaggerProperties) {
		if (StringUtils.isNotBlank(swaggerProperties.getReferer())) {
			RequestParameter parameter = new RequestParameterBuilder()
					.name(StringUtils.defaultIfBlank(swaggerProperties.getRefererName(), HttpHeaders.REFERER))
					.in(ParameterType.HEADER)
					.hidden(true)
					.required(true)
					.query(param -> param
							.model(model -> model.scalarModel(ScalarType.STRING))
							.defaultValue(swaggerProperties.getReferer())
					)
					.build();
			docket.globalRequestParameters(Collections.singletonList(parameter));
		}
		return docket;
	}
	
}
