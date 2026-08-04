package com.example.demo.repository;

import com.example.demo.model.Amendment;
import com.example.demo.model.AmendmentVote;
import com.example.demo.model.User;
import com.example.demo.model.VoteChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AmendmentVoteRepository extends JpaRepository<AmendmentVote, Long> {

	Optional<AmendmentVote> findByUserAndAmendment(User user, Amendment amendment);
	boolean existsByUserAndAmendment(User user, Amendment amendment);

	// 賛成/反対別に、Amendment単位でまとめて集計する
	@Query("SELECT av.amendment.id AS amendmentId, COUNT(av) AS cnt " +
			"FROM AmendmentVote av WHERE av.amendment.id IN :amendmentIds AND av.choice = :choice " +
			"GROUP BY av.amendment.id")
	List<AmendmentCount> countByAmendmentIdInAndChoice(
		@Param("amendmentIds") Collection<Long> amendmentIds,
		@Param("choice") VoteChoice choice
	);

	// 特定のユーザーの、特定Amendmentに対する投票内容を取得する
	@Query("SELECT av.choice FROM AmendmentVote av WHERE av.user.id = :userId AND av.amendment.id = :amendmentId")
	Optional<VoteChoice> findChoiceByUserIdAndAmendmentId(@Param("userId") Long userId, @Param("amendmentId") Long amendmentId);

	// クラスタリング用：誰が・どのAmendmentに・どちらに投票したか
	@Query("SELECT av.user.id AS userId, av.amendment.id AS amendmentId, av.choice AS choice " +
			"FROM AmendmentVote av WHERE av.amendment.id IN :amendmentIds")
	List<AmendmentVoteRecord> findVoteRecordsByAmendmentIds(@Param("amendmentIds") Collection<Long> amendmentIds);

	interface AmendmentCount {
		Long getAmendmentId();
		Long getCnt();
	}

	interface AmendmentVoteRecord {
		Long getUserId();
		Long getAmendmentId();
		VoteChoice getChoice();
	}
}
