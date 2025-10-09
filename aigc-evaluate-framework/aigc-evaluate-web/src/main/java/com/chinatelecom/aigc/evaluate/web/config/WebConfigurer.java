package com.chinatelecom.aigc.evaluate.web.config;

import com.chinatelecom.aigc.evaluate.web.interceptor.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.*;

import javax.annotation.Resource;

/**
 * The type Web mvc configurer.
 *
 * @author AIGC
 */
@AutoConfiguration
public class WebConfigurer implements WebMvcConfigurer {


    @Resource
    private RequestInterceptor requestInterceptor;

    /**
     * Override this method to add resource handlers for serving static resources.
     *
     * @param registry
     * @see ResourceHandlerRegistry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations(
                "classpath:/static/");
        registry.addResourceHandler("swagger-ui.html").addResourceLocations(
                "classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations(
                "classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /**
     * Cors configurer web mvc configurer.
     * 跨域配置
     *
     * @return the web mvc configurer
     */
    @Bean
    public WebConfigurer corsConfigurer() {
        return new WebConfigurer() {
            @Override
            //重写父类提供的跨域请求处理的接口
            public void addCorsMappings(CorsRegistry registry) {

                //添加映射路径
                registry.addMapping("/**")
                        //放行哪些原始域
                        .allowedOriginPatterns("*")
                        //是否发送Cookie信息
                        .allowCredentials(true)
                        //放行哪些原始域(请求方式)
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        //放行哪些原始域(头部信息)
                        .allowedHeaders("*")
                        //暴露哪些头部信息（因为跨域访问默认不能获取全部头部信息）
                        .exposedHeaders("Header1", "Header2");
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestInterceptor)
                .addPathPatterns("/aigc/evaluate/**");
    }
}