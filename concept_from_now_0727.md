# Claudeへの質問
```:txt
整理した結果、以下の実装でいきましょう。
・直近の「設計方針」のコードを採用
・コメントは「質疑通告」の様とのみとして、そこに賛成か反対は受け付けない（議論の段階の為）
・元の案と修正案それぞれに賛成・反対を表明できる。その際賛成・反対の割合をクラスタリングする。
・採決は賛成か反対それぞれのパーセンテージが閾値（今後検討）を超えればそれが採決結果となる。（いつを採決日時にするかも今後検討）

コード類は、今日のこの整理の時間が始まる前の状態のままなので、修正や追加が必要なコードも含めて改めて教えてください。
```

# Caludeの回答
整理された内容を反映して、必要なコード一式を体系立ててお見せします。まず全体の変更点をリストアップしてから、コードを提示します。

## 変更点サマリー

| 項目 | 状態 |
|---|---|
| `VoteChoice`(賛成/反対の共通enum) | 新規 |
| `Vote.java`(Billへの投票) | 修正(`choice`追加) |
| `AmendmentVote.java`(修正案への投票) | 新規 |
| ~~`CommentVote.java`~~ | **不採用**(コメントは質疑通告のみ、賛否投票は付けない) |
| `Amendment.java` | 修正(承認ステータス追加) |
| `BillNotification.java` | 修正(通知種別追加) |
| `VoteRepository` / `AmendmentVoteRepository` | 新規・修正(集計用クエリ、クラスタリング用データ取得) |
| `VoteService` | 新規(Bill/Amendmentへの投票ロジックを集約) |
| `AmendmentRepository` / `AmendmentService` / `AmendmentController` | 修正(承認フロー) |

---

## 1. VoteChoice.java(新規)

```java
package com.example.demo.model;

// 賛成・反対を表す共通の列挙型（Bill・Amendmentへの投票で共通利用する）
public enum VoteChoice {
    YEA, // 賛成
    NAY  // 反対
}
```

## 2. Vote.java(修正:原案への投票に`choice`を追加)

```java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "votes", uniqueConstraints = {
    // 同じユーザーが同じ法案に「2回以上Voteできない」様に制約をかける（賛否の変更はUPDATEで対応）
    @UniqueConstraint(columnNames = {"user_id", "bill_id"})
})
@Getter
@Setter
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    // 追加: 賛成/反対の区別
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
```

## 3. AmendmentVote.java(新規:修正案への投票、Voteと同じ構造)

```java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "amendment_votes", uniqueConstraints = {
    // 同じユーザーが同じ修正案に2回以上投票できないようにする（Voteと同じ考え方）
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
```

## 4. Amendment.java(修正:承認ステータス追加、前回提示のまま)

```java
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
        PENDING,   // 承認待ち（提出者以外が提案した場合の初期状態）
        APPROVED,  // 承認済み（正式な修正案として審議・投票対象になる）
        REJECTED   // 却下（提出者が非承認とした）
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

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
    private VoteChoice myChoice; // 自分がどちらに投票したか（null = 未投票）
}
```

## 5. BillNotification.java(修正:通知種別を追加)

```java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("Bill")
@Getter
@Setter
public class BillNotification extends Notification {

    public enum BillNotificationType {
        LIKE, VOTE, COMMENT, AMENDMENT,
        AMENDMENT_APPROVED, // 追加: 自分が提出した修正案が承認された
        AMENDMENT_REJECTED  // 追加: 自分が提出した修正案が却下された
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type")
    private BillNotificationType type;

    @ManyToOne
    @JoinColumn(name = "bill_id",
        foreignKey = @ForeignKey(foreignKeyDefinition = "FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE")
    )
    private Bill bill;
}
```

---

## 6. VoteRepository.java(修正:賛否別集計・クラスタリング用データ取得を追加)

