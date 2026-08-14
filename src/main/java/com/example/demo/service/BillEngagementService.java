package com.example.demo.service;

import com.example.demo.model.Amendment;
import com.example.demo.model.Bill;
import com.example.demo.model.User;
import com.example.demo.model.VoteChoice;
import com.example.demo.repository.AmendmentVoteRepository;
import com.example.demo.repository.LikeRepository;
import com.example.demo.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillEngagementService {
	
	private final AmendmentVoteRepository amendmentVoteRepository;
	private final LikeRepository likeRepository;
	private final VoteRepository voteRepository;

	// 単体のBillにいいね数・投票数・自分の状態を付与する
	public void attachEngagementInfo(Bill bill, User currentUser) {
		bill.setLikeCount(likeRepository.countByBill(bill));

		// 賛成/反対それぞれの件数を付与する
		bill.setYeaCount(voteRepository.countByBillAndChoice(bill, VoteChoice.YEA));
		bill.setNayCount(voteRepository.countByBillAndChoice(bill, VoteChoice.NAY));

		if (currentUser != null) {
			bill.setLikedByMe(likeRepository.existsByUserAndBill(currentUser, bill));

			// 自分がどちらに投票したかを付与する
			bill.setMyChoice(voteRepository.findChoiceByUserIdAndBillId(currentUser.getId(), bill.getId()).orElse(null));
		} else {
			bill.setLikedByMe(false);

			// 未ログイン時はnull（未投票扱い）
			bill.setMyChoice(null);
		}
	}

	/**
	 * リストのBillそれぞれにいいね数・投票数・自分の状態を付与する（一覧画面用）
	 * BillIdのリストに対してまとめて集計クエリを発行することでN+1を解消している
	 */
	public void attachEngagementInfo(List<Bill> bills, User currentUser) {
		if (bills.isEmpty()) {
			return;
		}

		List<Long> billIds = bills.stream()
				.map(Bill::getId)
				.toList();

		// いいね数をBillIdごとのMapに変換（SQLは1回）
		Map<Long, Long> likeCounts = likeRepository.countByBillIdIn(billIds).stream()
				.collect(Collectors.toMap(LikeRepository.BillCount::getBillId, LikeRepository.BillCount::getCnt));

		// 投票数をBillIdごとのMapに変換（SQLは1回）
		Map<Long, Long> yeaCounts = voteRepository.countByBillIdInAndChoice(billIds, VoteChoice.YEA).stream()
				.collect(Collectors.toMap(VoteRepository.BillCount::getBillId, VoteRepository.BillCount::getCnt));
		
		Map<Long, Long> nayCounts = voteRepository.countByBillIdInAndChoice(billIds, VoteChoice.NAY).stream()
		.collect(Collectors.toMap(VoteRepository.BillCount::getBillId, VoteRepository.BillCount::getCnt));
		
		// 自分がいいね・投票したBillIdの集合（ログイン時のみ、それぞれSQL1回）
		Set<Long> likedByMeIds = currentUser != null
			? new HashSet<>(likeRepository.findLikedBillIdsByUser(currentUser.getId(), billIds))
			: Set.of();

		// ここから先はメモリ上の処理のみ（追加のSQLは発行されない）
		for (Bill bill : bills) {
			bill.setLikeCount(likeCounts.getOrDefault(bill.getId(), 0L));
			bill.setYeaCount(yeaCounts.getOrDefault(bill.getId(), 0L));
			bill.setNayCount(nayCounts.getOrDefault(bill.getId(), 0L));

			if (currentUser != null) {
				bill.setMyChoice(voteRepository.findChoiceByUserIdAndBillId(currentUser.getId(), bill.getId()).orElse(null));
			} else {
				bill.setMyChoice(null);
			}

			bill.setLikedByMe(likedByMeIds.contains(bill.getId()));
		}
	}

	/**
	 * 単体のAmendmentに投票数・自分の状態を付与する
	 */
	public void attachAmendmentEngagementInfo(Amendment amendment, User currentUser) {
		amendment.setYeaCount(amendmentVoteRepository.countByAmendmentAndChoice(amendment, VoteChoice.YEA));
		amendment.setNayCount(amendmentVoteRepository.countByAmendmentAndChoice(amendment, VoteChoice.NAY));

		if (currentUser != null) {
			amendment.setMyChoice(amendmentVoteRepository.findChoiceByUserIdAndAmendmentId(currentUser.getId(), amendment.getId()).orElse(null));
		} else {
			amendment.setMyChoice(null);
		}
	}

	/**
	 * リストのAmendmentそれぞれに投票数・自分の状態を付与する（詳細画面用）
	 * AmendmentIdのリストに対してまとめて集計クエリを発行することでN+1を解消している
	 */
	public void attachAmendmentEngagementInfo(List<Amendment> amendments, User currentUser) {
		if (amendments.isEmpty()) {
			return;
		}

		List<Long> amendmentIds = amendments.stream()
				.map(Amendment::getId)
				.toList();

		// 投票数をAmendmentId毎のMapに変換（SQLは1回）
		Map<Long, Long> yeaCounts = amendmentVoteRepository.countByAmendmentIdInAndChoice(amendmentIds, VoteChoice.YEA).stream()
				.collect(Collectors.toMap(AmendmentVoteRepository.AmendmentCount::getAmendmentId, AmendmentVoteRepository.AmendmentCount::getCnt));
		
		Map<Long, Long> nayCounts = amendmentVoteRepository.countByAmendmentIdInAndChoice(amendmentIds, VoteChoice.NAY).stream()
				.collect(Collectors.toMap(AmendmentVoteRepository.AmendmentCount::getAmendmentId, AmendmentVoteRepository.AmendmentCount::getCnt));

		// ここから先はメモリ上の処理のみ（追加のSQLは発行されない）
		for (Amendment amendment : amendments) {
			amendment.setYeaCount(yeaCounts.getOrDefault(amendment.getId(), 0L));
			amendment.setNayCount(nayCounts.getOrDefault(amendment.getId(), 0L));

			if (currentUser != null) {
				amendment.setMyChoice(amendmentVoteRepository.findChoiceByUserIdAndAmendmentId(currentUser.getId(), amendment.getId()).orElse(null));
			} else {
				amendment.setMyChoice(null);
			}
		}
	}
}
