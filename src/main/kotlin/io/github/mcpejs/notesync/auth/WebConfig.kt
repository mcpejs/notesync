package io.github.mcpejs.notesync.auth

import io.github.mcpejs.notesync.common.BodySizeInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val authInterceptor: AuthInterceptor,
    private val bodySizeInterceptor: BodySizeInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns("/auth/**")
        registry.addInterceptor(bodySizeInterceptor)
            .addPathPatterns("/documents/*/versions")
    }
}
