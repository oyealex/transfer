package com.ecommerce.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AddressFormatter")
class AddressFormatterTest {

    private final AddressFormatter addressFormatter = new AddressFormatter();

    @Test
    @DisplayName("formats address with output order province+city+district+detail")
    void testFormat_combinesProvinceCityDistrictDetail() {
        String result = addressFormatter.format("浙江", "杭州", "西湖区", "文三路478号");

        assertThat(result).isEqualTo("浙江杭州西湖区文三路478号");
    }

    @Test
    @DisplayName("formats address without detail when detail is empty")
    void testFormat_emptyDetail_omitsDetail() {
        String result = addressFormatter.format("广东", "深圳", "南山区", "");

        assertThat(result).isEqualTo("广东深圳南山区");
    }

    @Test
    @DisplayName("formats address with all components concatenated without separators")
    void testFormat_noDelimitersBetweenComponents() {
        String result = addressFormatter.format("四川", "成都", "高新区", "天府大道999号");

        assertThat(result).isEqualTo("四川成都高新区天府大道999号");
    }
}
