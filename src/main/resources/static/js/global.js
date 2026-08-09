document.addEventListener("DOMContentLoaded", function () {

	// =====================
	// Voteボタンの制御用
	// =====================
	function showWarningToast(message) {
		const container = document.getElementById('toast-container');
		const toast = document.createElement('div');
		toast.classList.add('toast-body');
		toast.textContent = message;
		container.appendChild(toast);
		setTimeout(() => toast.remove(), 2500);
	}

	document.querySelectorAll('[data-is-own="true"]').forEach(btn => btn.classList.add('own-post-btn'));

	// =====================
	// ハッシュタグ自動リンク処理
	// =====================
	const postElements = document.querySelectorAll('.post-content-text p');
	postElements.forEach(function (element) {
		const text = element.textContent;
		const hashtagRegex = /[#＃][A-Za-z0-9ぁ-んァ-ヶ一-龠ー_]+/g;
		if (!hashtagRegex.test(text)) return;

		element.innerHTML = text.replace(hashtagRegex, function (match) {
			const tagName = match.substring(1).toLowerCase();
			return `<a href="/tags/${tagName}" class="hashtag-text">${match}</a>`;
		});
	});

	// ======================
	// いいねボタンの非同期処理
	// ======================
	document.querySelectorAll('.like-button').forEach(button => {
		button.addEventListener('click', async (e) => {
			const btn = e.currentTarget;
			const billId = btn.getAttribute('data-bill-id');
			btn.disabled = true;

			try {
				// 新設した LikeApiController を叩く
				const response = await fetch(`/api/bills/${billId}/like`, {
					method: 'POST'
				});

				if (response.ok) {
					const result = await response.json(); // { likes: X, liked: true/false }

					const countSpan = btn.querySelector('.like-count');
					const iconSpan = btn.querySelector('.like-icon');

					// 数字を最近に書き換え
					countSpan.textContent = result.likes;

					// 状態に応じてハートとクラスをトグル
					if (result.liked) {
						iconSpan.textContent = '❤️';
						btn.classList.add('liked');
					} else {
						iconSpan.textContent = '🖤';
						btn.classList.remove('liked');
					}
				}
			} catch (error) {
				console.error('いいねの非同期通信に失敗しました:', error);
			} finally {
				btn.disabled = false;
			}
		});
	});

	// =====================
	// Voteボタンの非同期処理
	// =====================
	document.querySelectorAll('.vote-button').forEach(button => {
		button.addEventListener('click', async (e) => {
			const btn = e.currentTarget;

			// 自分の投稿なら通信させずにトーストを出して終了
			if (btn.getAttribute('data-is-own') === 'true') {
				showWarningToast('🏛️ 自身の提出法案に「投票」することはできません');
				return;
			}

			const billId = btn.getAttribute('data-bill-id');
			const choice = btn.getAttribute('data-choice'); // "YEA" または "NAY"
			const actionsArea = btn.closest('.post-actions');
			btn.disabled = true;

			try {
				const response = await fetch(`/api/bills/${billId}/vote`, {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({ choice: choice })
				});

				if (response.ok) {
					const result = await response.json(); // { myChoice, yeaCount, nayCount }

					const yeaBtn = actionsArea.querySelector('.vote-button.yea');
					const nayBtn = actionsArea.querySelector('.vote-button.nay');

					yeaBtn.querySelector('.vote-count').textContent = result.yeaCount;
					nayBtn.querySelector('.vote-count').textContent = result.nayCount;

					yeaBtn.classList.toggle('voted', result.myChoice === 'YEA');
					nayBtn.classList.toggle('voted', result.myChoice === 'NAY');
				} else {
					const message = await response.text();
					showWarningToast(message || '投票に失敗しました');
				}
			} catch (error) {
				console.error('Voteの非同期通信に失敗しました:', error);
			} finally {
				btn.disabled = false;
			}
		});
	});

	// =====================
	// SP時：画面最下部付近でボトムバーを下に隠す処理
	// =====================
	const sidebar = document.getElementById('sidebar');

	if (sidebar) {
		let isTicking = false;

		const handleScroll = () => {
			// 768px以下のモバイル表示時のみ発動
			if (window.innerWidth <= 768) {
				// 画面全体の高さ、現在のスクロール位置、表示領域の高さを取得
				const documentHeight = document.documentElement.scrollHeight;
				const windowHeight = window.innerHeight;
				const scrollTop = window.scrollY || document.documentElement.scrollTop;

				// ページの最下部までの残りの距離（px）
				const distanceToBottom = documentHeight - (scrollTop + windowHeight);

				// 残り距離が80px以下になったら隠す
				if (distanceToBottom <= 120) {
					sidebar.classList.add('is-hidden');
				} else {
					sidebar.classList.remove('is-hidden');
				}
			} else {
				// PC表示時は常にis-hiddenクラスは外しておく
				sidebar.classList.remove('is-hidden');
			}

			// 後述のタイミングで発火した際にisTickingをfalseにしとかないと、
			// 次のスクロール動作時にhandleScrollが動作しなくなる為、最後に必ず解除（false）にする。
			isTicking = false;
		};

		// スクロールイベントの負荷軽減（requestAnimationFrame）
		window.addEventListener('scroll', () => {
			if (!isTicking) {
				window.requestAnimationFrame(handleScroll);
				isTicking = true;
			}
		});

		window.addEventListener('resize', handleScroll);
		handleScroll();
	}

	// =====================
	// 答弁フォームの表示切り替え処理
	// =====================
	document.querySelectorAll('.reply-toggle-btn').forEach(button => {
		button.addEventListener('click', (e) => {
			const commentId = e.currentTarget.getAttribute('data-comment-id');
			const targetForm = document.getElementById(`reply-form-${commentId}`);

			if (targetForm) {
				if (targetForm.style.display === 'none' || targetForm.style.display === '') {
					targetForm.style.display = 'block';
				} else {
					targetForm.style.display = 'none';
				}
			}
		});
	});
});