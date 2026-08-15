package com.example.demo.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class BillForm {

	@NotBlank(message = "タイトルを入力してください")
	@Size(max = 100, message = "タイトルは100文字以内で入力してください")
	private String title;

	@NotBlank(message = "本文を入力してください")
	private String description;

	// 画面の<select>で選択された委員会のID
	@NotNull(message = "委員会を選択してください")
	private Long committeeId;

	// 採決予定日時（提案者が7日以上を選択可能）
	@NotNull(message = "採決予定日時を設定してください")
	private LocalDateTime votingDeadline;
}
