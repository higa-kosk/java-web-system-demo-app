package com.example.demo.service;

import com.example.demo.form.AmendmentForm;
import com.example.demo.model.*;
import com.example.demo.model.Amendment.AmendmentStatus;
import com.example.demo.model.BillNotification.BillNotificationType;
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
	 * 修正案を新規作成し、法案の原案者に通知を送信する。
	 * 提出者自身が出す場合は即承認、他ユーザーが出す場合は承認待ちにする。
	 */
	@Transactional
	public Amendment createAmendment(AmendmentForm form, User currentUser) {
		// 1-1. 対象の法案を取得
		Bill bill = billRepository.findById(form.getBillId())
				.orElseThrow(() -> new IllegalArgumentException("無効な法案IDです： " + form.getBillId()));

		// 1-2. 修正案が原案者か否かを判定
		boolean isBillOwner = bill.getUser().getId().equals(currentUser.getId());
		
		// 2. 修正案（Amendment）エンティティの作成
		Amendment amendment = new Amendment();
		amendment.setTitle(form.getTitle());
		amendment.setDescription(form.getDescription());
		amendment.setBill(bill);
		amendment.setUser(currentUser);
		amendment.setStatus(isBillOwner ? AmendmentStatus.APPROVED : AmendmentStatus.PENDING);

		Amendment savedAmendment = amendmentRepository.save(amendment);

		// 3. 原案作成者（自分以外の場合）に通知を作成
		if (!isBillOwner) {
			BillNotification notification = new BillNotification();
			notification.setReceiver(bill.getUser()); // 通知先
			notification.setSender(currentUser); // 通知元
			notification.setType(BillNotificationType.AMENDMENT);
			notification.setBill(bill);

			notificationsRepository.save(notification);

		}

		return savedAmendment;
	}

	/**
	 * 修正案を承認する（法案提出者のみ実行可能）
	 * @param amendmentId, @param approver
	 * @return Amendment
	 */
	@Transactional
	public Amendment approveAmendment(Long amendmentId, User approver) {
		Amendment amendment = amendmentRepository.findById(amendmentId)
				.orElseThrow(() -> new IllegalArgumentException("修正案が見つかりません: " + amendmentId));

		if (!amendment.getBill().getUser().getId().equals(approver.getId())) {
			throw new IllegalStateException("この修正案を承認する権限がありません。");
		}

		amendment.setStatus(AmendmentStatus.APPROVED);
		Amendment saved = amendmentRepository.save(amendment);

		if (!amendment.getUser().getId().equals(approver.getId())) {
			BillNotification notification = new BillNotification();

			notification.setReceiver(amendment.getUser());
			notification.setSender(approver);
			notification.setType(BillNotificationType.AMENDMENT_APPROVED);
			notification.setBill(amendment.getBill());

			notificationsRepository.save(notification);
		}

		return saved;
	}

	/**
	 * 修正案を却下する（法案提出者のみ実行可能）
	 * @param amendmendId, @param approver
	 * @return Amendment
	 */
	@Transactional
	public Amendment rejectAmendment(Long amendmentId, User approver) {
		Amendment amendment = amendmentRepository.findById(amendmentId)
				.orElseThrow(() -> new IllegalArgumentException("修正案が見つかりません: " + amendmentId));

		if (!amendment.getBill().getUser().getId().equals(approver.getId())) {
			throw new IllegalStateException("この修正案を却下する権限がありません。");
		}

		amendment.setStatus(AmendmentStatus.REJECTED);
		Amendment saved = amendmentRepository.save(amendment);

		if (!amendment.getUser().getId().equals(approver.getId())) {
			BillNotification notification = new BillNotification();

			notification.setReceiver(amendment.getUser());
			notification.setSender(approver);
			notification.setType(BillNotificationType.AMENDMENT_REJECTED);
			notification.setBill(amendment.getBill());

			notificationsRepository.save(notification);
		}

		return saved;
	}

	/**
	 * 「承認済み」の修正案一覧を取得（一般ユーザー向け、投票対象として表示するもの）
	 */
	@Transactional(readOnly = true)
	public List<Amendment> getApprovedAmendmentsByBillId(Long billId) {
		return amendmentRepository.findByBillIdAndStatusOrderByCreatedAtDesc(billId, AmendmentStatus.APPROVED);
	}

	/**
	 * 「承認待ち」の修正案一覧（法案提出者向け、承認/却下操作をする為の物）
	 */
	@Transactional(readOnly = true)
	public List<Amendment> getPendingAmendmentsByBillId(Long billId) {
		return amendmentRepository.findByBillIdAndStatusOrderByCreatedAtDesc(billId, AmendmentStatus.PENDING);
	}
}
