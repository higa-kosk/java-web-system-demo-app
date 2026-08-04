package com.example.demo.controller;

import com.example.demo.form.AmendmentForm;
import com.example.demo.model.Amendment;
import com.example.demo.model.User;
import com.example.demo.model.Amendment.AmendmentStatus;
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

	/**
	 * 修正案の投稿処理
	 */
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

		// 入力エラーがある場合は、エラーメッセージを保持して元の法案詳細画面へリダイレクト
		if (bindingResult.hasErrors()) {
			redirectAttributes.addFlashAttribute("amendmentError", "入力内容に不備があります。タイトルと内容を確認してください。");
			return "redirect:/bills/" + form.getBillId();
		}

		try {
			Amendment saved = amendmentService.createAmendment(form, currentUser);
			String message = saved.getStatus() == AmendmentStatus.APPROVED
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
