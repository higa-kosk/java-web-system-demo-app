package com.example.demo.service;

import com.example.demo.form.CommentForm;
import com.example.demo.model.Amendment;
import com.example.demo.model.Amendment.AmendmentStatus;
import com.example.demo.model.Bill;
import com.example.demo.model.BillNotification;
import com.example.demo.model.Comment;
import com.example.demo.model.Tag;
import com.example.demo.model.User;
import com.example.demo.model.VoteChoice;
import com.example.demo.repository.AmendmentRepository;
import com.example.demo.repository.AmendmentVoteRepository;
import com.example.demo.repository.BillRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.NotificationsRepository;
import com.example.demo.repository.TagRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VoteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BillService {

	private final AmendmentRepository amendmentRepository;
	private final AmendmentVoteRepository amendmentVoteRepository;
	private final BillRepository billRepository;
	private final TagRepository tagRepository;
	private final VoteRepository voteRepository;
	private final CommentRepository commentRepository;
	private final NotificationsRepository notificationsRepository;
	private final UserRepository userRepository;

	/**
	 * 法案を作成・保存し、本文（description）からハッシュタグを抽出して中間テーブルにも保存する
	 */
	@Transactional
	public Bill createBill(Bill bill) {
		// 後述のハッシュタグ抽出・紐づけメソッドを実行後、エンティティにセット
		extractAndAttachTags(bill);

		return billRepository.save(bill);
	}

	/**
	 * 本文からハッシュタグ（#〇〇）を抽出してBillに紐づける内部メソッド
	 */
	private void extractAndAttachTags(Bill bill) {
		String description = bill.getDescription();
		List<Tag> tagList = new ArrayList<>();

		if (description != null && !description.isEmpty()) {
			// 正規表現で「#」から始まる単語を抽出
			Pattern pattern = Pattern.compile("[#＃][A-Za-z0-9ぁ-んァ-ヶ一-龠ー_]+");
			Matcher matcher = pattern.matcher(description);

			while (matcher.find()) {
				// 先頭の「#」を消して小文字化
				String tagName = matcher.group().substring(1).toLowerCase();

				// 既存のタグがあれば取得、無ければ新規保存
				Tag tag = tagRepository.findByName(tagName)
						.orElseGet(() -> {
							Tag newTag = new Tag();
							newTag.setName(tagName);

							return tagRepository.save(newTag);
						});

				if (!tagList.contains(tag)) {
					tagList.add(tag);
				}
			}
		}
		// 抽出したタグのリストをセット（これで、カスケード等により中間テーブルへ保存される）
		bill.getTags().addAll(tagList);
	}

	/**
	 * コメント作成、返信時の答弁済みフラグ更新、通知作成を1トランザクションでまとめる
	 */
	@Transactional
	public Comment postComment(Long billId, User author, CommentForm form) {
		Bill bill = billRepository.findById(billId)
				.orElseThrow(() -> new IllegalArgumentException("法案が見つかりません"));

		Comment comment = new Comment();
		comment.setContent(form.getContent());
		comment.setBill(bill);
		comment.setUser(author);
		comment.setQuestion(form.isQuestion());

		if (form.getParentId() != null) {
			commentRepository.findById(form.getParentId()).ifPresent(parentComment -> {
				comment.setParent(parentComment);

				// て案者自身が質疑に返信（答弁）した場合は、親コメントを「答弁済み」にする
				if (parentComment.isQuestion() && bill.getUser().getId().equals(author.getId())) {
					parentComment.setAnswered(true);
					commentRepository.save(parentComment);
				}
			});
		}

		commentRepository.save(comment);

		// 自作自演（自分の提案に自分でコメント）でなければ、提案者宛に通知を作成
		if (!bill.getUser().getId().equals(author.getId())) {
			BillNotification notification = new BillNotification();
			notification.setType(BillNotification.BillNotificationType.COMMENT);
			notification.setSender(author);
			notification.setReceiver(bill.getUser());
			notification.setBill(bill);
			notificationsRepository.save(notification);
		}

		return comment;
	}

	/**
	 * 提案した法案の削除（撤回）
	 */
	@Transactional
	public void deleteBill(Long billId, User currentUser) {

		// 自分自身の投稿かを分岐して、URLから受け取ったIDを使って、データベースから削除する
		Bill bill = billRepository.findById(billId)
				.orElseThrow(() -> new IllegalArgumentException("修正案が見つかりません: " + billId));

		User me = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + currentUser));

		if (!bill.getUser().getId().equals(me.getId())) {
			throw new IllegalStateException("自分自身の提案のみ撤回ができます。");
		} else {
			billRepository.deleteById(bill.getId());
		}
	}

	/**
	 * 採決を実行する
	 * - votingDeadlineが過ぎたか確認
	 * - 賛成票 > 反対票か確認（50% +1）
	 * - Bill.statusをPASSED/REJECTEDに変更
	 * - votingCompletedAtをセット
	 * - 修正案が可決した場合、原案をARCHIVEDに
	 */
	@Transactional
	public void executeVoting(Long billId) {
		Bill bill = billRepository.findById(billId)
				.orElseThrow(() -> new IllegalArgumentException("法案が見つかりません: " + billId));
		
		// 1. votingDeadlineを確認
		if (bill.getVotingDeadline() == null) {
			throw new IllegalStateException("採決予定日時が設定されていません");
		}

		LocalDateTime now = LocalDateTime.now();
		if (now.isBefore(bill.getVotingDeadline())) {
			throw new IllegalStateException("まだ採決日時に達していません。採決予定日時: " + bill.getVotingDeadline());
		}

		// 2. Billへの投票数をカウント
		long yeaCount = voteRepository.countByBillAndChoice(bill, VoteChoice.YEA);
		long nayCount = voteRepository.countByBillAndChoice(bill, VoteChoice.NAY);
		long totalVotes = yeaCount + nayCount;

		// 3. 投票があったか確認（投票がない場合は否決扱い）
		if (totalVotes == 0) {
			bill.setStatus(Bill.BillStatus.REJECTED);
			bill.setVotingCompletedAt(now);
			billRepository.save(bill);
			return;
		}

		// 4. 承認済み修正案を取得（最新順）
		List<Amendment> approvedAmendments = amendmentRepository.findByBillIdAndStatusOrderByCreatedAtDesc(
				bill.getId(), 
				AmendmentStatus.APPROVED
		);

		Amendment passedAmendment = null;

		// 5. 最新の修正案だけが投票対象
		if (!approvedAmendments.isEmpty()) {
			Amendment latestAmendment = approvedAmendments.get(0); // 最新の修正案

			long amendmentYea = amendmentVoteRepository.countByAmendmentAndChoice(latestAmendment, VoteChoice.YEA);
			long amendmentNay = amendmentVoteRepository.countByAmendmentAndChoice(latestAmendment, VoteChoice.NAY);
			long amendmentTotal = amendmentYea + amendmentNay;

			// 最新修正案の採決判定：賛成 > 反対
			if (amendmentTotal > 0 && amendmentYea > amendmentNay) {
				passedAmendment = latestAmendment;
			}
		}

		// 6. ステータスを確定
		if (passedAmendment != null) {
			
			// 最新修正案が可決 → 原案と過去の修正案をARCHIVEDに
			bill.setStatus(Bill.BillStatus.ARCHIVED);

			// 過去の修正案（最新より前の物）もARCHIVEDに
			for (int i = 1; i < approvedAmendments.size(); i++) {
				approvedAmendments.get(i).setStatus(AmendmentStatus.ARCHIVED);
				amendmentRepository.save(approvedAmendments.get(i));
			}
		
		} else if (yeaCount > nayCount) {

			// 修正案がない、または最新修正案が否決 → 原案が可決
			bill.setStatus(Bill.BillStatus.PASSED);
		
		} else {

			// 原案が否決
			bill.setStatus(Bill.BillStatus.REJECTED);
		}

		bill.setVotingCompletedAt(now);
		billRepository.save(bill);
	}
}