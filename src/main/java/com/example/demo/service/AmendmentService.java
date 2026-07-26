package com.example.demo.service;

import com.example.demo.form.AmendmentForm;
import com.example.demo.model.Amendment;
import com.example.demo.model.Bill;
import com.example.demo.model.BillNotification;
import com.example.demo.model.User;
import com.example.demo.repository.AmendmentRepository;
import com.example.demo.repository.BillRepository;
import com.example.demo.repository.NotificationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AmendmentService {
	
	private final AmendmentRepository amendmentRepository;
	private final BillRepository billRepository;
	private final NotificationsRepository notificationsRepository;

	/**
	 * 修正案を新規作成し、法案の原案者に通知を送信する
	 */
	@Transactional
	public Amendment createAmendment(AmendmentForm form, User currentUser) {
		// 1. 対象の法案を取得
		Bill bill = billRepository.findById(form.getBillId())
				.orElseThrow(() -> new IllegalArgumentException("無効な法案IDです： " + form.getBillId()));
		
		// 2. 修正案（Amendment）エンティティの作成
		Amendment amendment = new Amendment();
		amendment.setTitle(form.getTitle());
		amendment.setDescription(form.getDescription());
		amendment.setBill(bill);
		amendment.setUser(currentUser);

		Amendment savedAmendment = amendmentRepository.save(amendment);

		// 3. 原案作成者（自分以外の場合）に通知を作成
		if (!bill.getUser().getId().equals(currentUser.getId())) {
			BillNotification notification = new BillNotification();
			notification.setReceiver(bill.getUser()); // 通知先
			notification.setSender(currentUser); // 通知元
			notification.setType(BillNotification.BillNotificationType.AMENDMENT);
			notification.setBill(bill);
			notification.setRead(false);

			notificationsRepository.save(notification);
		}

		return savedAmendment;
	}

	/**
	 * 特定の法案に紐づく修正案一覧を取得
	 */
	@Transactional(readOnly = true)
	public List<Amendment> getAmendmentsByBillId(Long billId) {
		return amendmentRepository.findByBillIdOrderByCreatedAtDesc(billId);
	}
}
