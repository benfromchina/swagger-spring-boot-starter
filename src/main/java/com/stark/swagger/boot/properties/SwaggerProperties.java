package com.stark.swagger.boot.properties;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.VendorExtension;

/**
 * Swagger 配置参数。
 * @author Ben
 * @since 1.0.0
 * @version 1.0.0
 */
@ConfigurationProperties("swagger")
@Data
public class SwaggerProperties {
	
	/** 标题 */
	private String title = ApiInfo.DEFAULT.getTitle();
	
	/** 描述 */
    private String description = ApiInfo.DEFAULT.getDescription();
    
    /** 版本号 */
    private String version = ApiInfo.DEFAULT.getVersion();
    
    /** 接口使用条件说明 */
    private String termsOfServiceUrl = ApiInfo.DEFAULT.getTermsOfServiceUrl();
    
    /** 接口维护人姓名 */
    private String contactName = ApiInfo.DEFAULT_CONTACT.getName();
    
    /** 接口维护人网址 */
    private String contactUrl = ApiInfo.DEFAULT_CONTACT.getUrl();
    
    /** 接口维护人电子邮箱 */
    private String contactEmail = ApiInfo.DEFAULT_CONTACT.getEmail();
    
    /** 证书名称 */
    private String license = ApiInfo.DEFAULT.getLicense();
    
    /** 证书链接地址 */
    private String licenseUrl = ApiInfo.DEFAULT.getLicenseUrl();
    
    /** 额外功能扩展 */
    @SuppressWarnings("rawtypes")
	private List<VendorExtension> vendorExtensions = ApiInfo.DEFAULT.getVendorExtensions();
	
    /** 扫描包路径 */
	private String basePackage = "";
	
	/** oauth2 配置项 */
	private SwaggerOAuth2Properties oauth2 = new SwaggerOAuth2Properties();
	
	/** 是否将首页重定向到 swagger 接口文档页，默认 false */
	private boolean indexRedirect;
	
	/** 适用于 zuul 的 swagger 接口收集器配置项 */
	private ZuulSwaggerProperties zuul = new ZuulSwaggerProperties();
	
	/** 适用于 Gateway 的 swagger 接口收集器配置项 */
	private GatewaySwaggerProperties gateway = new GatewaySwaggerProperties();
	
	/**
	 * swagger oauth2 配置参数。
	 */
	@Data
	public static class SwaggerOAuth2Properties {

		/** 是否开启，默认 false */
		private boolean enabled;
		
		/** 授权类型：authorization_code/password/implicit/client_credentials */
		private String type;
		
		/** 获取 token 链接地址 */
		private String accessTokenUrl;
		
		/** 认证链接地址 */
		private String authorizeUrl;
		
		/** 应用ID */
		private String clientId;
		
		/** 应用秘钥 */
		private String clientSecret;
		
		/** 授权作用域 */
		private List<AuthorizationScopeProperties> scopes = Arrays.asList(
				new AuthorizationScopeProperties("all", "所有权限")
		);

	}
	
	/**
	 * 授权作用域配置项。
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AuthorizationScopeProperties {
		
		private String scope;
		
		private String description;
		
	}
	
	/**
	 * 适用于 zuul 的 swagger 接口收集器配置参项。
	 */
	@Data
	public static class ZuulSwaggerProperties {
		
		/** 是否开启 swagger 收集，默认 false */
		private boolean enabled;
		
		/** swagger 源的 service-id 正则表达式，默认空即所有 */
		private String serviceIdRegex;
		
	}
	
	/**
	 * 适用于 Gateway 的 swagger 接口收集器配置参项。
	 */
	@Data
	public static class GatewaySwaggerProperties {
		
		/** 是否开启 swagger 收集，默认 false */
		private boolean enabled;
		
		/** swagger 源的 service-id 正则表达式，默认空即所有 */
		private String serviceIdRegex;
		
	}
	
}
