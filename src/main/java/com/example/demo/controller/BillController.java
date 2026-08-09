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
import com.example.demo.service.BillEngagementService;
import com.example.demo.service.BillService;
import com.example.demo.service.TagService;

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
	private final AmendmentService amendmentService;
	private final BillService billService;
	private final BillEngagementService billEngagementService;
	private final TagService tagService;

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
		User currentUser = null;

		if (sessionUser != null) {
			currentUser = userRepository.findById(sessionUser.getId()).orElseGet(null);
		}
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

		billEngagementService.attachEngagementInfo(bills, currentUser);

		model.addAttribute("bills", bills);

		// 現在のタブの初期値をallにしておく
		model.addAttribute("currentTab", "all");

		// トレンド上位5件を画面に渡す
		model.addAttribute("trends", tagService.getTop5Trends());

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
		User currentUser = null;
		if (sessionUser != null) {
			currentUser = userRepository.findById(sessionUser.getId()).orElse(null);
		}
		model.addAttribute("loginUser", currentUser);

		billEngagementService.attachEngagementInfo(bills, currentUser);

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
		model.addAttribute("trends", tagService.getTop5Trends());

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

		// 単体のBillに、いいね・賛否投票の情報をまとめて付与する
		billEngagementService.attachEngagementInfo(bill, currentUser);
		model.addAttribute("loginUser", currentUser);
		
		// 「承認済み」の修正案一覧（誰でも見られる、投票対象の物）
		List<Amendment> approvedAmendments = amendmentService.getApprovedAmendmentsByBillId(id);
		model.addAttribute("amendments", approvedAmendments);

		// 法案提出者にだけ「承認待ち」の修正案一覧を渡す
		if (currentUser != null && bill.getUser().getId().equals(currentUser.getId())) {
			List<Amendment> pendingAmendments = amendmentService.getPendingAmendmentsByBillId(id);
			model.addAttribute("pendingAmendments", pendingAmendments);
		}

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

		model.addAttribute("trends", tagService.getTop5Trends());

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

		// もしフォームに不備がある場合は、表示に必要な情報を注入して法案詳細画面を表示する
		if (bindingResult.hasErrors()) {
			Bill bill = billRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("法案が見つかりません"));
			billEngagementService.attachEngagementInfo(bill, me);

			// エラー時もamendmentsが必要（bill_detail.htmlの#lists.size(amendments)の為）
			model.addAttribute("amendments", amendmentService.getApprovedAmendmentsByBillId(id));
			if (bill.getUser().getId().equals(me.getId())) {
				model.addAttribute("pendingAmendments", amendmentService.getPendingAmendmentsByBillId(id));
			}

			model.addAttribute("bill", bill);
			model.addAttribute("loginUser", me);
			model.addAttribute("trends", tagService.getTop5Trends());

			return "bill_detail";
		}

		// フォームに不備が無ければ、BillServiceでコメント投稿（保存等）処理を行う
		billService.postComment(id, me, commentForm);

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
		model.addAttribute("trends", tagService.getTop5Trends());

		return "bill_list";
	}
}