package com.mgmtp.gives.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.media.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Local Dev Fallback: Serve static media files directly from local storage.
        // Note: In staging/production, Nginx intercepts /api/media/** and serves them directly.
        String absolutePath = new File(uploadDir).getAbsolutePath();
        
        // Spring Boot requires static resource paths to end with a slash "/"
        if (!absolutePath.endsWith(File.separator) && !absolutePath.endsWith("/")) {
            absolutePath += "/";
        }

        registry.addResourceHandler("/api/media/**")
                .addResourceLocations("file:" + absolutePath);
    }
}
