package com.example.demo.repository;

import com.example.demo.model.Like;
import com.example.demo.model.Bill;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
	
	// 特定のユーザーが、特定のて案にいいねしているかを探す
	Optional<Like> findByUserAndBill(User user, Bill bill);

	// 該当する投稿の総いいね数を数える
	long countByBill(Bill bill);

	// 特定のユーザーがすでにVoteしているかどうかの判定用
	boolean existsByUserAndBill(User user, Bill bill);

	// 複数のBillIdに対して、Bill単位のいいね数をまとめて集計する（N+1解消用）
	@Query("SELECT l.bill.id AS billId, COUNT(l) AS cnt " +
			"FROM Like l WHERE l.bill.id IN :billIds " +
			"GROUP BY l.bill.id")
			List<BillCount> countByBillIdIn(@Param("billIds") Collection<Long> billIds);

	// 指定ユーザーが「いいね」しているBillIdの集合だけをまとめて取得する（N+1解消用）
	@Query("SELECT l.bill.id FROM Like l " +
			"WHERE l.user.id = :userId AND l.bill.id IN :billIds")
	List<Long> findLikedBillIdsByUser(@Param("userId") Long userId, @Param("billIds") Collection<Long> billIds);

	// 集計結果（BillIdsと件数のペア）を受け取るための射影（Projection）インターフェース
	/**
	 * BillCount
	 */
	public interface BillCount {
		Long getBillId();
		Long getCnt();
	}
}
