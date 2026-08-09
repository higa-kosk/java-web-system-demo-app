package com.example.demo.repository;

import com.example.demo.model.Vote;
import com.example.demo.model.Bill;
import com.example.demo.model.User;
import com.example.demo.model.VoteChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {
	
	// 特定のユーザーが、特定の提案にすでにVoteしているかを探す
	Optional<Vote> findByUserAndBill(User user, Bill bill);

	// 提案毎に、いくつのVote（投票）がついているかを集計する
	long countByBill(Bill bill);

	// 特定のユーザーがすでにVoteしているかどうかの判定用
	boolean existsByUserAndBill(User user, Bill bill);

	// 単体のBillに対する賛成/反対別の件数（API応答用、都度1件のBillだけ集計すればよい場面）
	long countByBillAndChoice(Bill bill, VoteChoice choice);

	// 複数のBillIdに対して、Bill単位の投票数をまとめて集計する（N+1解消用）
	@Query("SELECT v.bill.id AS billId, COUNT(v) AS cnt " +
			"FROM Vote v WHERE v.bill.id IN :billIds " +
			"GROUP BY v.bill.id")
	List<BillCount> countByBillIdIn(@Param("billIds") Collection<Long> billIds);

	// 指定ユーザーが「Vote」済みのBillIdの集合だけをまとめて取得する（N+1解消用）
	@Query("SELECT v.bill.id FROM Vote v " +
			"WHERE v.user.id = :userId AND v.bill.id IN :billIds")
	List<Long> findVotedBillIdsByUser(@Param("userId") Long userId, @Param("billIds") Collection<Long> billIds);

	// 賛成のみ／反対の身を区別してBill単位で集計する（yeaCount/nayCount用、採決判定用）
	@Query("SELECT v.bill.id AS billId, COUNT(v) AS cnt " +
			"FROM Vote v WHERE v.bill.id IN :billIds AND v.choice = :choice " +
			"GROUP BY v.bill.id")
	List<BillCount> countByBillIdInAndChoice(
		@Param("billIds") Collection<Long> billIds,
		@Param("choice") VoteChoice choice
	);

	// 特定のユーザーの、特定Billに対する投票内容（賛成/反対）を取得する
	@Query("SELECT v.choice FROM Vote v WHERE v.user.id = :userId AND v.bill.id = :billId")
	Optional<VoteChoice> findChoiceByUserIdAndBillId(@Param("userId") Long userId, @Param("billId") Long billId);

	// クラスタリング用の投票行列を作るための生データ取得
	// 「誰が」「どのBillに」「どちらに」投票したかの3つ組を全部取得する
	@Query("SELECT v.user.id AS userId, v.bill.id AS billId, v.choice AS choice " +
			"FROM Vote v WHERE v.bill.id IN :billIds")
	List<VoteRecord> findVoteRecordsByBillIds(@Param("billIds") Collection<Long> billIds);

	// 集計結果（BillIdと件数のペア）を受け取るためのプロジェクションインターフェース
	interface BillCount {
		Long getBillId();
		Long getCnt();
	}

	interface VoteRecord {
		Long getUserId();
		Long getBillId();
		VoteChoice getChoice();
	}
}
