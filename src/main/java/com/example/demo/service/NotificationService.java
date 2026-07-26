package com.example.demo.service;

import com.example.demo.model.BillNotification;
import com.example.demo.model.Notification;
import com.example.demo.repository.NotificationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
	
	private final NotificationsRepository notificationsRepository;

	/**
	 * 通知を既読化し、遷移先のパスを決定して返す
	 */
	@Transactional
	public String markAsReadAndResolveRedirectPath(Long notificationId, Long loginUserId) {
		Notification notification = notificationsRepository.findById(notificationId)
				.orElseThrow(() -> new IllegalArgumentException("無効な通知IDです： " + notificationId));

		// 安全のため、ログインユーザー宛の通知である場合のみ既読にする
		if (notification.getReceiver().getId().equals(loginUserId)) {
			notification.setRead(true);
			notificationsRepository.save(notification);
		}

		// 通知の型に応じて、本来の目的地のパスを決定する
		if (notification instanceof BillNotification billNotification) {
			if (billNotification.getBill() != null) {
				return "redirect:/bills/" + billNotification.getBill().getId();
			}

			return "redirect:/notifications";
		} else if (notification.isMessageNotification()) {
			return "redirect:/messages/chat/" + notification.getSender().getId();
		}

		return "redirect:/bills"; // 年の為のフォールバック
	}
}
