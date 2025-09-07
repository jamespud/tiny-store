package com.github.spud.tinystore.order.domain.model;

/**
 * 地址
 *
 * @param name       收件人姓名
 * @param phone      收件人电话
 * @param detail     详细地址
 * @param street     街道
 * @param city       城市
 * @param state      省份
 * @param postalCode 邮编
 * @param country    国家
 */
public record Address(String name, String phone, String detail, String street, String city,
                      String state,
                      String postalCode,
                      String country) {

}
