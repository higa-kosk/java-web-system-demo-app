package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AvatarStorageService avatarStorageService;

	// 新規登録処理（ID重複チェック＋パスワードハッシュ化をまとめる）
	@Transactional
	public User register(User user) {
		if (user.getId() != null && userRepository.existsById(user.getId())) {
			throw new IllegalArgumentException("エラー：既に存在するID（" + user.getId() + "）が指定されたため処理を中断しました");
		}
		user.setPassword(passwordEncoder.encode(user.getPassword()));
		return userRepository.save(user);
	}

	// プロフィール更新処理（アバター保存を含む）
	@Transactional
	public User updateProfile(User user, MultipartFile avatarFile) {
		if (user.getId() == null || !userRepository.existsById(user.getId())) {
			throw new IllegalArgumentException("エラー：存在しないユーザーの更新はできません。");
		}

		if (avatarFile != null && !avatarFile.isEmpty()) {
			try {
				String avatarUrl = avatarStorageService.saveAvatar(avatarFile, user.getId());
				user.setAvatarUrl(avatarUrl);
			} catch (Exception e) {
				throw new RuntimeException("アバター画像の保存に失敗しました", e);
			}
		} else {
			// アップロードがない場合は既存のURLを引き継ぐ
			userRepository.findById(user.getId())
					.ifPresent(existing -> user.setAvatarUrl(existing.getAvatarUrl()));
		}

		return userRepository.save(user);
	}

	// 退会処理：関連データを一括削除するカスケード処理
	@Transactional
	public void deleteUserCascade(Long id) {
		if (!userRepository.existsById(id)) {
			throw new IllegalArgumentException("エラー：存在しないユーザーは削除できません（ID: " + id + "）");
		}
		userRepository.deleteBillTagsByUserId(id);
		userRepository.deleteFollowRelationsByUserId(id);
		userRepository.deleteById(id);
	}

	// フォローする
	@Transactional
	public User follow(Long meId, Long targetId) {
		User me = userRepository.findById(meId)
				.orElseThrow(() -> new IllegalArgumentException("フォロー元（自分）のユーザーが見つかりません"));
		User target = userRepository.findById(targetId)
				.orElseThrow(() -> new IllegalArgumentException("フォロー対象のユーザーが見つかりません"));
		me.follow(target);
		
		return userRepository.save(me);
	}

	// フォロー解除する
	@Transactional
	public User unfollow(Long meId, Long targetId) {
		User me = userRepository.findById(meId)
				.orElseThrow(() -> new IllegalArgumentException("フォロー元（自分）のユーザーが見つかりません"));
		User target = userRepository.findById(targetId)
				.orElseThrow(() -> new IllegalArgumentException("フォロー対象のユーザーが見つかりません"));
		me.unfollow(target);

		return userRepository.save(me);
	}
}
