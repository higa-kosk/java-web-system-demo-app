package com.example.demo.controller;

import com.example.demo.repository.LikeRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.model.Comment;
import com.example.demo.form.CommentForm;
import com.example.demo.model.Amendment;
import com.example.demo.model.Bill;
import com.example.demo.model.BillNotification;
import com.example.demo.model.User;
import com.example.demo.model.Tag;
import com.example.demo.form.AmendmentForm;
import com.example.demo.form.BillForm;
import com.example.demo.model.Committee;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.BillRepository;
import com.example.demo.repository.TagRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VoteRepository;
import com.example.demo.repository.NotificationsRepository;
import com.example.demo.repository.CommitteeRepository;
import com.example.demo.service.AmendmentService;
import com.example.demo.service.BillService;

@Controller
@RequiredArgsConstructor // これを書くことでconstructor(this.xx = xx)を書かなくて済む
public class BillController {

	private final LikeRepository likeRepository;
	private final BillRepository billRepository;
	private final UserRepository userRepository;
	private final CommentRepository commentRepository;
	private final TagRepository tagRepository;
	private final VoteRepository voteRepository;
	private final NotificationsRepository notificationsRepository;
	private final CommitteeRepository committeeRepository;
	private final BillService billService;
	private final AmendmentService amendmentService;

	// 投稿一覧を表示する窓口
	@GetMapping("/bills")
	public String billList(
			@RequestParam(name = "keyword", required = false) String keyword,
			HttpSession session,
			HttpServletResponse response,
			Model model) {

		// ブラウザにキャッシュさせずに、チャット画面からブラウザで戻った時に必ずサーバーを叩かせる
		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setDateHeader("Expires", 0);	

		// セッションからユーザー情報を取得
		User sessionUser = (User) session.getAttribute("loginUser");

		// ログインしている場合のみ、最新のユーザー情報をDBから取得してモデルに渡す
		if (sessionUser != null) {
			userRepository.findById(sessionUser.getId())
					.ifPresent(currentUser -> {
						model.addAttribute("loginUser", currentUser);

						// 全体に表示する投稿一覧を取得（これはログイン有無に関係なく実行）
						List<Bill> bills;

						if (keyword != null && !keyword.trim().isEmpty()) {
							bills = billRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword);
							model.addAttribute("keyword", keyword); // 画面にキーワードを保持させる
						} else {
							// キーワードがなければ、今まで通り全件を最新順で取得
							bills = billRepository.findAllByOrderByCreatedAtDesc();
						}

						// Vote-2: 各投稿にいいね数と、Vote数及び自分がVoteしたかの情報を付与する
						for (Bill bill : bills) {

							// 1-1. 総いいね数をカウントしてセット
							bill.setLikeCount(likeRepository.countByBill(bill));

							// 1-2. ログイン中の場合、自分がいいねしたかを判定してセット
							if (currentUser != null) {
								bill.setLikedByMe(likeRepository.existsByUserAndBill(currentUser, bill));
							} else {
								bill.setLikedByMe(false); // 念のためユーザーがいない場合は一律false
							}

							// 総Vote数をカウントしてセット
							bill.setVoteCount(voteRepository.countByBill(bill));

							// ログイン中の場合、自分がVoteしたかを判定してセット
							if (currentUser != null) {
								bill.setVotedByMe(voteRepository.existsByUserAndBill(currentUser, bill));
							} else {
								bill.setVotedByMe(false); // 念のためユーザーがいない場合は一律false
							}
						}
						model.addAttribute("bills", bills);
					});
		} else {
			// 未ログインの場合は、画面側で制御できるように null を明示するか、ログイン状態をfalseにする
			model.addAttribute("loginUser", null);
		}

		// 全体に表示する投稿一覧を取得（これはログイン有無に関係なく実行）
		List<Bill> bills;

