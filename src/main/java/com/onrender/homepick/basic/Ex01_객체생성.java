package com.onrender.homepick.basic;

// F5 -> 스프링부트
// F6 -> 자바
// F7 -> 파이썬
// 객체 생성 및 사용
public class Ex01_객체생성 {
    public static void main(String[] args){
        // new 키워드로 Car 객체 생성
        Car myCar = new Car("소나타", 100);

        // 출력: 소나타
        // 객체.속성
        // 객체.메서드()
        // System.out.println(myCar.model);
        System.out.println(myCar.getModel());
    }
}
