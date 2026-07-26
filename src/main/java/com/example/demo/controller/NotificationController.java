package com.example.demo.controller;

import com.example.demo.model.Notification;
import com.example.demo.model.BillNotification;
import com.example.demo.service.NotificationService;
import com.example.demo.model.User;
import com.example.demo.repository.NotificationsRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationsRepository notificationsRepository;
	private final NotificationService notificationService;

	@GetMapping
	public String showNotifications(HttpSession session, HttpServletResponse response, Model model) {
		
		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setDateHeader("Expires", 0);

		// セッションからログインユーザーを取得
		User currentUser = (User) session.getAttribute("loginUser");
		if (currentUser == null) {
			return "redirect:/login";
		}

		// 1. 自分（Receiver）宛の通知をIDの降順（新しい順）で全件取得
		List<Notification> notifications = notificationsRepository.findByReceiverOrderByIdDesc(currentUser);

		model.addAttribute("notifications", notifications);

		// 全体の未読数をリポジトリから取得
		long totalUnread = notificationsRepository.countByReceiverAndIsReadFalse(currentUser);

		// 取得済みのリストからそれぞれの未読数を安全にカウントする
		long unreadBillCount = notifications.stream()
			.filter(n -> !n.isRead() && n.isBillNotification())
			.count();

		long unreadMessageCount = notifications.stream()
			.filter(n -> !n.isRead() && n.isMessageNotification())
			.count();

		model.addAttribute("unreadBillCount", unreadBillCount);
		model.addAttribute("unreadMessageCount", unreadMessageCount);

		return "notification_list";
	}

	@GetMapping("/{id}/read")
	public String readAndRedirect(@PathVariable Long id, HttpSession session) {
		User loginUser = (User) session.getAttribute("loginUser");
		if (loginUser == null) {
			return "redirect:/login";
		}

		return notificationService.markAsReadAndResolveRedirectPath(id, loginUser.getId());
	}
}