```java
package com.example.demo.repository;

import com.example.demo.model.Bill;
import com.example.demo.model.User;
import com.example.demo.model.Vote;
import com.example.demo.model.VoteChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByUserAndBill(User user, Bill bill);
    long countByBill(Bill bill);
    boolean existsByUserAndBill(User user, Bill bill);

    // 既存: 賛否問わない合計投票数の集計（一覧画面用）
    @Query("SELECT v.bill.id AS billId, COUNT(v) AS cnt " +
            "FROM Vote v WHERE v.bill.id IN :billIds " +
            "GROUP BY v.bill.id")
    List<BillCount> countByBillIdIn(@Param("billIds") Collection<Long> billIds);

    @Query("SELECT v.bill.id FROM Vote v " +
            "WHERE v.user.id = :userId AND v.bill.id IN :billIds")
    List<Long> findVotedBillIdsByUser(@Param("userId") Long userId, @Param("billIds") Collection<Long> billIds);

    // 追加: 賛成のみ／反対のみを区別してBill単位で集計する（yeaCount/nayCount用、採決判定用）
    @Query("SELECT v.bill.id AS billId, COUNT(v) AS cnt " +
            "FROM Vote v WHERE v.bill.id IN :billIds AND v.choice = :choice " +
            "GROUP BY v.bill.id")
    List<BillCount> countByBillIdInAndChoice(
        @Param("billIds") Collection<Long> billIds,
        @Param("choice") VoteChoice choice
    );

    // 追加: 特定ユーザーの、特定Billに対する投票内容（賛成/反対）を取得する
    @Query("SELECT v.choice FROM Vote v WHERE v.user.id = :userId AND v.bill.id = :billId")
    Optional<VoteChoice> findChoiceByUserIdAndBillId(@Param("userId") Long userId, @Param("billId") Long billId);

    // 追加: クラスタリング用の投票行列を作るための生データ取得
    // 「誰が」「どのBillに」「どちらに」投票したかの3つ組を全部取得する
    @Query("SELECT v.user.id AS userId, v.bill.id AS billId, v.choice AS choice " +
            "FROM Vote v WHERE v.bill.id IN :billIds")
    List<VoteRecord> findVoteRecordsByBillIds(@Param("billIds") Collection<Long> billIds);

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
```

## 7. AmendmentVoteRepository.java(新規)

```java
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

    // 特定ユーザーの、特定Amendmentに対する投票内容を取得する
    @Query("SELECT av.choice FROM AmendmentVote av WHERE av.user.id = :userId AND av.amendment.id = :amendmentId")
    Optional<VoteChoice> findChoiceByUserIdAndAmendmentId(@Param("userId") Long userId, @Param("amendmentId") Long amendmentId);

    // クラスタリング用: 誰が・どのAmendmentに・どちらに投票したか
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
```

## 8. AmendmentRepository.java(修正:ステータス別取得を追加)

```java
package com.example.demo.repository;

import com.example.demo.model.Amendment;
import com.example.demo.model.Amendment.AmendmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmendmentRepository extends JpaRepository<Amendment, Long> {

    // 全ステータス込みの一覧（法案提出者向けの管理画面等で使う想定）
    List<Amendment> findByBillIdOrderByCreatedAtDesc(Long billId);

    // 特定ステータスの修正案だけを取得（一般ユーザー表示には「承認済み」のみ渡す）
    List<Amendment> findByBillIdAndStatusOrderByCreatedAtDesc(Long billId, AmendmentStatus status);

    long countByBillId(Long billId);
}
```

---

## 9. VoteService.java(新規:Bill/Amendmentへの投票ロジックを集約)

```java
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
            .orElseThrow(() -> new IllegalArgumentException("修正案が見つかりません: " + amendmentId));

        if (amendment.getStatus() != Amendment.AmendmentStatus.APPROVED) {
            throw new IllegalStateException("この修正案はまだ審議対象として承認されていません。");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + userId));

        AmendmentVote vote = amendmentVoteRepository.findByUserAndAmendment(user, amendment).orElseGet(AmendmentVote::new);
        vote.setUser(user);
        vote.setAmendment(amendment);
        vote.setChoice(choice);
        amendmentVoteRepository.save(vote);
    }
}
```

`orElseGet(Vote::new)` / `orElseGet(AmendmentVote::new)`としているのは、「既に投票済みなら賛否を上書き(意見の変更)、未投票なら新規作成」という挙動です。「一度投票したら変更不可」にしたい場合は別途ご相談ください。

