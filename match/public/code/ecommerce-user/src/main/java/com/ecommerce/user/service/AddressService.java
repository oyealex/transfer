package com.ecommerce.user.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.entity.UserAddress;
import com.ecommerce.user.repository.UserAddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD service for user shipping addresses.
 */
@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final UserAddressRepository userAddressRepository;

    public AddressService(UserAddressRepository userAddressRepository) {
        this.userAddressRepository = userAddressRepository;
    }

    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        if (request.isDefault()) {
            clearDefaultForUser(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetail(request.getDetail());
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setDefault(request.isDefault());

        UserAddress saved = userAddressRepository.save(address);
        log.info("Address created: id={}, userId={}", saved.getId(), userId);
        return AddressResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return userAddressRepository.findByUserId(userId).stream()
                .map(AddressResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));

        if (!address.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Address", addressId);
        }

        if (request.isDefault() && !address.isDefault()) {
            clearDefaultForUser(userId);
        }

        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetail(request.getDetail());
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setDefault(request.isDefault());

        UserAddress saved = userAddressRepository.save(address);
        log.info("Address updated: id={}, userId={}", saved.getId(), userId);
        return AddressResponse.from(saved);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));

        if (!address.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Address", addressId);
        }

        userAddressRepository.delete(address);
        log.info("Address deleted: id={}, userId={}", addressId, userId);
    }

    private void clearDefaultForUser(Long userId) {
        List<UserAddress> addresses = userAddressRepository.findByUserId(userId);
        for (UserAddress addr : addresses) {
            if (addr.isDefault()) {
                addr.setDefault(false);
                userAddressRepository.save(addr);
            }
        }
    }
}
