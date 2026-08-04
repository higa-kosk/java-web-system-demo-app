package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteService {
	
	private final VoteRepository voteRepository;
	private final AmendmentVoteRepository amendmentVoteRepository;
	private final BillRepository billRepository;
	private final AmendmentRepository amendmentRepository;
	private final UserRepository userRepository;

	// 原案（Bill）への投票（初回投票／賛否の変更の両方に対応）
	@Transactional
	public void voteOnBill(Long billId, Long userId, VoteChoice choice) {

		Bill bill = billRepository.findById(billId)
				.orElseThrow(() -> new IllegalArgumentException("法案が見つかりません: " + billId));
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + userId));
		
		Vote vote = voteRepository.findByUserAndBill(user, bill).orElseGet(Vote::new);
		vote.setUser(user);
		vote.setBill(bill);
		vote.setChoice(choice);
		voteRepository.save(vote);
	}

	// 修正案（Amendment）への投票
	// 承認済みの修正案のみ投票対象とする（承認待ち・却下されたものには投票させない）
	@Transactional
	public void voteOnAmendment(Long amendmentId, Long userId, VoteChoice choice) {
		Amendment amendment = amendmentRepository.findById(amendmentId)
				.orElseThrow(() -> new IllegalArgumentException("修正案が見つかりません" + amendmentId));

		if (amendment.getStatus() != Amendment.AmendmentStatus.APPROVED) {
			throw new IllegalStateException("この修正案はまだ審議対象として承認されていません。");
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("ユーザーがみつかりません: " + userId));

		AmendmentVote vote = amendmentVoteRepository.findByUserAndAmendment(user, amendment).orElseGet(AmendmentVote::new);
		vote.setUser(user);
		vote.setAmendment(amendment);
		vote.setChoice(choice);
		amendmentVoteRepository.save(vote);
	}
}
