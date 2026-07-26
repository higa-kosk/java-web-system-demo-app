package com.example.demo.service;

import com.example.demo.model.Bill;
import com.example.demo.model.User;
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
	
	private final LikeRepository likeRepository;
	private final VoteRepository voteRepository;

	// 単体のBillにいいね数・投票数・自分の状態を付与する
	public void attachEngagementInfo(Bill bill, User currentUser) {
		bill.setLikeCount(likeRepository.countByBill(bill));
		bill.setVoteCount(voteRepository.countByBill(bill));

		if (currentUser != null) {
			bill.setLikedByMe(likeRepository.existsByUserAndBill(currentUser, bill));
			bill.setVotedByMe(voteRepository.existsByUserAndBill(currentUser, bill));
		} else {
			bill.setLikedByMe(false);
			bill.setVotedByMe(false);
		}
	}

	// リストのBillそれぞれにいいね数・投票数・自分の状態を付与する（一覧画面用）
	// BillIdのリストに対してまとめて集計クエリを発行することでN+1を解消している
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
		Map<Long, Long> voteCounts = voteRepository.countByBillIdIn((billIds)).stream()
				.collect(Collectors.toMap(VoteRepository.BillCount::getBillId, VoteRepository.BillCount::getCnt));
		
		// 自分がいいね・投票したBillIdの集合（ログイン時のみ、それぞれSQL1回）
		Set<Long> likedByMeIds = currentUser != null
			? new HashSet<>(likeRepository.findLikedBillIdsByUser(currentUser.getId(), billIds))
			: Set.of();
		Set<Long> votedByMeIds = currentUser != null
			? new HashSet<>(voteRepository.findVotedBillIdsByUser(currentUser.getId(), billIds))
			: Set.of();

		// ここから先はメモリ上の処理のみ（追加のSQLは発行されない）
		for (Bill bill : bills) {
			bill.setLikeCount(likeCounts.getOrDefault(bill.getId(), 0L));
			bill.setVoteCount(voteCounts.getOrDefault(bill.getId(), 0L));
			bill.setLikedByMe(likedByMeIds.contains(bill.getId()));
			bill.setVotedByMe(votedByMeIds.contains(bill.getId()));
		}
	}
}
