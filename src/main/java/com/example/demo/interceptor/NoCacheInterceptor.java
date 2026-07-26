package com.example.demo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class NoCacheInterceptor implements HandlerInterceptor {
	
	/**
	 * 【ブラウザのキャッシュ対策】
	 * 使用時は、config/WebMvcConfig(implements WebMvcConfigurer)内に、
	 * キャッシュ対策を行いたい画面表示系のパス（例：/bills/**）を記述する。
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// 「戻るボタン」でキャッシュされた古い画面が表示されるのを防ぐ
		response.setHeader("Cache-Control", "no-cache, no-store, must-relavidate"); // HTTP 1.1
		response.setHeader("Pragma", "no-cache"); // HTTP 1.0
		response.setDateHeader("Expires", 0); // Proxies
		return true;
	}
}
