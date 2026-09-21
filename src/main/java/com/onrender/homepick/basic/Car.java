package com.onrender.homepick.basic;

import lombok.Getter;

@Getter
public class Car{
    // 인스턴트 필드
    private String model;
    private int speed;

    // 생성자(Contructor) 함수
    public Car(String model, int speed){
        this.model = model;
        this.speed = speed;
    }

    // public String getModel() {
    //     return model;
    // }
    // public String getSpeed() {
    //     return speed;
    // }
}