/* ============================================
   cursor.js — Homepick 마우스 커서/트레일 효과
   ============================================ */

document.addEventListener('DOMContentLoaded', function() {
    // 터치 디바이스(모바일/태블릿)에서는 커서 효과 비활성화
    if ('ontouchstart' in window || navigator.maxTouchPoints > 0) {
        return;
    }

    // 커서 요소 동적 생성
    const cursor = document.createElement('div');
    cursor.classList.add('custom-cursor');
    
    const follower = document.createElement('div');
    follower.classList.add('custom-cursor-follower');

    document.body.appendChild(cursor);
    document.body.appendChild(follower);

    let mouseX = 0, mouseY = 0;
    let followerX = 0, followerY = 0;

    // 마우스 이동 이벤트
    document.addEventListener('mousemove', function(e) {
        mouseX = e.clientX;
        mouseY = e.clientY;
        
        // 메인 커서는 즉시 이동
        cursor.style.transform = `translate3d(${mouseX}px, ${mouseY}px, 0)`;
    });

    // 트레일 효과 (부드러운 따라가기)
    function render() {
        followerX += (mouseX - followerX) * 0.15; // 0.15는 따라오는 속도 조절
        followerY += (mouseY - followerY) * 0.15;

        follower.style.transform = `translate3d(${followerX}px, ${followerY}px, 0)`;
        requestAnimationFrame(render);
    }
    render();

    // 호버 애니메이션 타겟 (푸터 내부의 클릭 가능한 요소들)
    const interactives = document.querySelectorAll('.homepick-footer a, .homepick-footer button, .homepick-footer select, .homepick-footer input, .homepick-footer .custom-select-list li, .homepick-footer .footer-family-select');
    
    interactives.forEach(el => {
        el.addEventListener('mouseenter', () => {
            cursor.classList.add('active');
            follower.classList.add('active');
        });
        el.addEventListener('mouseleave', () => {
            cursor.classList.remove('active');
            follower.classList.remove('active');
        });
    });

    // 푸터 영역 진입/이탈 감지
    const footer = document.querySelector('.homepick-footer');
    if (footer) {
        footer.addEventListener('mouseenter', () => {
            cursor.classList.add('visible');
            follower.classList.add('visible');
        });
        footer.addEventListener('mouseleave', () => {
            cursor.classList.remove('visible');
            follower.classList.remove('visible');
        });
    }

    // 문서 밖으로 나갈 때 안전장치
    document.addEventListener('mouseout', function(e) {
        if (!e.relatedTarget && !e.toElement) {
            cursor.classList.remove('visible');
            follower.classList.remove('visible');
        }
    });
});
