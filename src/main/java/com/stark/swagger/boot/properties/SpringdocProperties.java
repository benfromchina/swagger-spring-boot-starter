package com.stark.swagger.boot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Swagger 配置参数。
 * @author Ben
 * @since 2023/1/5
 * @version 1.0.0
 */
@ConfigurationProperties("springdoc")
@Data
public class SpringdocProperties {
	
	/** 标题 */
	private Info info = new Info();

	/** 外部文档 */
	private ExternalDocumentation externalDocs = new ExternalDocumentation();
	
    /** 扫描包路径 */
	private String basePackage;
	
	/** 是否将首页重定向到 swagger 接口文档页，默认 false */
	private boolean indexRedirect;

	/** Referer 请求头 */
	private String referer;

	/** Referer 请求头参数名 */
	private String refererName;

	/** 适用于 Gateway 的 swagger 接口收集器配置项 */
	private GatewaySwaggerProperties gateway = new GatewaySwaggerProperties();

	@Data
	public static class Info {

		/** 标题 */
		private String title;

		/** 描述 */
		private String description;

		/** 接口描述 */
		private String termsOfService;

		/** 维护人信息 */
		private Contact contact = new Contact();

		/** 证书信息 */
		private License license = new License();

		/** 版本号 */
		private String version;

		/** 扩展信息 */
		private Map<String, Object> extensions = new LinkedHashMap<>();

		/** 简要说明 */
		private String summary;

		@Data
		public static class Contact {

			/** 名称 */
			private String name;

			/** 链接地址 */
			private String url;

			/** 邮箱 */
			private String email;

			/** 扩展信息 */
			private Map<String, Object> extensions = new LinkedHashMap<>();

		}

		@Data
		public static class License {

			/** 名称 */
			private String name;

			/** 链接地址 */
			private String url;

			/** 标识 */
			private String identifier;

			/** 扩展信息 */
			private Map<String, Object> extensions = new LinkedHashMap<>();

		}

	}

	/** 外部文档 */
	@Data
	public static class ExternalDocumentation {

		/** 描述 */
		private String description;

		/** 连接地址 */
		private String url;

		/** 扩展信息 */
		private Map<String, Object> extensions = new LinkedHashMap<>();

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
