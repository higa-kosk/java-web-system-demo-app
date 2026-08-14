package com.example.demo.controller.async;

import com.example.demo.controller.async.VoteController.VoteRequest;
import com.example.demo.model.User;
import com.example.demo.model.VoteChoice;
import com.example.demo.service.VoteService;
import com.example.demo.service.VoteService.VoteResult;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/amendments/{amendmentId}/vote")
@RequiredArgsConstructor
public class AmendmentVoteController {

	private final VoteService voteService;

	@PostMapping
	public ResponseEntity<?> vote(
		@PathVariable("amendmentId") Long amendmentId,
		@RequestBody VoteRequest request,
		HttpSession session) {

		// セッションからログインユーザーを取得
		User currentUser = (User) session.getAttribute("loginUser");
		if (currentUser == null) {
			// APIでデータを返す処理の場合は、セッション切れの際は401エラーを返す
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("ログインが必要です");
		}

		try {
			VoteResult result = voteService.voteOnAmendment(amendmentId, currentUser.getId(), request.getChoice());
			return ResponseEntity.ok(result);

		} catch (IllegalStateException e) {
			// 修正案が承認待ちなど、業務ルール違反
			return ResponseEntity.badRequest().body(e.getMessage());

		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	// リクエストボディ（賛成/反対の選択を受け取るための入れ物）
    public static class VoteRequest {
        private VoteChoice choice;

        public VoteChoice getChoice() {
            return choice;
        }

        public void setChoice(VoteChoice choice) {
            this.choice = choice;
        }
    }
}