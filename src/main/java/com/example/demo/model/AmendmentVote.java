package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "amendment_votes", uniqueConstraints = {
	// 同じユーザーが同じ修正案に2回以上投稿できないようにする（Voteと同じ考え方）
	@UniqueConstraint(columnNames = {"user_id", "amendment_id"})
})
@Getter
@Setter
public class AmendmentVote {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "amendment_id", nullable = false)
	private Amendment amendment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private VoteChoice choice;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
