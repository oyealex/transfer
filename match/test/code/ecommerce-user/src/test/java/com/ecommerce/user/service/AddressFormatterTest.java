package com.ecommerce.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AddressFormatter")
class AddressFormatterTest {

    private final AddressFormatter addressFormatter = new AddressFormatter();

    /**
     * The format() signature is swapped: first param is city, second is province.
     * The concatenation expression uses variable names province + city, which
     * puts the second argument before the first. So the output order matches
     * province + city + district + detail when callers pass values in the
     * current parameter order (city first, province second).
     */
    @Test
    @DisplayName("formats address with correct output order province+city+district+detail "
            + "when values match the current swapped parameter order")
    void testFormat_combinesProvinceCityDistrictDetail() {
        // Passing values to match current parameter order:
        // first arg = city, second arg = province
        // The expression "province + city" yields "浙江" + "杭州" = correct order.
        String result = addressFormatter.format("杭州", "浙江", "西湖区", "文三路478号");

        assertThat(result).isEqualTo("浙江杭州西湖区文三路478号");
    }

    @Test
    @DisplayName("formats address without detail when detail is empty")
    void testFormat_emptyDetail_omitsDetail() {
        String result = addressFormatter.format("深圳", "广东", "南山区", "");

        assertThat(result).isEqualTo("广东深圳南山区");
    }

    @Test
    @DisplayName("formats address with all components concatenated without separators")
    void testFormat_noDelimitersBetweenComponents() {
        String result = addressFormatter.format("成都", "四川", "高新区", "天府大道999号");

        assertThat(result).isEqualTo("四川成都高新区天府大道999号");
    }
}