---

## 10. AmendmentService.java(修正:承認フロー、前回提示のまま)

```java
package com.example.demo.service;

import com.example.demo.form.AmendmentForm;
import com.example.demo.model.*;
import com.example.demo.model.Amendment.AmendmentStatus;
import com.example.demo.model.BillNotification.BillNotificationType;
import com.example.demo.repository.AmendmentRepository;
import com.example.demo.repository.BillRepository;
import com.example.demo.repository.NotificationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AmendmentService {

    private final AmendmentRepository amendmentRepository;
    private final BillRepository billRepository;
    private final NotificationsRepository notificationsRepository;

    // 修正案を新規作成する。提出者自身が出す場合は即承認、他ユーザーが出す場合は承認待ちにする
    @Transactional
    public Amendment createAmendment(AmendmentForm form, User currentUser) {
        Bill bill = billRepository.findById(form.getBillId())
                .orElseThrow(() -> new IllegalArgumentException("無効な法案IDです： " + form.getBillId()));

        boolean isBillOwner = bill.getUser().getId().equals(currentUser.getId());

        Amendment amendment = new Amendment();
        amendment.setTitle(form.getTitle());
        amendment.setDescription(form.getDescription());
        amendment.setBill(bill);
        amendment.setUser(currentUser);
        amendment.setStatus(isBillOwner ? AmendmentStatus.APPROVED : AmendmentStatus.PENDING);

        Amendment savedAmendment = amendmentRepository.save(amendment);

        if (!isBillOwner) {
            BillNotification notification = new BillNotification();
            notification.setReceiver(bill.getUser());
            notification.setSender(currentUser);
            notification.setType(BillNotificationType.AMENDMENT);
            notification.setBill(bill);
            notificationsRepository.save(notification);
        }

        return savedAmendment;
    }

    // 修正案を承認する（法案提出者のみ実行可能）
    @Transactional
    public Amendment approveAmendment(Long amendmentId, User approver) {
        Amendment amendment = amendmentRepository.findById(amendmentId)
                .orElseThrow(() -> new IllegalArgumentException("修正案が見つかりません: " + amendmentId));

        if (!amendment.getBill().getUser().getId().equals(approver.getId())) {
            throw new IllegalStateException("この修正案を承認する権限がありません。");
        }

        amendment.setStatus(AmendmentStatus.APPROVED);
        Amendment saved = amendmentRepository.save(amendment);

        if (!amendment.getUser().getId().equals(approver.getId())) {
            BillNotification notification = new BillNotification();
            notification.setReceiver(amendment.getUser());
            notification.setSender(approver);
            notification.setType(BillNotificationType.AMENDMENT_APPROVED);
            notification.setBill(amendment.getBill());
            notificationsRepository.save(notification);
        }

        return saved;
    }

    // 修正案を却下する（法案提出者のみ実行可能）
    @Transactional
    public Amendment rejectAmendment(Long amendmentId, User approver) {
        Amendment amendment = amendmentRepository.findById(amendmentId)
                .orElseThrow(() -> new IllegalArgumentException("修正案が見つかりません: " + amendmentId));

        if (!amendment.getBill().getUser().getId().equals(approver.getId())) {
            throw new IllegalStateException("この修正案を却下する権限がありません。");
        }

        amendment.setStatus(AmendmentStatus.REJECTED);
        Amendment saved = amendmentRepository.save(amendment);

        if (!amendment.getUser().getId().equals(approver.getId())) {
            BillNotification notification = new BillNotification();
            notification.setReceiver(amendment.getUser());
            notification.setSender(approver);
            notification.setType(BillNotificationType.AMENDMENT_REJECTED);
            notification.setBill(amendment.getBill());
            notificationsRepository.save(notification);
        }

        return saved;
    }

    // 「承認済み」の修正案一覧（一般ユーザー向け、投票対象として表示するもの）
    @Transactional(readOnly = true)
    public List<Amendment> getApprovedAmendmentsByBillId(Long billId) {
        return amendmentRepository.findByBillIdAndStatusOrderByCreatedAtDesc(billId, AmendmentStatus.APPROVED);
    }

    // 「承認待ち」の修正案一覧（法案提出者向け、承認/却下操作をするためのもの）
    @Transactional(readOnly = true)
    public List<Amendment> getPendingAmendmentsByBillId(Long billId) {
        return amendmentRepository.findByBillIdAndStatusOrderByCreatedAtDesc(billId, AmendmentStatus.PENDING);
    }
}
```

