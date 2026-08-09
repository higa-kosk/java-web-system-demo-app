package com.example.demo.service;

import com.example.demo.model.Tag;
import com.example.demo.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {
	
	private final TagRepository tagRepository;

	private static final int TREND_DAYS = 3;
	private static final int TREND_LIMIT = 5;

	// 直近3日間で登場回数が多い上位5件のタグを取得する（サイドバーのトレンド表示用）
	@Transactional(readOnly = true)
	public List<Tag> getTop5Trends() {
		LocalDateTime since = LocalDateTime.now().minusDays(TREND_DAYS);
		return tagRepository.findTrendingTags(since).stream()
				.limit(TREND_LIMIT)
				.toList();
	}
}
