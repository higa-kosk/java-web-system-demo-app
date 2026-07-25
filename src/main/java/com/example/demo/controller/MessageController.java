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
import com.example.demo.model.Notification;
import com.example.demo.model.User;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.NotificationsRepository;
import com.example.demo.repository.UserRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MessageController {

	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final NotificationsRepository notificationsRepository;

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

		// 実態リスト（List）を取得して一括既読更新を行う
		List<Notification> unreadNotifications = notificationsRepository.findUnreadMessageNotificationsFromPartner(loginUser, talkToUser);
		if (!unreadNotifications.isEmpty()) {
			for (Notification n : unreadNotifications) {
				n.setRead(true);
			}
			notificationsRepository.saveAll(unreadNotifications);
		}
		
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
	public String sendMessage(
			@RequestParam("recipientId") Long recipientId,
			@RequestParam("content") String content,
			HttpSession session) {

		User loginUser = (User) session.getAttribute("loginUser");
		
		if (loginUser == null) {
			return "redirect:/login";
		}

		User recipient = userRepository.findById(recipientId)
				.orElseThrow(() -> new IllegalArgumentException("無効な受信者IDです:" + recipientId));

		// 1. メッセージオブジェクトを作成して保存
		Message message = new Message();
		message.setSender(loginUser);
		message.setRecipient(recipient);
		message.setContent(content);

		messageRepository.save(message);

		// 2. 通知機能：メッセージ受信通知を裏で作成して保存する
		MessageNotification notification = new MessageNotification();
		notification.setSender(loginUser);
		notification.setReceiver(recipient);
		notification.setRead(false);

		notificationsRepository.save(notification);

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

		// 2. つながりのあるユーザー一覧をDBから取得
		// ※本来はMessageRepositoryに専用クエリを書くのがきれいだが、まずは全ユーザーからチャット履歴がある人を抽出する
		List<User> allUsers = userRepository.findAll();
		// 相手ユーザー毎の「最新メッセージ」を格納するマップ（表示順保持のためLinkedHashMap）
		Map<User, Message> chatPartnersMap = new LinkedHashMap<>();
		// 相手ユーザー毎の「未読メッセージ通知数」を格納するマップ
		Map<User, Long> unreadCountsMap = new LinkedHashMap<>();

		for (User user : allUsers) {
			if (user.getId().equals(loginUser.getId())) {
				continue; // 自分自身はスキップ
			}

			// 二人の間のチャット履歴を取得
			List<Message> history = messageRepository.findChatHistory(loginUser.getId(), user.getId());

			if (!history.isEmpty()) {
				// 最新のメッセージ（リストの最後）を取得
				Message latestMessage = history.get(history.size() - 1);
				chatPartnersMap.put(user, latestMessage);

				// この相手から自分宛の「未読メッセージ通知」の数をカウント
				// (TYPE(n) = MessageNotification かつ sender = user かつ receiver = loginUser かつ isRead = false)
				// ここでは簡易的に、リポジトリでカウントするか、後述のリポジトリ修正で対応する
				long unreadCount = notificationsRepository.countUnreadMessageNotificationsFromPartner(loginUser, user);
				unreadCountsMap.put(user, unreadCount);
			}
		}

		// 3. 画面にデータを渡す
		model.addAttribute("loginUser", loginUser);
		model.addAttribute("chatPartners", chatPartnersMap); // キーがUser、値がMessage
		model.addAttribute("unreadCounts", unreadCountsMap); // キーがUser、値が未読数

		return "message_list";
	}
}
