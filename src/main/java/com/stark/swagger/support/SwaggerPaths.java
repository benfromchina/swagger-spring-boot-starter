package com.stark.swagger.support;

import com.stark.swagger.boot.properties.SpringdocProperties;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * swagger 访问路径工具类。
 *
 * @author Ben
 * @since 2023/1/5
 * @version 1.0.0
 */
@SuppressWarnings("unused")
public class SwaggerPaths {

    /**
     * 生成 gateway 网关 swagger 访问链接列表。
     * @param springdocProperties springdoc 配置项。
     * @param gatewayProperties gateway 配置项。
     * @return swagger 访问链接列表。
     */
    public static String[] createSwaggerPatterns(SpringdocProperties springdocProperties, GatewayProperties gatewayProperties) {
        List<String> list = new ArrayList<>();
        if (springdocProperties.isIndexRedirect()) {
            list.add("");
            list.add("/");
            list.add("/index");
            list.add("/swagger-ui/**");
            list.add("/v3/api-docs/**");
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
                if (StringUtils.isBlank(springdocProperties.getGateway().getServiceIdRegex()) || route.getId().matches(springdocProperties.getGateway().getServiceIdRegex())) {
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

                    list.add(path + "/swagger-ui/**");
                    list.add("/" + route.getId() + "/v3/api-docs/**");
                }
            }
        });
        return list.toArray(new String[0]);
    }

}
