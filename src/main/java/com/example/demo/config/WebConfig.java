package com.example.demo.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.example.demo.interceptor.NoCacheInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final NoCacheInterceptor noCacheInterceptor;
	
	/**
	 * 【画像処理関係】
	 * アップロード先の内部のディレクトリを、表向きのディレクトリに隠ぺいする
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// 保存先フォルダ（uploads/avatars）の絶対パスを取得
		Path uploadDir = Paths.get("uploads/avatars");
		String uploadPath = uploadDir.toFile().getAbsolutePath();

		// URLの「/images/avatars/**」へのアクセスを、実際のローカルフォルダ「uploads/avatars」にマッピングする
		registry.addResourceHandler("/images/avatars/**")
			.addResourceLocations("file:" + uploadPath + "/");
	}

	/**
	 * 【ブラウザキャッシュ対策】
	 * ブラウザの戻るや進むを使用した際に、元のページのキャッシュを読み込まない様にする
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 画面表示系のパスにのみ適用する
		registry.addInterceptor(noCacheInterceptor)
				.addPathPatterns("bills/**", "tags/**", "/committees/**", "/login", "/messages/**", "/notifications/**", "/users/**");
	}
}
