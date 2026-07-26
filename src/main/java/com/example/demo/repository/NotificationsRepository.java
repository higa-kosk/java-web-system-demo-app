package com.example.demo.repository;

import com.example.demo.model.Notification;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NotificationsRepository extends JpaRepository<Notification, Long> {
    
    // 特定のユーザー（仮に自分）宛の通知を、最新日時順（降順）で全件取得する
    List<Notification> findByReceiverOrderByCreatedAtDesc(User receriver);

    // 特定のユーザーの「未読（isRead = false」の通知数をカウントする（ヘッダーバッヂ用）
    long countByReceiverAndIsReadFalse(User receiver);

    // 自分（Receiver）宛の通知をIDの降順（新しい順）で全件取得する
    List<Notification> findByReceiverOrderByIdDesc(User receiver);

    // DM一覧用：特定の相手から自分宛の未読メッセージ通知の「数」をカウントする
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.receiver = :receiver AND n.sender = :sender AND n.isRead = false AND TYPE(n) = MessageNotification")
    long countUnreadMessageNotificationsFromPartner(@Param("receiver") User receiver, @Param("sender") User sender);

    // チャット画面用：特定の相手から自分宛の未読メッセージ通知の「実態リスト」を取得する（既読更新用）
    @Query("SELECT n FROM Notification n WHERE n.receiver = :receiver AND n.sender = :sender AND n.isRead = false AND TYPE(n) = MessageNotification")
    List<Notification> findUnreadMessageNotificationsFromPartner(@Param("receiver") User receiver, @Param("sender") User sender);

    // 自分宛の未読メッセージ通知を、送信者（相手）毎に纏めて件数集計する（N+1解消用）
    @Query("SELECT n.sender.id AS senderId, COUNT(n) AS cnt " +
            "FROM Notification n " +
            "WHERE n.receiver = :receiver AND n.sender.id IN :senderIds " +
            "AND n.isRead = false AND TYPE(n) = MessageNotification " +
            "GROUP BY n.sender.id")
    List<SenderCount> countUnreadMessageNotificationsGroupedBySender(
            @Param("receiver") User receiver,
            @Param("senderIds") Collection<Long> senderIds
    );

    // 集計結果（送信者IDと件数のペア）を受け取るためのプロジェクションインターフェース
    interface SenderCount {
        Long getSenderId();
        Long getCnt();
    }
}
