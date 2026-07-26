package com.example.demo.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.example.demo.interceptor.DevAutoLoginInterceptor;
import com.example.demo.interceptor.NoCacheInterceptor;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final NoCacheInterceptor noCacheInterceptor;

	// devプロファイルが無効な環境ではBeanが存在しない為、ObjectProviderで受け取る
	// （直接注入すると本番環境で起動時エラーになってしまう）
	private final ObjectProvider<DevAutoLoginInterceptor> devAutoLoginInterceptorProvider;
	
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

		// devプロファイルが有効な場合のみ、自動ログインインターセプターを登録する
		DevAutoLoginInterceptor devInterceptor = devAutoLoginInterceptorProvider.getIfAvailable();
		if (devInterceptor != null) {
			registry.addInterceptor(devInterceptor)
					.addPathPatterns("/bills/**", "/messages/**", "committees/**");
		}
	}
}
