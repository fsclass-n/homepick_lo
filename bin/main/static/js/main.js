document.addEventListener("DOMContentLoaded", () => {
    // 1. 초기 페이드인 등장 관찰 (IntersectionObserver)
    const observer = new IntersectionObserver((entries, obs) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add("show");
                obs.unobserve(entry.target);
            }
        });
    }, { threshold: 0.15 });

    document.querySelectorAll(".scroll-fade").forEach(el => observer.observe(el));

    // 2. 배경 이미지 & 사진 카드 다중 패럴랙스
    const section = document.getElementById("section-3");
    const parallaxCards = document.querySelectorAll(".parallax-card");
    const parallaxBg = document.querySelector(".ai-parallax-bg-img");

    let isTicking = false;

    function handleParallax() {
        const rect = section.getBoundingClientRect();
        const windowHeight = window.innerHeight;

        // 섹션이 화면에 노출 중일 때만 계산
        if (rect.top <= windowHeight && rect.bottom >= 0) {
            const offset = rect.top - (windowHeight / 2);

            // [A] 배경 이미지 패럴랙스 이동
            if (parallaxBg) {
                parallaxBg.style.transform = `scale(1.2) translate3d(0, ${offset * 0.12}px, 0)`;
            }

            // [B] 카드 패럴랙스 이동 + 고유 회전각 유지
            parallaxCards.forEach(card => {
                const speed = parseFloat(card.dataset.speed) || 0;
                const baseDeg = getComputedStyle(card).getPropertyValue('--base-deg') || '0deg';
                const moveY = offset * speed;

                card.style.transform = `translate3d(0, ${moveY}px, 0) rotate(${baseDeg})`;
            });
        }
        isTicking = false;
    }

    window.addEventListener("scroll", () => {
        if (!isTicking) {
            window.requestAnimationFrame(handleParallax);
            isTicking = true;
        }
    }, { passive: true });
});