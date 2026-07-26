package com.example.demo.service;

import com.example.demo.model.Message;
import com.example.demo.model.MessageNotification;
import com.example.demo.model.Notification;
import com.example.demo.model.User;
import com.example.demo.repository.MessageRepository;
import com.example.demo.repository.NotificationsRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {
	
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final NotificationsRepository notificationsRepository;

	/**
	 * メッセージ送信＋通知作成を1トランザクションでまとめる
	 */ 
	@Transactional
	public Message sendMessage(User sender, Long recipientId, String content) {
		User recipient = userRepository.findById(recipientId)
				.orElseThrow(() -> new IllegalArgumentException("無効な受信者IDです：" + recipientId));
		
		Message message = new Message();
		message.setSender(sender);
		message.setRecipient(recipient);
		message.setContent(content);
		messageRepository.save(message);

		MessageNotification notification = new MessageNotification();
		notification.setSender(sender);
		notification.setReceiver(recipient);
		notification.setRead(false);
		notificationsRepository.save(notification);

		return message;
	}

	/**
	 *  未読のメッセージ通知を一括既読化する
	 */
	@Transactional
	public void markChatAsRead(User loginUser, User talkToUser) {
		List<Notification> unread = notificationsRepository.findUnreadMessageNotificationsFromPartner(loginUser, talkToUser);
		
		if (!unread.isEmpty()) {
			unread.forEach(n -> n.setRead(true));
			notificationsRepository.saveAll(unread);
		}
	}

	/**
	 * チャット相手一覧（最新メッセージ）を取得する
	 * ログインユーザーが関わるメッセージを1回のクエリで取得し、相手毎に最新の1件だけをメモリ上で拾う
	 */
	public Map<User, Message> findLatestMessagesByPartner(User loginUser) {
		List<Message> allMessages = messageRepository.findAllInvolvingUserOrderByCreatedAtDesc(loginUser.getId());

		Map<User, Message> result = new LinkedHashMap<>();

		for (Message m : allMessages) {
			User partner = m.getSender().getId().equals(loginUser.getId())
					? m.getRecipient()
					: m.getSender();

			// 新しい順に並んでいるので、初めて登場した時点の物が最新メッセージ
			result.putIfAbsent(partner, m);
		}

		return result;
	}

	/**
	 * チャット相手毎の未読メッセージ通知数を、1回のクエリでまとめて取得する
	 */
	public Map<User, Long> countUnreadByPartner(User loginUser, Map<User, Message> chatPartners) {
		if (chatPartners.isEmpty()) {
			return new LinkedHashMap<>();
		}

		List<Long> partnerIds = chatPartners.keySet().stream()
				.map(User::getId)
				.toList();

		// 相手ID毎の未読数をMapに変換（SQLは1回）
		Map<Long, Long> unreadCountsByPartnerId 
				= notificationsRepository.countUnreadMessageNotificationsGroupedBySender(loginUser, partnerIds).stream()
				.collect(Collectors.toMap(
					NotificationsRepository.SenderCount::getSenderId,
					NotificationsRepository.SenderCount::getCnt
				));

		// 元のUserオブジェクトの並び順を保ったまま、未読数を割り当てる
		Map<User, Long> result = new LinkedHashMap<>();

		for (User partner : chatPartners.keySet()) {
			result.put(partner, unreadCountsByPartnerId.getOrDefault(partner.getId(), 0L));
		}

		return result;
	}
}