		if (keyword != null && !keyword.trim().isEmpty()) {
			bills = billRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword);
			model.addAttribute("keyword", keyword); // 画面にキーワードを保持させる
		} else {
			// キーワードがなければ、今まで通り全件を最新順で取得
			bills = billRepository.findAllByOrderByCreatedAtDesc();
		}

		// 現在のタブの初期値をallにしておく
		model.addAttribute("currentTab", "all");

		// トレンド上位5件を画面に渡す
		model.addAttribute("trends", tagRepository.findTop5Trends());

		return "bill_list";
	}

	// 投稿フォーム画面を表示する
	@GetMapping("/bills/new")
	public String showNewBillForm(Model model) {

		// エンティティではなくFromオブジェクトを画面に渡す
		model.addAttribute("billForm", new BillForm());

		// 【超重要】提出先の委員会を選べるように、全委員会リストを画面に渡す
		model.addAttribute("committees", committeeRepository.findAll());

		// データベースからすべてのタグを取得して、画面に渡す
		List<Tag> allTags = tagRepository.findAll();
		model.addAttribute("allTags", allTags);

		return "bill_form";
	}

	// 投稿をデータベースに保存する
	@PostMapping("/bills/create")
	public String createBill(
			@Valid @ModelAttribute("billForm") BillForm billForm,
			BindingResult bindingResult,
			HttpSession session,
			Model model) {

		// セッションからログインユーザーを取得
		User currentUser = (User) session.getAttribute("loginUser");
		if (currentUser == null) {
			return "redirect:/login";
		}

		// バリデーションエラーがあれば元の画面に戻す
		if (bindingResult.hasErrors()) {
			// エラーで戻った時も、タグ一覧を再セットしてあげる
			model.addAttribute("allTags", tagRepository.findAll());

			// エラー時も、委員会リストを再セット
			model.addAttribute("committees", committeeRepository.findAll());

			return "bill_form";
		}

		// Form から Entity への詰め替え
		Bill bill = new Bill();
		bill.setTitle(billForm.getTitle());
		bill.setDescription(billForm.getDescription());
		bill.setUser(currentUser);

		// 選択されたIDからCommitteeを取得してセット
		Committee committee = committeeRepository.findById(billForm.getCommitteeId())
			.orElseThrow(() -> new IllegalArgumentException("無効な委員会IDです: " + billForm.getCommitteeId()));
		bill.setCommittee(committee);

		// MARK: ハッシュタグ抽出+bill自体の保存をbillServiceで実行
		billService.createBill(bill);

		return "redirect:/bills";
	}

	// 投稿を削除する
	@PostMapping("/bills/{id}/delete")
	public String deleteBill(@PathVariable("id") Long id, HttpSession session) {

		// ログインしているかチェック
        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

		// URLから受け取ったIDを使って、データベースから削除する
		billRepository.deleteById(id);

		return "redirect:/bills";
	}

	// 特定のユーザーの投稿一覧を表示するルート
	@GetMapping("/bills/user/{userId}")
	public String userBillList(
			@PathVariable("userId") Long userId,
			HttpSession session,
			Model model) {

		// 1. 指定されたユーザーIDの投稿だけを最新順で取得
		List<Bill> bills = billRepository.findByUserIdOrderByCreatedAtDesc(userId);

		// 2. セッションからユーザー情報を取得
		User sessionUser = (User) session.getAttribute("loginUser");

		// タイムラインと同じ構造：ログインしている場合のみ最新のユーザー情報をDBから取得してモデルに渡す
		if (sessionUser != null) {

			userRepository.findById(sessionUser.getId())
				.ifPresent(currentUser -> {
					model.addAttribute("loginUser", currentUser);

					// 各投稿にいいね・Vote情報を付与
					for (Bill bill : bills) {
						bill.setLikeCount(likeRepository.countByBill(bill));
						bill.setLikedByMe(likeRepository.existsByUserAndBill(currentUser, bill));

						bill.setVoteCount(voteRepository.countByBill(bill));
						bill.setVotedByMe(voteRepository.existsByUserAndBill(currentUser, bill));
					}
				});
		} else {

			// 未ログインの場合はnullを明示し、投稿の自分のアクションフラグを一律falseにする
			model.addAttribute("loginUser", null);
			for (Bill bill : bills) {
				bill.setLikeCount(likeRepository.countByBill(bill));
				bill.setLikedByMe(false);
				bill.setVoteCount(voteRepository.countByBill(bill));
				bill.setVotedByMe(false);
			}
		}

		// 2. 画面のタイトル等に表示するために、そのユーザーの名前も取得（任意）
		if (!bills.isEmpty()) {
			model.addAttribute("targetUser", bills.get(0).getUser());
		} else {
			// 投稿が空の場合でも動くように、ユーザー自身を直接取得してモデルに入れると安全
			model.addAttribute("targetUser", userRepository.findById(userId).orElse(null));
		}

		model.addAttribute("bills", bills);
		return "user_bill_list";
	}

	// フォローしているユーザーの投稿だけを表示するタイムライン
	@GetMapping("/bills/following")
	public String followingBillList(HttpSession session, Model model) {

		// 1. セッションからログインユーザーを取得
		User me = (User) session.getAttribute("loginUser");
		if (me == null) {
			return "redirect:/login";
		}

		// 常に最新の状態のフォローリストを参照する為、念の為DBから引き直す
		me = userRepository.findById(me.getId()).orElse(me);

		// 2. 自分がフォローしているユーザーの「IDのリスト」を作る
		List<Long> followingUserIds = me.getFollowing().stream()
				.map(User::getId)
				.toList();

		List<Bill> bills;
		if (followingUserIds.isEmpty()) {
			// まだ誰もフォローしていない場合は、からのリストを返す（エラー回避）
			bills = new java.util.ArrayList<>();
		} else {
			// 3. フォローしている人のIDリストをリポジトリに渡して、投稿を取得する
			bills = billRepository.findByUserIdInOrderByCreatedAtDesc(followingUserIds);
		}

		// 4. 画面にデータを渡す
		model.addAttribute("bills", bills);
		model.addAttribute("loginUser", me);
		model.addAttribute("currentTab", "following"); // 今どっちのタブにいるかを判定するためのフラグ

		// トレンド上位5件を画面に渡す
		model.addAttribute("trends", tagRepository.findTop5Trends());

		return "bill_list"; // 画面は新しく作らず、既存の bill_list.html を使いまわす。
	}

	// 特定の投稿の詳細画面を表示する
	@GetMapping("/bills/{id}")
	public String billDetail(
			@PathVariable("id") Long id,
			HttpSession session,
			Model model) {

		// 1. 対象の投稿を取得
		Bill bill = billRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("指定された法案が見つかりません:" + id));

		// セッションからログインユーザーを取得（最新情報をDBから引き直す）
		User sessionUser = (User) session.getAttribute("loginUser");
		User currentUser = null;
		if (sessionUser != null) {
			currentUser = userRepository.findById(sessionUser.getId()).orElse(null);
		}

		// 取得した1件の投稿にいいね数と投票数の情報を詰め込む
		bill.setLikeCount(likeRepository.countByBill(bill));
		bill.setVoteCount(voteRepository.countByBill(bill));

		if (currentUser != null) {
			bill.setLikedByMe(likeRepository.existsByUserAndBill(currentUser, bill));
			bill.setVotedByMe(voteRepository.existsByUserAndBill(currentUser, bill));
			model.addAttribute("loginUser", currentUser); // 常に最新のユーザーを渡す
		} else {
			bill.setLikedByMe(false);
			bill.setVotedByMe(false);
			model.addAttribute("loginUser", null);
		}

		// 法案に紐づく修正案一覧を追加
		List<Amendment> amendments = amendmentService.getAmendmentsByBillId(id);
		model.addAttribute("amendments", amendments);

		// 修正案投稿フォーム用の空オブジェクトを追加
		if (!model.containsAttribute("amendmentForm")) {
			AmendmentForm amendmentForm = new AmendmentForm();
			amendmentForm.setBillId(id);
			model.addAttribute("amendmentForm", amendmentForm);
		}

		// 2. 画面にデータを渡す
		model.addAttribute("bill", bill);
		// Bill.java に @OneToMany を書いたので、JPAが自動で紐づくコメントを一緒に持ってきてくれる
		model.addAttribute("comments", bill.getComments());

		// HTMLのth:object="{commentForm}"を受け止める為に空のオブジェクトを必ず渡す
		model.addAttribute("commentForm", new CommentForm());

		model.addAttribute("trends", tagRepository.findTop5Trends());

		return "bill_detail";
	}

	// 法案に対して意見（コメント）を投稿する
	@PostMapping("/bills/{id}/comments")
	public String createComment(
			@PathVariable("id") Long id,
			@Valid CommentForm commentForm, // Validアノテーションで読み込む
			BindingResult bindingResult, // これで上記と関連してバリデーション結果を受け取る
			HttpSession session,
			Model model) {				// エラー時に画面を再構成する為Modelを追加

		// 1. セッションからは「箱」としてユーザーを取得
		User sessionUser = (User) session.getAttribute("loginUser");
		// セッション自体が空、またはDBから最新のユーザーが取得できない場合はログインへ
		if (sessionUser == null) {
			return "redirect:/login";
		}

		// 最新のユーザー情報をDBから引き直す（Lazy/セッション不整合対策）
		User me = userRepository.findById(sessionUser.getId())
			.orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));

		// 2. どの投稿に対するコメントか、親を取得
		Bill bill = billRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("法案が見つかりません"));

		// バリデーションエラー（空欄や文字数超過）がある場合の処理
		if (bindingResult.hasErrors()) {
			// 詳細画面を再表示するために、billDetailと同じデータを詰め直す
			bill.setLikeCount(likeRepository.countByBill(bill));
			bill.setVoteCount(voteRepository.countByBill(bill));
			bill.setLikedByMe(likeRepository.existsByUserAndBill(me, bill));
			bill.setVotedByMe(voteRepository.existsByUserAndBill(me, bill));

			model.addAttribute("bill", bill);
			model.addAttribute("loginUser", me);
			model.addAttribute("trends", tagRepository.findTop5Trends());

			return "bill_detail"; // リダイレクトではなく、エラーを持ったまま詳細画面のHTMLを表示
		}

		// 3. コメントオブジェクトを生成してデータをセット
		Comment comment = new Comment();
		comment.setContent(commentForm.getContent()); // フォームから値を取得
		comment.setBill(bill);
		comment.setUser(me); // 最新のユーザーオブジェクトを紐づける
		comment.setQuestion(commentForm.isQuestion()); // 質疑通告フラグ

		// 返信（答弁）の場合の処理
		if (commentForm.getParentId() != null) {
			Comment parentComment = commentRepository.findById(commentForm.getParentId())
				.orElse(null);

			if (parentComment != null) {
				comment.setParent(parentComment);

				// 提案者自身が質疑に返信（答弁）した場合は、親コメントを「答弁済み」にする
				if (parentComment.isQuestion() && bill.getUser().getId().equals(me.getId())) {
					parentComment.setAnswered(true);
					commentRepository.save(parentComment);
				}
			}
		}

		// 4. データベースに保存
		commentRepository.save(comment);

		// 5. 通知機能：自作自演（自分の提案に自分でコメント）でなければ、提案者宛にコメント通知を作成して保存
		if (!bill.getUser().getId().equals(me.getId())) {
			BillNotification notification = new BillNotification();
			notification.setType(BillNotification.BillNotificationType.COMMENT);
			notification.setSender(me);
			notification.setReceiver(bill.getUser());
			notification.setBill(bill);

			notificationsRepository.save(notification);
		}

		// 6. 書き込みが終わったら、元の詳細画面にリダイレクトで戻る
		return "redirect:/bills/" + id;
	}

	// タグに基づく提案を取得して画面に渡す処理
	@GetMapping("/tags/{tagName}")
	public String showBillsByTag(@PathVariable("tagName") String tagName, Model model) {
		// 1. 指定されたタグ名がついている投稿だけをリポジトリから取得
		List<Bill> taggedBills = billRepository.findByTagsName(tagName);

		// 2. タイムライン（bill_list.html）と同じ変数名「bills」で画面に渡す
		model.addAttribute("bills", taggedBills);

		// 3. 今何のタグで絞り込んでいるかを画面に表示するために、タグ名も渡しておく
		model.addAttribute("currentTag", tagName);

		// 4. 新しい画面を作らず、既存の「bill_list.html」をそのまま使いまわす

		// 割り込み：トレンド上位5件を画面に渡す
		model.addAttribute("trends", tagRepository.findTop5Trends());

		return "bill_list";
	}
}