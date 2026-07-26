package com.example.demo.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.model.Message;
import com.example.demo.model.MessageNotification;
import com.example.demo.model.User;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.NotificationsRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.MessageService;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MessageController {

	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final NotificationsRepository notificationsRepository;
	private final MessageService messageService;

	// 特定の相手とのチャット画面を表示する
	@GetMapping("/messages/chat/{talkToUserId}")
	public String chatWithUser(
			@PathVariable("talkToUserId") Long talkToUserId, 
			Model model, 
			HttpSession session,
			HttpServletResponse response) {

		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setDateHeader("Expires", 0);

		// 1. セッションからログインユーザーを取得
		User loginUser = (User) session.getAttribute("loginUser");

		// ログインしていなければ強制的にログイン画面へ戻す
		if (loginUser == null) {
			return "redirect:/login";
		}

		// 2. チャット相手のユーザー情報を取得
		User talkToUser = userRepository.findById(talkToUserId)
				.orElseThrow(() -> new IllegalArgumentException("無効なユーザーIDです:" + talkToUserId));

		// 2-1. 相手ユーザーからの通知を既読に変更する（処理内容はMessageServiceで定義）
		messageService.markChatAsRead(loginUser, talkToUser);
		
		// 3. テストで実証済みの「findChatHistory」を使って、二人の間のメッセージ履歴を取得
		List<Message> chatHistory = messageRepository.findChatHistory(loginUser.getId(), talkToUser.getId());

		// 4. 画面（HTML）にデータを渡す
		model.addAttribute("loginUser", loginUser);
		model.addAttribute("talkToUser", talkToUser);
		model.addAttribute("chatHistory", chatHistory);

		return "chat";
	}

	// メッセージを送信する（保存処理）
	@PostMapping("/messages/send")
	public String sendMessage(@RequestParam("recipientId") Long recipientId,
			@RequestParam("content") String content,
			HttpSession session) {

		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null) {
			return "redirect:/login";
		}

		// メッセージ送信＋通知作成
		messageService.sendMessage(loginUser, recipientId, content);

		// 送信後は、元のチャット画面にリダイレクト（再読み込み）する
		return "redirect:/messages/chat/" + recipientId;
	}

	// メッセージのやり取り相手毎に一覧を表示する
	@GetMapping("/messages/list")
	public String messageList(Model model, HttpServletResponse response, HttpSession session) {

		// ブラウザにキャッシュを強制的に禁止させ、戻った時も必ずサーバーを叩かせる
		response.setHeader("Cache-Control", "no-cache, no-store, must-relavidate"); // HTTP 1.1
		response.setHeader("Pragma", "no-cache"); // HTTP 1.0
		response.setDateHeader("Expires", 0); // Proxies

		// 1. セッションからログインユーザーを取得
		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null) {
			return "redirect:/login";
		}

		// 2. つながりのあるユーザー+メッセージ一覧をDBから取得
		Map<User, Message> chatPartnersMap = messageService.findLatestMessagesByPartner(loginUser);
		// チャット相手毎の未読メッセージ通知数を、1回のクエリでまとめて取得する
		Map<User, Long> unreadCountsMap = messageService.countUnreadByPartner(loginUser, chatPartnersMap);

		// 3. 画面にデータを渡す
		model.addAttribute("loginUser", loginUser);
		model.addAttribute("chatPartners", chatPartnersMap); // キーがUser、値がMessage
		model.addAttribute("unreadCounts", unreadCountsMap); // キーがUser、値が未読数

		return "message_list";
	}
}
