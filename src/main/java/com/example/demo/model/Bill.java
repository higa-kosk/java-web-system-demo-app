package com.example.demo.model;

import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bills")
@Getter
@Setter
public class Bill {

    // --- 法案ステータスの定義（Enum） ---
    public enum BillStatus {
        UNDER_DELIBERATION("審議中"),
        PASSED("可決"),
        REJECTED("否決"),
        ARCHIVED("廃案"); // 修正案（Amendment）が可決した場合の原案

        private final String displayName;

        // 日本語表示用のコンストラクタ
        BillStatus(String displayName) {
            this.displayName = displayName;
        }

        // 画面で日本語で表示するためのゲッター
        public String getDisplayName() {
            return this.displayName;
        }
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "法案名は必須です")
    @Size(max = 100, message = "法案名は100文字以内で入力してください")
    @Column(nullable = false, length = 100)
    private String title;

    @NotBlank(message = "提案理由・本文は必須です")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

     // 審議中・可決・否決のステータス管理用
    // --- ステータスフィールド（デフォルト値を「審議中」に設定） ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BillStatus status = BillStatus.UNDER_DELIBERATION;

    @ManyToOne
    @JoinColumn(
        name = "committee_id",
        nullable = false,
        foreignKey = @ForeignKey(foreignKeyDefinition = "FOREIGN KEY (committee_id) REFERENCES committees(id) ON DELETE CASCADE")
    )
    private Committee committee;

    // 提案者（ユーザー「多（bill）対 一（User）」のリレーションを設定
    @ManyToOne
    @JoinColumn(
        name = "user_id", 
        nullable = false, 
        foreignKey = @ForeignKey(foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE")) // データベース側で user_id という外部キー（FK）を作って紐づける
    private User user;

    // いいねを定義
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Like> likes = new HashSet<>();

    // 投票を定義
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Vote> votes = new HashSet<>();

    // 1つの法案に対する複数のコメント（一対多）
    // cascade = CascadeType.ALL にすることで、法案が削除されたらそのコメントも自動で全削除される
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC") // 画面に出すときに自動で古い順に並び替える
    private Set<Comment> comments = new HashSet<>();

    // 法案に紐づく複数のハッシュタグ（多対多）
    @ManyToMany
    @JoinTable(
        name = "bill_tags", // これが自動生成される「中間テーブル」の名前になる
        joinColumns = @JoinColumn(name = "bill_id"), // 自分（bill）側のID列
        inverseJoinColumns = @JoinColumn(name = "tag_id") // 相手（Tag）側のID列
    )
    private Set<Tag> tags = new HashSet<>();

    // 採決予定日時（提案者が設定）
    @Column(name = "voting_deadline")
    private LocalDateTime votingDeadline;

    // 実際に採決が実行された日時
    @Column(name = "voting_completed_at")
    private LocalDateTime votingCompletedAt;
    
    // 提出日時
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 更新日時
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 保存される直前に、自動で現在日時をセットするアノテーション
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // データベースのカラムではなく、画面表示時に対象ユーザーに応じて動的に詰めるフィールドを利用
    // Like（いいね）を一時的に取り扱う為の記述
    @Transient
    private long likeCount; // 総いいね数

    @Transient
    private boolean likedByMe; // 自分がいいねしているか

    @Transient
    private long yeaCount; // 賛成票の数（画面で「賛成：〇票」と出力する用）
    
    @Transient
    private long nayCount; // 反対票の数（画面で「反対：〇票」と出力する用）

    @Transient
    private VoteChoice myChoice;
}
