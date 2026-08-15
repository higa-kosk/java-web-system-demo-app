package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "amendments")
@Getter
@Setter
public class Amendment {

	// 修正案の承認状況
	public enum AmendmentStatus {
		PENDING,	// 承認待ち（提出者以外が提案した場合の初期状態）
		APPROVED,	// 承認済み（正式な修正案として審議・投票対象になる）
		REJECTED,	// 却下（提出者が非承認とした）
		ARCHIVED	// 取り下げられた
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 対象の元法案（親）
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bill_id", nullable = false)
	private Bill bill;

	// 修正案の提出者
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 修正案のタイトル
	@Column(nullable = false)
	private String title;

	// 修正内容および提案理由
	@Column(columnDefinition = "TEXT", nullable = false)
	private String description;

	// 提出日時
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AmendmentStatus status = AmendmentStatus.PENDING;

	// 画面表示用の一時フィールド（Bill.javaのvoteCount等と同じ考え方）
	@Transient
	private long yeaCount;

	@Transient
	private long nayCount;

	@Transient
	private boolean votedByMe;

	@Transient
	private VoteChoice myChoice;	// 自分がどちらに投票したか（null = 未投票）
}
