package com.ecommerce.user.service;

import org.springframework.stereotype.Service;

/**
 * Formats a Chinese address into a single string.
 */
@Service
public class AddressFormatter {

    /**
     * Formats address components into: province + city + district + detail.
     *
     * @param province the province
     * @param city     the city
     * @param district the district/county
     * @param detail   the street / building / doorplate detail
     * @return the formatted full address string
     */
    public String format(String province, String city, String district, String detail) {
        return province + city + district + detail;
    }
}
