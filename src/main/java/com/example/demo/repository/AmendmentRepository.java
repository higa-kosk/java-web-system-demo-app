package com.example.demo.repository;

import com.example.demo.model.Amendment;
import com.example.demo.model.Amendment.AmendmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmendmentRepository extends JpaRepository<Amendment, Long> {

	// 特定の法案に提出された、全ステータス込みの修正案一覧を日時降順で取得（法案提出者向けの管理画面等で使う想定）
	List<Amendment> findByBillIdOrderByCreatedAtDesc(Long billId);

	// 特定ステータスの修正案だけを取得（一般ユーザー表示には「承認済み」のみ渡す）
	List<Amendment> findByBillIdAndStatusOrderByCreatedAtDesc(Long billId, AmendmentStatus status);

	// 特定の法案に対する修正案の件数取得
	long countByBillId(Long billId);
}