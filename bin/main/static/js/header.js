const header = document.querySelector("#header");
const subBg = document.querySelector(".sub-bg");
const btnHamburger = document.querySelector('.btn-hamburger');
const gnb = document.querySelector('.gnb');
const menuOverlay = document.querySelector('.menu-overlay');
const depth2List = document.querySelectorAll(".depth2");
const depth1Links = document.querySelectorAll('.depth1 > li > a');
const depth1Items = document.querySelectorAll('.depth1 > li');

header.addEventListener("mouseenter", function () {
  if (window.innerWidth > 991) {
    if (subBg) subBg.classList.add("active");
    depth2List.forEach(function (depth2) {
      depth2.classList.add("active");
    });
  }
});

header.addEventListener("mouseleave", function () {
  if (window.innerWidth > 991) {
    if (subBg) subBg.classList.remove("active");
    depth2List.forEach(function (depth2) {
      depth2.classList.remove("active");
    });
  }
});

btnHamburger.addEventListener('click', function(){
  // 햄버거 버튼 하나가 열기/닫기 토글 역할을 하도록 수정
  if (gnb.classList.contains('active')) {
    closeMobileMenu();
  } else {
    gnb.classList.add('active');
    menuOverlay.classList.add('active');
    btnHamburger.classList.add('active'); // 햄버거 버튼 애니메이션 작동
  }
});

menuOverlay.addEventListener('click', closeMobileMenu);

function closeMobileMenu(){
  gnb.classList.remove('active');
  menuOverlay.classList.remove('active');
  btnHamburger.classList.remove('active'); // 햄버거 버튼 X에서 원상복구
  
  // 모바일 메뉴 닫기 시 열려있던 모든 아코디언 서브메뉴 초기화 (닫힘 상태로 변경)
  depth1Items.forEach(function(li){
    li.classList.remove('open');
  });
  depth2List.forEach(function(depth2){
    depth2.classList.remove('active');
  });
}

depth1Links.forEach(function(link){
  link.addEventListener('click', function(e){
    if (window.innerWidth <= 991) {
      const subMenu = this.nextElementSibling;

      if (subMenu && subMenu.classList.contains('depth2')) {
        e.preventDefault();
        const parentLi = this.parentElement;

        // 다른 모든 열린 메뉴를 닫아주는 아코디언 기능 추가
        const isOpen = parentLi.classList.contains('open');
        depth1Items.forEach(function(li){
          li.classList.remove('open');
        });
        depth2List.forEach(function(depth2){
          depth2.classList.remove('active');
        });

        // 클릭한 메뉴가 원래 닫혀있었다면 열어줌 (열려있었다면 위에서 모두 닫혔으므로 토글 효과 완성)
        if (!isOpen) {
          parentLi.classList.add('open');
          subMenu.classList.add('active');
        }
      }
    }
  });
});

window.addEventListener('resize', function(){
  if (window.innerWidth > 991) {
    closeMobileMenu();
  }
});