## 11. AmendmentController.java(修正:承認・却下エンドポイント、前回提示のまま)

```java
package com.example.demo.controller;

import com.example.demo.form.AmendmentForm;
import com.example.demo.model.Amendment;
import com.example.demo.model.User;
import com.example.demo.service.AmendmentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AmendmentController {

    private final AmendmentService amendmentService;

    @PostMapping("/amendments/create")
    public String createAmendment(
            @Valid @ModelAttribute("amendmentForm") AmendmentForm form,
            BindingResult bindingResult,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("loginUser");
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("amendmentError", "入力内容に不備があります。タイトルと内容を確認してください。");
            return "redirect:/bills/" + form.getBillId();
        }

        try {
            Amendment saved = amendmentService.createAmendment(form, currentUser);
            String message = saved.getStatus() == Amendment.AmendmentStatus.APPROVED
                ? "修正案を提出しました。"
                : "修正案を提出しました。提出者の承認をお待ちください。";
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("amendmentError", "修正案の提出に失敗しました: " + e.getMessage());
        }

        return "redirect:/bills/" + form.getBillId();
    }

    @PostMapping("/amendments/{id}/approve")
    public String approveAmendment(
            @PathVariable("id") Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("loginUser");
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            Amendment amendment = amendmentService.approveAmendment(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "修正案を承認しました。");
            return "redirect:/bills/" + amendment.getBill().getId();
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("amendmentError", e.getMessage());
            return "redirect:/bills";
        }
    }

    @PostMapping("/amendments/{id}/reject")
    public String rejectAmendment(
            @PathVariable("id") Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("loginUser");
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            Amendment amendment = amendmentService.rejectAmendment(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "修正案を却下しました。");
            return "redirect:/bills/" + amendment.getBill().getId();
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("amendmentError", e.getMessage());
            return "redirect:/bills";
        }
    }
}
```

## 12. BillController(修正:投票エンドポイントの追加が必要)

現状の`BillController`には、Voteのトグル(★/☆)を処理するエンドポイントが見当たりませんでした(JS側から`/bills/{id}/vote`のようなAPIを叩いていると思われますが、そのControllerメソッドは今回のファイル一覧にありませんでした)。既存のVoteトグル処理を置き換える形で、以下のような賛否投票エンドポイントが必要になります。

```java
@PostMapping("/bills/{id}/vote")
public String voteOnBill(
        @PathVariable("id") Long id,
        @RequestParam("choice") VoteChoice choice,
        HttpSession session) {

    User loginUser = (User) session.getAttribute("loginUser");
    if (loginUser == null) {
        return "redirect:/login";
    }

    voteService.voteOnBill(id, loginUser.getId(), choice);
    return "redirect:/bills/" + id;
}
```

(既存の投票ボタンのJS/HTMLがどう実装されているか分からなかったので、`Bill`の投票ボタン(★/☆)を処理している既存のController/JSファイルを見せていただけますか? 賛成/反対の2択に変えるにあたって、UIとエンドポイントを揃えて設計したいです。)

---

## 次のステップ

ここまでで「投票データを賛否込みで貯める基盤」が整いました。次に必要になるのは、

1. **採決判定ロジック**(閾値は今後検討とのことなので、`Bill.status`を更新する仕組みの骨格だけ用意しておくか、一旦保留にするか)
2. **クラスタリング処理自体**(`VoteRepository.findVoteRecordsByBillIds` + `AmendmentVoteRepository.findVoteRecordsByAmendmentIds`から投票行列を作り、実際にクラスタリングアルゴリズムをどう組み込むか)

のどちらから着手したいですか? また、既存のBill投票ボタン(★/☆)まわりの実装ファイル(Controller・JS)があれば、そちらも合わせて共有いただけると、賛成/反対のUI設計まで一緒に詰められます。