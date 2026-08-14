package com.example.demo.controller.async;

import com.example.demo.model.Bill;
import com.example.demo.model.User;
import com.example.demo.model.Like;
import com.example.demo.model.BillNotification;
import com.example.demo.repository.LikeRepository;
import com.example.demo.repository.BillRepository;
import com.example.demo.repository.NotificationsRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/bills/{id}/like") // 分かりやすく /api/を頭につけたURLにする
@RequiredArgsConstructor
public class LikeController {
    
	private final BillRepository billRepository;
	private final LikeRepository likeRepository;
	private final NotificationsRepository notificationRepository;

	@PostMapping
	public ResponseEntity<?> toggleLikeAsync(
		@PathVariable("id") Long id,
		HttpSession session) {

		// セッションからログインユーザーを取得
		User currentUser = (User) session.getAttribute("loginUser");
		if (currentUser == null) {
			// セッション切れの場合は401エラーを返す
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("ログインが必要です");
		}
		
		// 1. 対象の投稿を探す
		Bill bill = billRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Invalid bill ID: " + id));

		// 3. 既に自分がこの投稿にいいねしているかチェック
		Optional<Like> existingLike = likeRepository.findByUserAndBill(currentUser, bill);

		boolean liked;
		if (existingLike.isPresent()) {
			// 3-1. 既にあれば「いいね解除」：レコードを削除
			likeRepository.delete(existingLike.get());
			liked = false;
		} else {
			// 3-2. なければ「いいね登録」：レコードを新規保存
			Like newLike = new Like();
			newLike.setUser(currentUser);
			newLike.setBill(bill);
			likeRepository.save(newLike);
			liked = true;

			// 通知機能： いいね通知を裏で作成して保存する
			// 自作自演（自分の投稿に自分でいいね）でなければ通知を送る
			if (!bill.getUser().getId().equals(currentUser.getId())) {
				BillNotification notification = new BillNotification();
				notification.setType(BillNotification.BillNotificationType.LIKE); // タイプ：LIKE
				notification.setSender(currentUser); // アクションを起こした人（自分）
				notification.setReceiver(bill.getUser()); // 通知を受ける人（投稿の作者）
				notification.setBill(bill); // 対象の投稿

				notificationRepository.save(notification);
			}
		}

		// 4. 最新の総いいね数をカウントし直す
		long currentLikeCount = likeRepository.countByBill(bill);

		// 5. フロントに「最新の数」と「自分がいいね状態か」をデータで返す
		return ResponseEntity.ok(Map.of(
			"likes", currentLikeCount,
			"liked", liked
		));
	}
}
