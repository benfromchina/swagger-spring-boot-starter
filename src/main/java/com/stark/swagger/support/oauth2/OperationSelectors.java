package com.stark.swagger.support.oauth2;

import springfox.documentation.spi.service.contexts.OperationContext;

/**
 * OAuth2 请求选择器。
 * <p>实现该接口，注入 IOC 容器，可自定义需要 OAuth2 校验的请求规则。
 * @author Ben
 * @since 1.0.0
 * @version 1.0.0
 */
@FunctionalInterface
public interface OperationSelectors {
	
	/**
	 * 当前请求是否需要 OAuth2 校验。
	 * @param context 请求上下文。
	 * @return 需要 OAuth2 校验返回 {@literal true} ，否则返回 {@literal false} 。
	 */
	boolean match(OperationContext context);

}
