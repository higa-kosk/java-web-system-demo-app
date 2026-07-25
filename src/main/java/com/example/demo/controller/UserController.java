package com.example.demo.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable; // URLの可変部分（パス）を扱うために必要
import org.springframework.web.bind.annotation.RequestHeader; // フォロー機能追加時に使用
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;

import jakarta.validation.Valid; // バリデーション用1
import org.springframework.validation.BindingResult; // バリデーション用2

@Controller
@RequiredArgsConstructor
public class UserController {

	private final UserRepository userRepository;
	private final UserService userService;

	@GetMapping("/users")
	public String showUserList(HttpSession session, Model model) {

		// セッションからログインユーザーを取得
		User sessionUser = (User) session.getAttribute("loginUser");

		// フォロー中IDを格納する変数（未ログイン時は空っぽ）
		Set<Long> followedUserIds = new HashSet<>();

		// ログインしている場合のみ、最新情報をDBから引き直して画面に渡す
		if (sessionUser != null) {

			userRepository.findById(sessionUser.getId())
					.ifPresent(currentUser -> {
						model.addAttribute("loginUser", currentUser);

						// フォローしているユーザーのIDを抽出
						Set<Long> ids = currentUser.getFollowing().stream()
								.map(User::getId)
								.collect(Collectors.toSet());
						followedUserIds.addAll(ids);
					});
		} else {
			// 未ログインの場合は、明示的にnullを渡してHTML側が正しく判定できるようにする
			model.addAttribute("loginUser", null);
		}

		// 画面に表示するユーザー一覧を取得（ログイン有無に関係なく実行）
		List<User> userList = userRepository.findAll();

		// 画面にデータを渡す
		model.addAttribute("users", userList);
		model.addAttribute("followedUserIds", followedUserIds); // HTML側で使用する

		return "user_list"; // user_list.htmlを表示する
	}

	// 1. 登録フォーム画面を表示する（GETリクエスト）
	@GetMapping("/users/new")
	public String showCreationForm(Model model) {
		// フォームと紐づけるための中身が空のUserオブジェクトを渡す
		model.addAttribute("user", new User());
		return "user_form";
	}

	// 2. 新規登録用の保存窓口：フォームから送信されたデータを保存する（POSTリクエスト）
	@PostMapping("/users/create")
	public String createUser(@Valid User user, BindingResult bindingResult, Model model) {

		// 入力チェックでおかしな点（エラー）が見つかったら、データをモデルに詰めて、フォーム画面に戻す
		if (bindingResult.hasErrors()) {
			model.addAttribute("user", user); // これを書いておくと、エラー内容と入力値を画面に引き継げる
			return "user_form";
		}

		userService.register(user);

		// 保存が終わったら、ログイン画面にリダイレクトさせる
		return "redirect:/login";
	}

	// 3. 編集・更新用の保存窓口
	@PostMapping("/users/update")
	public String updateUser(
			@Valid User user,
			BindingResult bindingResult,
			@RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
			HttpSession session) {

		// 更新の時も同様にエラーがあればフォーム画面に戻す
		if (bindingResult.hasErrors()) {
			return "user_form";
		}

		// 送信されてきたUserのidが、ログイン中のユーザーIDと一致するかチェック
		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null || !user.getId().equals(loginUser.getId())) {
			return "redirect:/login";
		}

		// 更新対象は「フォームから送られてきた新しいデータ（user）であるべき
		User updatedUser = userService.updateProfile(user, avatarFile);

		// 保存後の最新状態（アバターURLも反映済み）をセッションに入れる
		session.setAttribute("loginUser", updatedUser);

