package com.example.demo.interceptor;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 開発環境（"dev"プロファイル）でのみ有効になるインターセプター
 * 未ログイン状態でアクセスされた場合、サンプルユーザー（ID:2）で自動ログインさせる
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAutoLoginInterceptor implements HandlerInterceptor {
	
	private final UserRepository userRepository;

	// 開発用に自動ログインさせるサンプルユーザーのID
	private static final Long DEV_USER_ID = 2L;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		HttpSession session = request.getSession();

		if (session.getAttribute("loginUser") == null) {
			userRepository.findById(DEV_USER_ID)
					.ifPresent(devUser -> session.setAttribute("loginUser", devUser));
		}

		return true; // 処理を継続する（falseにするとControllerに到達しなくなる）
	}
}
