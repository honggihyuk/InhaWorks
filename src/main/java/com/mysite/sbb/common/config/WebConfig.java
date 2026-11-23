package com.mysite.sbb.common.config;

import com.mysite.sbb.board.service.UploadAccessInterceptor;
import com.mysite.sbb.common.file.FileStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// /uploads/** 주소로 업로드 폴더의 사진을 보여준다. 팀 전용 게시판의 첨부는 권한을 확인한다
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final FileStore fileStore;
    private final UploadAccessInterceptor uploadAccessInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(fileStore.getUploadDir().toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(uploadAccessInterceptor).addPathPatterns("/uploads/**");
    }
}