		return "redirect:/users";
	}

	// 4. 編集画面を表示する（URLの末尾にあるIDを @PathVariable で受け取る）
	@GetMapping("/users/edit/{id}")
	public String showEditForm(
			@PathVariable("id") Long id,
			HttpSession session,
			Model model) {

		// 現在ログインされているかチェック
		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null) {
			return "redirect:/login"; // 未ログインならログイン画面へ
		}

		// 編集対象のID(id)と、ログインしている本人のID(loginUser.getId())が一致するかチェック
		if (!id.equals(loginUser.getId())) {
			// 本人じゃない場合はエラー画面に飛ばすか、一覧にリダイレクトさせる
			return "redirect:/users";
		}

		// IDを元に、データベースから既存のユーザー情報を1件だけ取得
		// 見つからなかった場合は例外を投げる（今回は簡易的に例外処理を記述）
		User user = userRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

		// 取得した「データが入っているUserオブジェクト」を画面に渡す
		model.addAttribute("user", user);

		return "user_form"; // -> 新規登録で使ったフォーム（user_form.html）を再利用
	}

	// 5. 削除用の窓口
	@PostMapping("/users/delete/{id}")
	public String deleteUser(@PathVariable("id") Long id, HttpSession session) {

		// ログインしているかチェック
		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null) {
			return "redirect:/login";
		}

		// 削除しようとしているIDが、ログイン中の自分自身のIDかチェック
		if (!id.equals(loginUser.getId())) {
			return "redirect:/users";
		}

		userService.deleteUserCascade(id);

		// 自分のアカウントを削除したので、セッションをクリアしてログアウト状態にする
		session.invalidate();

		// 削除が終わったら、ホーム（タイムライン画面）にリダイレクトさせる
		return "redirect:/bills";
	}

	// ユーザーをフォローする窓口
	@PostMapping("/users/{id}/follow")
	public String followUser(
			@PathVariable("id") Long id,
			@RequestHeader(value = "Referer", required = false) String referer,
			HttpSession session) {

		// セッションからログインユーザーを取得してDBから最新状態を引き直す
		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null) {
			return "redirect:/login";
		}

		User me = userService.follow(loginUser.getId(), id);

		// MARK: save()の後、セッション内のユーザー情報も最新（フォロー追加後）に更新しておく
		session.setAttribute("loginUser", me);

		// 5. ボタンを押した元の画面にそのままリダイレクトで戻る
		return referer != null ? "redirect:" + referer.replaceFirst("^https?://[^/]+", "") : "redirect:/bills";
	}

	// ユーザーのフォローを解除する窓口
	@PostMapping("/users/{id}/unfollow")
	public String unfollowUser(
			@PathVariable("id") Long id,
			@RequestHeader(value = "Referer", required = false) String referer,
			HttpSession session) {

		// セッションからログインユーザーを取得してDBから最新状態を引き直す
		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null) {
			return "redirect:/login";
		}

		User me = userService.unfollow(loginUser.getId(), id);

		// MARK: save()の後、セッション内のユーザー情報も最新（フォロー解除後）に更新しておく
		session.setAttribute("loginUser", me);

		return referer != null ? "redirect:" + referer.replaceFirst("^https?://[^/]+", "") : "redirect:/bills";
	}

	@GetMapping("/users/{id}")
	public String viewProfile(
			@PathVariable("id") Long id,
			HttpSession session,
			Model model,
			HttpServletResponse response) {

		// 1. 「戻るボタン」対策：プロフィール画面もキャッシュさせない
		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setDateHeader("Expires", 0);

		// 2. sessyonkararoguモデルに渡す（既存のshowUserListと統一）
		User sessionUser = (User) session.getAttribute("loginUser");
		if (sessionUser != null) {
			userRepository.findById(sessionUser.getId()).ifPresent(currentUser -> {
				model.addAttribute("loginUser", currentUser);
			});
		} else {
			// 未ログイン時はログイン画面に飛ばす
			return "redirect:/login";
		}

		// 3. URLで指定されたIDのユーザー情報をDBから取得
		User targetUser = userRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("指定されたユーザーが見つかりません ID:" + id));

		// 表示対象のユーザー情報をモデルに詰める
		model.addAttribute("targetUser", targetUser);

		return "profile";
	}
}
