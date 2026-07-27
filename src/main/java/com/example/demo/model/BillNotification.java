package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
// ⭕ データベース上でこのクラスを表す識別値を指定
@DiscriminatorValue("Bill")
@Getter
@Setter
public class BillNotification extends Notification {
	
	// 法案への通知特有のEnum
	public enum BillNotificationType {
		LIKE, VOTE, COMMENT, AMENDMENT,
		AMENDMENT_APPROVED,	// 自分が提出した修正案が承認された
		AMENDMENT_REJECTED	// 自分が提出した修正案が却下された
	}

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type")
	private BillNotificationType type;

	// 投稿への通知にのみ必須となるbillカラム
	@ManyToOne
	@JoinColumn(name = "bill_id",
		foreignKey = @ForeignKey(foreignKeyDefinition = "FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE")
	)
	private Bill bill;
}